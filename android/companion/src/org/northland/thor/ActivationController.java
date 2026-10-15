package org.northland.thor;

import android.content.*;

/** Active status comes from the authenticated helper connection, never saved flags. */
final class ActivationController implements AutoCloseable {
    interface Listener { void state(String message,boolean active); }
    final Context context;
    final Listener listener;
    ActivationController(Context context,Listener listener) { this.context=context;this.listener=listener; }
    void refresh() {
        if(PanelService.bridgeOnline) { listener.state("Controls are active. Open Northland and release the buttons.",true);return; }
        android.content.SharedPreferences prefs=context.getSharedPreferences("wireless",0);
        String message=prefs.getString("status",prefs.getBoolean("paired",false)?
            "Tap Activate controls. Wi-Fi and Wireless debugging must be enabled.":
            "Pair this app with Android once, then tap Activate controls. No extra app or root needed.");
        if(!WirelessActivationService.running && (message.startsWith("Controls are active") || message.startsWith("Looking for")
            || message.startsWith("Checking Wireless")
            || message.startsWith("Connecting and") || message.startsWith("Pairing with") || message.startsWith("Enter Android")
            || message.startsWith("Still searching") || message.startsWith("Open Wireless")))
            message="Tap Activate controls. Wi-Fi and Wireless debugging must be enabled.";
        listener.state(message,false);
    }
    void activate() { context.startForegroundService(new Intent(context,WirelessActivationService.class).setAction(WirelessActivationService.ACTIVATE)); }
    void pair() { context.startForegroundService(new Intent(context,WirelessActivationService.class).setAction(WirelessActivationService.PAIR)); }
    public void close() { }
}
