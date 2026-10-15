package org.northland.thor;

import android.content.Context;
import android.os.*;
import java.io.*;
import java.nio.channels.*;

/** Shizuku launches this Binder as shell. Only our app can request named operations. */
public final class ActivationService extends Binder {
    static final String DESCRIPTOR="org.northland.thor.ActivationService";
    static final int STATUS=1,START=2,STOP=3,DESTROY=16777115;
    final int ownerUid;
    Thread worker;
    volatile String error;

    public ActivationService(Context context) throws Exception {
        if(android.os.Process.myUid()!=2000) throw new SecurityException("Shell activation required");
        ownerUid=context.getPackageManager().getApplicationInfo("org.northland.thor",0).uid;
        attachInterface(null,DESCRIPTOR);
    }
    static boolean locked() throws IOException {
        File dir=new File(InputBridge.DIR);
        if(!dir.isDirectory()) return false;
        try(RandomAccessFile file=new RandomAccessFile(new File(dir,"lock"),"rw")) {
            try(FileLock lock=file.getChannel().tryLock()) { return lock==null; }
            catch(OverlappingFileLockException e) { return true; }
        }
    }
    synchronized String status() {
        File state=new File(InputBridge.DIR,"state.json");
        try {
            if(locked() && state.isFile() && Math.abs(System.currentTimeMillis()-state.lastModified())<2500) return "RUNNING";
        } catch(IOException e) { return "ERROR: "+e.getMessage(); }
        if(worker!=null && worker.isAlive()) return "STARTING";
        return error==null?"STOPPED":"ERROR: "+error;
    }
    synchronized String start() {
        String current=status();
        if(current.equals("RUNNING") || current.equals("STARTING")) return current;
        error=null;
        worker=new Thread(() -> {
            try { InputBridge.main(new String[]{"controller"}); }
            catch(Exception e) { error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }
        },"northland-controller");
        worker.start();return "STARTING";
    }
    void stop() throws IOException {
        File dir=new File(InputBridge.DIR);
        if(!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create helper directory");
        new File(dir,"stop").createNewFile();
        error=null;
    }
    @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags) throws RemoteException {
        if(code==INTERFACE_TRANSACTION) { reply.writeString(DESCRIPTOR);return true; }
        int caller=Binder.getCallingUid();
        if(caller!=ownerUid && caller!=2000) throw new SecurityException("Only Northland Thor may activate its helper");
        if(code==DESTROY) {
            new Thread(() -> {
                try {
                    // Stop only our owned worker. A separately activated ADB helper
                    // must survive replacement of an idle Shizuku service.
                    if(worker!=null && worker.isAlive()) { stop();worker.join(8000); }
                    if(worker==null || !worker.isAlive()) System.exit(0);
                } catch(Exception e) { android.util.Log.e("NorthlandActivation","Could not stop cleanly",e); }
            },"northland-deactivate").start();
            return true;
        }
        if(code<STATUS || code>STOP) return super.onTransact(code,data,reply,flags);
        data.enforceInterface(DESCRIPTOR);
        if(data.dataAvail()!=0) throw new IllegalArgumentException("Activation takes no arguments");
        String result;
        try {
            if(code==START) result=start();
            else if(code==STOP) { stop();result="STOPPING"; }
            else result=status();
        } catch(IOException e) { result="ERROR: "+e.getMessage(); }
        reply.writeNoException();reply.writeString(result);return true;
    }
}
