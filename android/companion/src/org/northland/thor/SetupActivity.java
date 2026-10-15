package org.northland.thor;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;

public final class SetupActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent().getBooleanExtra("show-panel", false) && Settings.canDrawOverlays(this)) {
            startForegroundService(new Intent(this, PanelService.class)); finish(); return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(40, 40, 40, 40);
        TextView text = new TextView(this);
        text.setTextSize(20);
        text.setText("Northland Thor\n\nController prototype for the purchased Android game.\n\nStart the input bridge from your computer after each device restart. This app shows the cursor and bottom-screen commands. It does not include Northland.\n");
        layout.addView(text);
        Button start = new Button(this); start.setText("Show command panel");
        start.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                return;
            }
            startForegroundService(new Intent(this, PanelService.class));
            finish();
        });
        layout.addView(start);
        Button stop = new Button(this); stop.setText("Hide panel");
        stop.setOnClickListener(v -> { stopService(new Intent(this, PanelService.class)); finish(); });
        layout.addView(stop); setContentView(layout);
    }
}
