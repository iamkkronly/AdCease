package com.kkronly.adcease;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * AdCease dashboard: Start / Pause control + live query statistics.
 * Developer: Kaustav Kanti Ray @iamkkronly
 */
public class MainActivity extends AppCompatActivity {

    /** Developer: Kaustav Kanti Ray @iamkkronly */
    private static final String DEVELOPER = Prefs.DEVELOPER;

    private static final int REQ_VPN = 7001;

    private TextView title, subtitle, total, blocked, threats;
    private ImageView power;
    private View glow;

    private final BroadcastReceiver statsRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { render(); }
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);

        title    = findViewById(R.id.title);
        subtitle = findViewById(R.id.subtitle);
        total    = findViewById(R.id.statTotal);
        blocked  = findViewById(R.id.statBlocked);
        threats  = findViewById(R.id.statThreats);
        power    = findViewById(R.id.powerButton);
        glow     = findViewById(R.id.glow);

        power.setOnClickListener(v -> toggle());
        findViewById(R.id.resetStats).setOnClickListener(v -> {
            Prefs.resetStats(this);
            render();
        });

        FilterUpdateWorker.schedule(this);   // every 6 hours
        Blocklist.ensureLoaded(this);
    }

    private void toggle() {
        if (AdCeaseVpnService.isRunning()) {
            // PAUSE
            startService(new Intent(this, AdCeaseVpnService.class)
                    .setAction(AdCeaseVpnService.ACTION_STOP));
            Prefs.setEnabled(this, false);
            power.postDelayed(this::render, 350);
        } else {
            // START
            Intent prep = VpnService.prepare(this);
            if (prep != null) startActivityForResult(prep, REQ_VPN);
            else startEngine();
        }
    }

    private void startEngine() {
        Intent i = new Intent(this, AdCeaseVpnService.class).setAction(AdCeaseVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
        power.postDelayed(this::render, 500);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_VPN) {
            if (res == RESULT_OK) startEngine();
            else Toast.makeText(this, "VPN permission is required for system-wide blocking", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(AdCeaseVpnService.ACTION_STATS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statsRx, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(statsRx, f);
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(statsRx); } catch (Exception ignored) {}
    }

    private void render() {
        boolean on = AdCeaseVpnService.isRunning();
        title.setText(on ? "Protected" : "Paused");
        title.setTextColor(on ? 0xFF1B8A3A : 0xFF9AA0A6);
        subtitle.setText(on ? "Your device is protected from ads" : "Tap the button to resume protection");
        power.setImageResource(R.drawable.ic_power);
        power.setColorFilter(on ? 0xFF1B8A3A : 0xFF9AA0A6);
        glow.setBackgroundResource(on ? R.drawable.glow_on : R.drawable.glow_off);

        total.setText(String.valueOf(Prefs.stat(this, Prefs.KEY_TOTAL)));
        blocked.setText(String.valueOf(Prefs.stat(this, Prefs.KEY_BLOCKED)));
        threats.setText(String.valueOf(Prefs.stat(this, Prefs.KEY_THREATS)));

    }
}
