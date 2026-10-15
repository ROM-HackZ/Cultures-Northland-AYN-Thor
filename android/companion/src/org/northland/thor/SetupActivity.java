package org.northland.thor;

import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.app.ActivityOptions;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.*;

public final class SetupActivity extends Activity {
    ActivationController activation;
    final Handler handler=new Handler(Looper.getMainLooper());
    final Runnable refresh=new Runnable() { public void run() {
        if(activation!=null) activation.refresh();
        handler.postDelayed(this,1000);
    }};
    void panel() { startForegroundService(new Intent(this,PanelService.class)); }
    boolean overlayReady() {
        if(Settings.canDrawOverlays(this)) return true;
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
        return false;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent().getBooleanExtra("show-panel", false) && Settings.canDrawOverlays(this)) {
            panel(); finish(); return;
        }
        ScrollView scroll=new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(40, 24, 40, 24);
        TextView text = new TextView(this);
        text.setTextSize(24);
        text.setText("Northland Thor");
        layout.addView(text);
        TextView status=new TextView(this);status.setTextSize(18);status.setPadding(0,16,0,16);
        status.setText("Checking controls…");layout.addView(status);
        Button activate=new Button(this);activate.setText("Activate controls");layout.addView(activate);
        activation=new ActivationController(this,(message,active) -> {
            status.setText(message);activate.setEnabled(!active);
        });
        activate.setOnClickListener(v -> { if(overlayReady()) { panel();activation.activate(); } });
        Button game = new Button(this); game.setText("Open / return to Northland");
        game.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName("com.runesoft.cultures", "com.rune_soft.culturesnorthland.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            ActivityOptions options = ActivityOptions.makeBasic(); options.setLaunchDisplayId(0);
            try { startActivity(intent, options.toBundle()); finish(); }
            catch (ActivityNotFoundException e) {
                Toast.makeText(this, "Install your purchased Android copy of Northland first.", Toast.LENGTH_LONG).show();
            }
        });
        layout.addView(game);
        Button shizuku=new Button(this);shizuku.setText("Shizuku setup");
        shizuku.setOnClickListener(v -> {
            Intent intent=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if(intent==null) intent=new Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/download/"));
            startActivity(intent);
        });layout.addView(shizuku);
        TextView guide=new TextView(this);guide.setTextSize(16);
        guide.setText("Without a computer: install Shizuku and follow its Wireless debugging pairing steps. After a reboot, start Shizuku on the Thor, then tap Activate controls here. Wi-Fi is needed for wireless activation.\n\nUSB setup also works from the computer installer. Your purchased Northland app and saves stay in place.");
        layout.addView(guide);
        Button start = new Button(this); start.setText("Show command panel");
        start.setOnClickListener(v -> {
            if(!overlayReady()) return;
            panel();
            finish();
        });
        layout.addView(start);
        Button stop = new Button(this); stop.setText("Hide panel");
        stop.setOnClickListener(v -> { stopService(new Intent(this, PanelService.class)); finish(); });
        layout.addView(stop);scroll.addView(layout);setContentView(scroll);
        if(Settings.canDrawOverlays(this)) panel();
    }
    @Override protected void onResume() {
        super.onResume();if(activation!=null) handler.post(refresh);
    }
    @Override protected void onPause() { handler.removeCallbacks(refresh);super.onPause(); }
    @Override protected void onDestroy() {
        handler.removeCallbacks(refresh);if(activation!=null) activation.close();super.onDestroy();
    }
}
