package org.northland.thor;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;

/** App-side activation. The shell service exposes no commands or user paths. */
final class ActivationController implements AutoCloseable {
    interface Listener { void state(String message,boolean active); }
    final Context context;
    final Listener listener;
    final Handler handler=new Handler(Looper.getMainLooper());
    final ExecutorService io=Executors.newSingleThreadExecutor();
    final Shizuku.UserServiceArgs args;
    IBinder service;
    boolean pending,binding,closed;
    final Shizuku.OnBinderReceivedListener received=this::refresh;
    final Shizuku.OnBinderDeadListener dead=() -> { service=null;binding=false;refresh(); };
    final Shizuku.OnRequestPermissionResultListener permission=(code,grant) -> {
        if(code==41 && grant==PackageManager.PERMISSION_GRANTED && pending) bind();
        else if(code==41) { setPending(false);refresh(); }
    };
    final ServiceConnection connection=new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name,IBinder binder) {
            if(closed) return;
            service=binder;binding=false;
            if(pending) { setPending(false);request(ActivationService.START); }
            else request(ActivationService.STATUS);
        }
        @Override public void onServiceDisconnected(ComponentName name) { service=null;binding=false;refresh(); }
    };
    ActivationController(Context context,Listener listener) {
        this.context=context;this.listener=listener;
        args=new Shizuku.UserServiceArgs(new ComponentName(context,ActivationService.class))
            .tag("northland-controller").processNameSuffix("activation").daemon(true).version(ActivationBuild.VERSION);
        pending=context.getSharedPreferences("activation",0).getBoolean("requested",false);
        Shizuku.addBinderReceivedListenerSticky(received);
        Shizuku.addBinderDeadListener(dead);
        Shizuku.addRequestPermissionResultListener(permission);
    }
    boolean installed() {
        try { context.getPackageManager().getPackageInfo("moe.shizuku.privileged.api",0);return true; }
        catch(PackageManager.NameNotFoundException e) { return false; }
    }
    void setPending(boolean requested) {
        pending=requested;
        context.getSharedPreferences("activation",0).edit().putBoolean("requested",requested).apply();
    }
    void refresh() {
        if(closed) return;
        if(PanelService.bridgeOnline) { if(pending) setPending(false);listener.state("Controls are active. Open Northland and release the buttons.",true);return; }
        if(!installed()) { listener.state("USB setup is available. For activation without a computer, install Shizuku.",false);return; }
        if(!Shizuku.pingBinder()) { listener.state("Start Shizuku on the Thor, then tap Activate controls. No computer is needed.",false);return; }
        if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED) {
            listener.state("Tap Activate controls and allow Northland Thor in Shizuku.",false);return;
        }
        if(pending) { bind();return; }
        if(service!=null) request(ActivationService.STATUS);
        else listener.state(binding?"Connecting to Shizuku…":"Shizuku is ready. Tap Activate controls.",false);
    }
    void activate() {
        if(!Shizuku.pingBinder()) { refresh();return; }
        setPending(true);
        if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED) { Shizuku.requestPermission(41);return; }
        bind();
    }
    void bind() {
        if(closed || binding) return;
        if(Shizuku.getUid()!=2000) { setPending(false);listener.state("Start Shizuku with Wireless debugging or USB, using its non-root mode.",false);return; }
        if(service!=null) { setPending(false);request(ActivationService.START);return; }
        binding=true;listener.state("Starting controls…",false);
        try { Shizuku.bindUserService(args,connection); }
        catch(RuntimeException e) { binding=false;setPending(false);listener.state("Could not activate: "+e.getMessage(),false); }
    }
    void request(int operation) {
        IBinder remote=service;
        if(closed || remote==null) return;
        io.execute(() -> {
            String result;
            Parcel data=Parcel.obtain(),reply=Parcel.obtain();
            try {
                data.writeInterfaceToken(ActivationService.DESCRIPTOR);
                if(!remote.transact(operation,data,reply,0)) throw new RemoteException("Activation service unavailable");
                reply.readException();result=reply.readString();
            } catch(Exception e) { result="ERROR: "+e.getMessage(); }
            finally { data.recycle();reply.recycle(); }
            final String status=result;
            handler.post(() -> {
                if(closed) return;
                listener.state(status.equals("RUNNING")?"Controls are active. Open Northland and release the buttons.":
                    status.equals("STARTING")?"Starting controls…":status.equals("STOPPED")?"Controls stopped. Tap Activate controls.":status,
                    status.equals("RUNNING"));
            });
        });
    }
    @Override public void close() {
        closed=true;io.shutdown();
        Shizuku.removeBinderReceivedListener(received);Shizuku.removeBinderDeadListener(dead);
        Shizuku.removeRequestPermissionResultListener(permission);
        if(binding || service!=null) try { Shizuku.unbindUserService(args,connection,false); } catch(RuntimeException ignored) { }
    }
}
