package com.kkronly.adcease;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.util.Log;

/**
 * Restarts the ad blocker after a device reboot / app update.
 * Developer: Kaustav Kanti Ray @iamkkronly
 */
public class BootReceiver extends BroadcastReceiver {

    /** Developer: Kaustav Kanti Ray @iamkkronly */
    private static final String DEVELOPER = Prefs.DEVELOPER;

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent == null ? "" : String.valueOf(intent.getAction());
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a)
                && !"android.intent.action.LOCKED_BOOT_COMPLETED".equals(a)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;

        if (!Prefs.isEnabled(c)) return;
        // VpnService.prepare() returns null once the user has granted consent,
        // which means we may start the tunnel headlessly on boot.
        if (VpnService.prepare(c) != null) {
            Log.w("AdCease", "VPN consent missing after boot; waiting for user");
            return;
        }
        FilterUpdateWorker.schedule(c);
        Intent i = new Intent(c, AdCeaseVpnService.class).setAction(AdCeaseVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i);
        else c.startService(i);
        
    }
}
