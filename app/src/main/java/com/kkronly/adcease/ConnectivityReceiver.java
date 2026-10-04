package com.kkronly.adcease;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.VpnService;
import android.os.Build;

/**
 * Auto-reconnect: re-arms the tunnel when the network comes back.
 * Developer: Kaustav Kanti Ray @iamkkronly
 */
public class ConnectivityReceiver extends BroadcastReceiver {

    /** Developer: Kaustav Kanti Ray @iamkkronly */
    private static final String DEVELOPER = Prefs.DEVELOPER;

    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Prefs.isEnabled(c) || AdCeaseVpnService.isRunning()) return;
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) return;
        if (VpnService.prepare(c) != null) return;
        Intent i = new Intent(c, AdCeaseVpnService.class).setAction(AdCeaseVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i);
        else c.startService(i);
    }
}
