package org.northland.thor;

import android.content.Context;
import android.net.nsd.*;
import android.net.wifi.WifiManager;
import android.os.*;
import java.net.*;
import java.util.*;

/** Discovers only this device's debugging ports. Connections always use loopback. */
final class LocalAdbDiscovery implements AutoCloseable {
    interface Listener { void found(int port);void failed(String message); }
    final NsdManager nsd;
    final Handler main=new Handler(Looper.getMainLooper());
    final Listener listener;
    final ArrayDeque<NsdServiceInfo> pending=new ArrayDeque<>();
    final String type;
    final WifiManager.MulticastLock multicast;
    boolean closed,resolving,started;
    final NsdManager.DiscoveryListener discovery=new NsdManager.DiscoveryListener() {
        public void onDiscoveryStarted(String type) { }
        public void onDiscoveryStopped(String type) { }
        public void onStartDiscoveryFailed(String type,int code) { main.post(() -> fail("Discovery could not start. Check Wi-Fi and Wireless debugging.")); }
        public void onStopDiscoveryFailed(String type,int code) { }
        public void onServiceFound(NsdServiceInfo service) { main.post(() -> { if(!closed) { pending.add(service);resolveNext(); } }); }
        public void onServiceLost(NsdServiceInfo service) { main.post(() -> pending.removeIf(s -> s.getServiceName().equals(service.getServiceName()))); }
    };
    LocalAdbDiscovery(Context context,boolean pairing,Listener listener) {
        this.listener=listener;type=pairing?"_adb-tls-pairing._tcp.":"_adb-tls-connect._tcp.";
        nsd=context.getSystemService(NsdManager.class);
        WifiManager wifi=context.getApplicationContext().getSystemService(WifiManager.class);
        multicast=wifi.createMulticastLock("northland-wireless-discovery");multicast.setReferenceCounted(false);
    }
    void start() {
        try { multicast.acquire();started=true;nsd.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,discovery); }
        catch(RuntimeException e) { fail("Enable Wi-Fi and Wireless debugging, then try again."); }
        main.postDelayed(() -> fail("No debugging port found. Toggle Wireless debugging off and on, then try again."),120000);
    }
    void resolveNext() {
        if(closed || resolving || pending.isEmpty()) return;
        resolving=true;
        try { nsd.resolveService(pending.remove(),new NsdManager.ResolveListener() {
            public void onResolveFailed(NsdServiceInfo service,int code) { main.post(() -> { resolving=false;resolveNext(); }); }
            public void onServiceResolved(NsdServiceInfo service) { main.post(() -> {
                resolving=false;if(closed) return;
                if(isLocal(service.getHost()) && service.getPort()>0 && service.getPort()<65536) {
                    int port=service.getPort();close();listener.found(port);
                } else resolveNext();
            }); }
        }); } catch(RuntimeException e) { resolving=false;resolveNext(); }
    }
    static boolean isLocal(InetAddress host) {
        if(host==null) return false;
        if(host.isLoopbackAddress()) return true;
        try {
            for(NetworkInterface network:Collections.list(NetworkInterface.getNetworkInterfaces()))
                if(Collections.list(network.getInetAddresses()).contains(host)) return true;
        } catch(SocketException ignored) { }
        return false;
    }
    void fail(String message) { if(!closed) { close();listener.failed(message); } }
    public void close() {
        if(closed) return;closed=true;main.removeCallbacksAndMessages(null);pending.clear();
        if(started) try { nsd.stopServiceDiscovery(discovery); } catch(RuntimeException ignored) { }
        if(multicast.isHeld()) multicast.release();
    }
}
