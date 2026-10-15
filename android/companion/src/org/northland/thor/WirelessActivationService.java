package org.northland.thor;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;

/** Foreground setup survives switching to Android's pairing-code screen. */
public final class WirelessActivationService extends Service {
    static final String PAIR="org.northland.thor.PAIR",CODE="org.northland.thor.PAIR_CODE",
        ACTIVATE="org.northland.thor.ACTIVATE",CANCEL="org.northland.thor.CANCEL_SETUP";
    static final String CHANNEL="wireless-setup",RESULT_KEY="pairing-code";
    static volatile boolean running;
    final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService io=Executors.newSingleThreadExecutor();
    LocalAdbDiscovery discovery;
    WirelessBootstrap client;
    int pairingPort;
    volatile int generation;
    boolean pairingBusy,inProgress;
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onCreate() {
        super.onCreate();running=true;
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(
            CHANNEL,"Northland setup",NotificationManager.IMPORTANCE_DEFAULT));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null) { stopSelf();return START_NOT_STICKY; }
        String action=intent.getAction();
        if(CANCEL.equals(action)) { finish("Setup cancelled. You can try again.");return START_NOT_STICKY; }
        if(CODE.equals(action)) {
            if(pairingPort==0) { finish("Pairing expired. Open a fresh pairing code and try again.");return START_NOT_STICKY; }
            Bundle results=RemoteInput.getResultsFromIntent(intent);
            CharSequence code=results==null?null:results.getCharSequence(RESULT_KEY);
            if(code!=null && pairingPort>0 && !pairingBusy) pair(code.toString().trim());
            return START_NOT_STICKY;
        }
        if(!PAIR.equals(action) && !ACTIVATE.equals(action)) { stopSelf();return START_NOT_STICKY; }
        if(inProgress) {
            if(!ACTIVATE.equals(action)) return START_NOT_STICKY;
            generation++;
            if(discovery!=null) { discovery.close();discovery=null; }
            if(client!=null) { client.close();client=null; }
            pairingPort=0;pairingBusy=false;
        }
        inProgress=true;
        startForeground(2,notification("Looking for Wireless debugging on this Thor...",false,true));
        boolean pair=PAIR.equals(action);
        if(pair) discover(true);
        else {
            setState("Checking Wireless debugging on this Thor...");
            int attempt=++generation;
            io.execute(() -> {
                int current=LocalAdbPort.current();
                android.util.Log.i("NorthlandActivation",current>0?"Current local TLS port is readable":"Local TLS port unavailable; using discovery");
                main.post(() -> { if(attempt==generation) {
                    if(current>0) activate(current,true);else discover(false);
                }});
            });
        }
        return START_NOT_STICKY;
    }
    void discover(boolean pair) {
        setState(pair?"Open Wireless debugging, then Pair device with pairing code. Enter its code in our notification.":
            "Looking for Wireless debugging. Keep Wi-Fi and Wireless debugging enabled.");
        int attempt=++generation;
        discovery=new LocalAdbDiscovery(this,pair,new LocalAdbDiscovery.Listener() {
            public void found(int port) {
                if(attempt!=generation) return;discovery=null;
                if(pair) {
                    pairingPort=port;
                    setState("Enter Android's six-digit code in the Northland Thor notification.");
                    notifyState("Pairing code ready. Enter Android's code here.",true,true);
                    main.postDelayed(() -> { if(attempt==generation && !pairingBusy)
                        finish("Pairing timed out. Open a fresh pairing code and try again."); },180000);
                } else activate(port,false);
            }
            public void failed(String message) { if(attempt==generation) { discovery=null;finish(message); } }
        });
        discovery.start();
        if(!pair) main.postDelayed(() -> {
            if(attempt==generation && discovery!=null) {
                String hint="Still searching. Open Wireless debugging settings, turn it off and on, then allow your Wi-Fi network.";
                setState(hint);notifyState(hint,false,true);
            }
        },10000);
    }
    void pair(String code) {
        if(!code.matches("[0-9]{6}")) { notifyState("Use the six-digit code shown by Android.",true,true);return; }
        pairingBusy=true;client=new WirelessBootstrap();WirelessBootstrap operation=client;int attempt=generation;
        setState("Pairing with this Thor...");notifyState("Pairing with this Thor...",false,true);
        main.postDelayed(() -> { if(attempt==generation) finish("Pairing timed out. Use a fresh code and try again."); },25000);
        io.execute(() -> {
            try {
                operation.pair(this,pairingPort,code);
                main.post(() -> { if(attempt==generation) {
                    getSharedPreferences("wireless",0).edit().putBoolean("paired",true).apply();
                    finish("Paired. Open Northland Thor and tap Activate controls.");
                }});
            } catch(Exception e) { main.post(() -> { if(attempt==generation)
                finish("Pairing failed. Keep Android's code window open and try a fresh code."); }); }
            finally { operation.close(); }
        });
    }
    void activate(int port,boolean rediscoverOnFailure) {
        client=new WirelessBootstrap();WirelessBootstrap operation=client;int attempt=generation;
        setState("Connecting and starting controls...");notifyState("Starting controls...",false,true);
        main.postDelayed(() -> { if(attempt==generation)
            finish(operation.authenticated?
                "Android connected, but controls did not start. Tap Activate controls to retry.":
                "Connection timed out. Enable Wireless debugging, then tap Activate controls again."); },30000);
        io.execute(() -> {
            try {
                operation.activate(this,port);
                long deadline=SystemClock.uptimeMillis()+8000;
                while(!PanelService.bridgeOnline && SystemClock.uptimeMillis()<deadline && attempt==generation) SystemClock.sleep(100);
                if(!PanelService.bridgeOnline) throw new java.io.IOException("Helper did not connect");
                main.post(() -> { if(attempt==generation) finish("Controls are active. Open Northland and release the buttons."); });
            } catch(Exception e) {
                android.util.Log.w("NorthlandActivation","Wireless activation failed while "+operation.stage+": "+e.getClass().getSimpleName());
                main.post(() -> { if(attempt==generation) {
                    if(rediscoverOnFailure && !operation.authenticated) { client=null;discover(false); }
                    else finish(operation.authenticated?
                        "Android connected, but controls did not start. Tap Activate controls to retry.":
                        "Could not connect to Android. Check Wireless debugging and pair this app if needed.");
                }});
            } finally { operation.close(); }
        });
    }
    void setState(String message) { getSharedPreferences("wireless",0).edit().putString("status",message).apply(); }
    Notification notification(String message,boolean code,boolean ongoing) {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,SetupActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Northland Thor setup").setContentText(message)
            .setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(open).setOngoing(ongoing)
            .setOnlyAlertOnce(true);
        if(code) {
            PendingIntent submit=PendingIntent.getService(this,41,new Intent(this,WirelessActivationService.class).setAction(CODE),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
            builder.addAction(new Notification.Action.Builder(null,"Enter pairing code",submit)
                .addRemoteInput(new RemoteInput.Builder(RESULT_KEY).setLabel("Six-digit pairing code").build()).build());
        }
        if(ongoing) {
            PendingIntent cancel=PendingIntent.getService(this,42,new Intent(this,WirelessActivationService.class).setAction(CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(null,"Cancel",cancel).build());
        }
        return builder.build();
    }
    void notifyState(String message,boolean code,boolean ongoing) {
        getSystemService(NotificationManager.class).notify(2,notification(message,code,ongoing));
    }
    void finish(String message) {
        generation++;setState(message);notifyState(message,false,false);
        stopForeground(STOP_FOREGROUND_DETACH);stopSelf();
    }
    @Override public void onDestroy() {
        running=false;generation++;main.removeCallbacksAndMessages(null);
        if(discovery!=null) discovery.close();
        if(client!=null) client.close();io.shutdownNow();super.onDestroy();
    }
}
