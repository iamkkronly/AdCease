package com.kkronly.adcease;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

/**
 * Four-step onboarding: welcome → VPN consent → notifications →
 * battery optimization.
 *
 * Developer: Kaustav Kanti Ray @iamkkronly
 */
public class OnboardingActivity extends AppCompatActivity {

    /** Developer: Kaustav Kanti Ray @iamkkronly */
    private static final String DEVELOPER = Prefs.DEVELOPER;

    private static final int REQ_VPN = 9001;
    private static final int REQ_NOTIF = 9002;

    private ViewFlipper flipper;
    private Button next;
    private TextView stepLabel;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        if (Prefs.isOnboarded(this)) { goMain(); return; }

        setContentView(R.layout.activity_onboarding);
        flipper   = findViewById(R.id.flipper);
        next      = findViewById(R.id.btnNext);
        stepLabel = findViewById(R.id.stepLabel);

        findViewById(R.id.btnSkip).setOnClickListener(v -> advance());
        next.setOnClickListener(v -> onNext());
        updateStep();
    }

    private void onNext() {
        switch (flipper.getDisplayedChild()) {
            case 0: advance(); break;
            case 1: {                       // VPN consent
                Intent prep = VpnService.prepare(this);
                if (prep != null) startActivityForResult(prep, REQ_VPN);
                else advance();
                break;
            }
            case 2: {                       // Notifications
                if (Build.VERSION.SDK_INT >= 33) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
                } else advance();
                break;
            }
            case 3: requestBatteryExemption(); break;
            default: advance();
        }
    }

    @SuppressLint("BatteryLife")
    private void requestBatteryExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
                    catch (Exception ignored) {}
                }
            }
        }
        advance();
    }

    private void finishOnboarding() {
        Prefs.setOnboarded(this);
        FilterUpdateWorker.schedule(this);
        // first filter sync right away
        new Thread(() -> Blocklist.refresh(getApplicationContext())).start();
        goMain();
    }

    private void advance() {
        if (flipper.getDisplayedChild() < flipper.getChildCount() - 1) {
            flipper.showNext();
            updateStep();
        } else finishOnboarding();
    }

    private void updateStep() {
        int i = flipper.getDisplayedChild();
        int n = flipper.getChildCount();
        stepLabel.setText("Step " + (i + 1) + " of " + n);
        next.setText(i == n - 1 ? "Finish" : "Continue");
        findViewById(R.id.btnSkip).setVisibility(i == 0 || i == n - 1 ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (req == REQ_VPN) advance();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        if (req == REQ_NOTIF) advance();
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
