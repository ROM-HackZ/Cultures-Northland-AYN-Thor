package org.northland.thor;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import io.github.muntashirakon.adb.*;

/** Loopback ADB client. Only pairing and our fixed helper startup are exposed. */
final class WirelessBootstrap implements AutoCloseable {
    volatile AdbConnection connection;
    volatile PairingConnectionCtx pairing;
    volatile boolean closed;
    volatile boolean authenticated;
    volatile String stage="connecting";
    void pair(Context context,int port,String code) throws Exception {
        checkPort(port);
        if(!code.matches("[0-9]{6}")) throw new IOException("Enter the six-digit pairing code.");
        WirelessIdentity identity=WirelessIdentity.load(context);
        pairing=new PairingConnectionCtx("127.0.0.1",port,code.getBytes(StandardCharsets.UTF_8),
            identity.key,identity.certificate,"Northland Thor");
        if(closed) throw new IOException("Pairing cancelled");
        try { pairing.start(); } finally { pairing.close();pairing=null; }
    }
    void activate(Context context,int port) throws Exception {
        checkPort(port);WirelessIdentity identity=WirelessIdentity.load(context);
        if(closed) throw new IOException("Activation cancelled");
        connection=AdbConnection.create("127.0.0.1",port,identity.key,identity.certificate,Build.VERSION.SDK_INT);
        connection.setDeviceName("Northland Thor");
        if(closed) throw new IOException("Activation cancelled");
        if(!connection.connect(12,TimeUnit.SECONDS,true)) throw new IOException("Wireless connection timed out. Pair this app first.");
        authenticated=true;stage="checking shell UID";
        if(!shell("id -u").trim().equals("2000")) throw new IOException("Wireless debugging must run as Android shell.");
        stage="starting helper";
        if(!shell(startCommand(context.getPackageCodePath())).contains("NORTHLAND_STARTED"))
            throw new IOException("Could not start the controller helper.");
        stage="waiting for helper socket";
    }
    static void checkPort(int port) throws IOException {
        if(port<1 || port>65535) throw new IOException("Invalid debugging port.");
    }
    static String startCommand(String apk) {
        if(!apk.startsWith("/data/app/") || !apk.endsWith(".apk") || apk.indexOf('\n')>=0 || apk.indexOf('\0')>=0)
            throw new IllegalArgumentException("Invalid installed app path");
        String quoted="'"+apk.replace("'","'\\''")+"'";
        return "mkdir -p /data/local/tmp/northland-thor && "
            +"{ CLASSPATH="+quoted+" /system/bin/nohup /system/bin/setsid /system/bin/app_process64 /system/bin org.northland.thor.InputBridge controller "
            +"> /data/local/tmp/northland-thor/bridge.log 2>&1 < /dev/null & echo NORTHLAND_STARTED; }";
    }
    private String shell(String command) throws Exception {
        if(closed) throw new IOException("Activation cancelled");
        try(AdbStream stream=connection.open("shell,v2,raw:"+command)) {
            ShellReply reply=ShellReply.read(stream.openInputStream());
            if(reply.exitCode!=0) throw new IOException("Shell command exited with status "+reply.exitCode);
            android.util.Log.i("NorthlandActivation",stage+": shell exited successfully");
            return reply.stdout;
        }
    }
    public void close() {
        closed=true;PairingConnectionCtx pair=pairing;if(pair!=null) pair.close();
        AdbConnection adb=connection;if(adb!=null) try { adb.close(); } catch(IOException ignored) { }
    }
}
