package org.northland.thor;

import java.net.InetAddress;

/** Host checks for the privileged bootstrap's input boundaries. */
public final class WirelessActivationCheck {
    interface Checked { void run() throws Exception; }
    static void reject(Checked operation) throws Exception {
        try { operation.run(); } catch(java.io.IOException|IllegalArgumentException expected) { return; }
        throw new AssertionError("Unsafe bootstrap input accepted");
    }
    public static void main(String[] args) throws Exception {
        for(String value:new String[]{"", "0", "-1", "65536", "999999999999999", "35067;id", "35067\n12345", "::1:35067"})
            if(LocalAdbPort.parse(value)!=0) throw new AssertionError("Invalid local TLS port accepted: "+value);
        if(LocalAdbPort.parse("35067\n")!=35067 || LocalAdbPort.parse("1")!=1 || LocalAdbPort.parse("65535")!=65535)
            throw new AssertionError("Valid current TLS port rejected");
        WirelessBootstrap.checkPort(1);WirelessBootstrap.checkPort(65535);
        reject(() -> WirelessBootstrap.checkPort(0));
        reject(() -> WirelessBootstrap.checkPort(-1));
        reject(() -> WirelessBootstrap.checkPort(65536));
        for(String path:new String[]{"/sdcard/payload.apk","/data/app/base.jar","/data/app/a\nb.apk","/data/app/a\0b.apk"})
            reject(() -> WirelessBootstrap.startCommand(path));
        String path="/data/app/test space/'$(touch marker)`whoami`/base.apk";
        String command=WirelessBootstrap.startCommand(path);
        if(!command.contains("CLASSPATH='/data/app/test space/'\\''$(touch marker)`whoami`/base.apk' /system/bin/nohup "))
            throw new AssertionError("Installed APK path is not shell quoted");
        if(!command.contains("echo NORTHLAND_STARTED;") || !command.contains("/system/bin/setsid /system/bin/app_process64 /system/bin org.northland.thor.InputBridge controller"))
            throw new AssertionError("Unexpected startup operation");
        if(LocalAdbDiscovery.isLocal(null) || LocalAdbDiscovery.isLocal(InetAddress.getByName("203.0.113.10")))
            throw new AssertionError("Remote discovery accepted");
        if(!LocalAdbDiscovery.isLocal(InetAddress.getByName("127.0.0.1"))
            || !LocalAdbDiscovery.isLocal(InetAddress.getByName("::1")))
            throw new AssertionError("Loopback discovery rejected");
        System.out.println("Wireless activation boundaries passed");
    }
}
