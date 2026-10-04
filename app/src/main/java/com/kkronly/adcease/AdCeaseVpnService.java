package com.kkronly.adcease;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AdCease system-wide DNS ad blocker.
 *
 * A local VpnService captures DNS traffic only (a /32 route to a synthetic
 * resolver address), classifies every query against the cached HaGeZi Pro++
 * feed, sinkholes matches and forwards everything else to Control D's
 * x-hagezi-proplus endpoint over DNS-over-HTTPS. No root required.
 *
 * Developer: Kaustav Kanti Ray @iamkkronly
 */
public class AdCeaseVpnService extends VpnService {

    private static final String TAG = "AdCease";
    /** Developer: Kaustav Kanti Ray @iamkkronly */
    public static final String DEVELOPER = Prefs.DEVELOPER;

    public static final String ACTION_START = "com.kkronly.adcease.START";
    public static final String ACTION_STOP  = "com.kkronly.adcease.STOP";
    public static final String ACTION_STATS = "com.kkronly.adcease.STATS";

    /** Default upstream — x-hagezi-proplus.freedns.controld.com (DoH form). */
    public static final String UPSTREAM_DOH  = "https://freedns.controld.com/x-hagezi-proplus";
    public static final String UPSTREAM_HOST = "x-hagezi-proplus.freedns.controld.com";

    private static final String TUN_ADDR = "10.111.222.1";
    private static final String TUN_DNS  = "10.111.222.2";
    private static final String CHANNEL  = "adcease_status";
    private static final int    NOTIF_ID = 1001;

    private static volatile boolean running = false;
    public static boolean isRunning() { return running; }

    private ParcelFileDescriptor tun;
    private Thread worker;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private ExecutorService pool;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int reconnectAttempts = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            Prefs.setEnabled(this, false);
            teardown();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        Prefs.setEnabled(this, true);
        startForeground(NOTIF_ID, buildNotification("Protected", "Ad blocking is on"));
        connect();
        // START_STICKY gives us auto-restart if the system kills the process.
        return START_STICKY;
    }

    // ------------------------------------------------------------------
    // Tunnel lifecycle + auto-reconnect
    // ------------------------------------------------------------------

    private synchronized void connect() {
        if (running) return;
        stopping.set(false);
        try {
            Builder b = new Builder()
                    .setSession("AdCease")
                    .addAddress(TUN_ADDR, 32)
                    .addDnsServer(TUN_DNS)
                    .addRoute(TUN_DNS, 32)
                    .setMtu(1500);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try { b.addDisallowedApplication(getPackageName()); } catch (Exception ignored) {}
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) b.setMetered(false);
            Intent cfg = new Intent(this, MainActivity.class);
            b.setConfigureIntent(PendingIntent.getActivity(this, 0, cfg,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                            : PendingIntent.FLAG_UPDATE_CURRENT));

            tun = b.establish();
            if (tun == null) throw new IllegalStateException("establish() returned null");

            Blocklist.ensureLoaded(this);
            pool = Executors.newFixedThreadPool(8);
            running = true;
            reconnectAttempts = 0;
            worker = new Thread(this::pump, "adcease-pump");
            worker.start();
            broadcastStats();
            // internal state only; nothing logged to the device
        } catch (Exception e) {
            Log.e(TAG, "connect failed", e);
            running = false;
            scheduleReconnect();
        }
    }

    /** Exponential-ish backoff reconnect, capped at 60s, while the user keeps it enabled. */
    private void scheduleReconnect() {
        if (!Prefs.isEnabled(this) || stopping.get()) return;
        reconnectAttempts++;
        long delay = Math.min(60000L, 2000L * reconnectAttempts);
        Log.w(TAG, "Reconnecting in " + delay + "ms (attempt " + reconnectAttempts + ")");
        updateNotification("Reconnecting", "Turning protection back on…");
        ui.postDelayed(() -> {
            if (Prefs.isEnabled(this) && !running) {
                teardown();
                connect();
                if (running) updateNotification("Protected", "Ad blocking is on");
            }
        }, delay);
    }

    @Override
    public void onRevoke() {
        Log.w(TAG, "VPN revoked by system/another app");
        running = false;
        teardown();
        scheduleReconnect();   // auto-reconnect
    }

    private synchronized void teardown() {
        stopping.set(true);
        running = false;
        try { if (tun != null) tun.close(); } catch (Exception ignored) {}
        tun = null;
        if (pool != null) { pool.shutdownNow(); pool = null; }
        if (worker != null) { worker.interrupt(); worker = null; }
        broadcastStats();
    }

    @Override
    public void onDestroy() {
        boolean shouldRestart = Prefs.isEnabled(this);
        teardown();
        if (shouldRestart) {
            // ask the system to bring us back
            try {
                Intent i = new Intent(this, AdCeaseVpnService.class).setAction(ACTION_START);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
                else startService(i);
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // Packet pump
    // ------------------------------------------------------------------

    private void pump() {
        byte[] buf = new byte[32767];
        try (FileInputStream in = new FileInputStream(tun.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(tun.getFileDescriptor())) {

            while (running && !Thread.currentThread().isInterrupted()) {
                int n = in.read(buf);
                if (n <= 0) { Thread.sleep(5); continue; }
                if (!DnsUtil.isIpv4Udp(buf, n) || DnsUtil.dstPort(buf) != 53) continue;

                byte[] pkt = new byte[n];
                System.arraycopy(buf, 0, pkt, 0, n);
                byte[] dns = DnsUtil.udpPayload(pkt, n);
                if (dns.length < 12) continue;

                if (pool != null && !pool.isShutdown()) pool.execute(() -> handle(pkt, dns, out));
            }
        } catch (Exception e) {
            if (running) {
                Log.e(TAG, "pump died", e);
                running = false;
                ui.post(this::scheduleReconnect);
            }
        }
    }

    private void handle(byte[] pkt, byte[] dns, OutputStream out) {
        String host = DnsUtil.question(dns);
        Prefs.bump(this, Prefs.KEY_TOTAL, 1);

        int verdict = Blocklist.classify(host);
        try {
            if (verdict != 0) {
                Prefs.bump(this, Prefs.KEY_BLOCKED, 1);
                if (verdict == 2) Prefs.bump(this, Prefs.KEY_THREATS, 1);
                byte[] reply = DnsUtil.buildReply(pkt, DnsUtil.nxdomain(dns));
                synchronized (out) { out.write(reply); out.flush(); }
            } else {
                byte[] up = resolveDoh(dns);
                if (up != null) {
                    // Control D's Pro++ profile sinkholes too; NXDOMAIN/0.0.0.0 counts as blocked.
                    if (isSinkholed(up)) Prefs.bump(this, Prefs.KEY_BLOCKED, 1);
                    byte[] reply = DnsUtil.buildReply(pkt, up);
                    synchronized (out) { out.write(reply); out.flush(); }
                }
            }
        } catch (Exception e) {
            // swallow silently
        }
        broadcastStats();
    }

    private static boolean isSinkholed(byte[] dns) {
        if (dns.length < 12) return false;
        int rcode = dns[3] & 0x0F;
        if (rcode == 3) return true;                         // NXDOMAIN
        int an = ((dns[6] & 0xFF) << 8) | (dns[7] & 0xFF);
        return an == 0 && rcode == 0;                        // NODATA
    }

    /** POST the raw DNS query to the Control D x-hagezi-proplus DoH endpoint. */
    private byte[] resolveDoh(byte[] query) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(UPSTREAM_DOH).openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setRequestProperty("Content-Type", "application/dns-message");
            con.setRequestProperty("Accept", "application/dns-message");
            con.setRequestProperty("User-Agent", "AdCease/1.0 (by Kaustav Kanti Ray @iamkkronly)");
            con.getOutputStream().write(query);
            con.getOutputStream().flush();
            if (con.getResponseCode() != 200) return null;
            java.io.InputStream is = con.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(1024);
            byte[] b = new byte[1024];
            int r;
            while ((r = is.read(b)) > 0) bos.write(b, 0, r);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        } finally { if (con != null) con.disconnect(); }
    }

    // ------------------------------------------------------------------
    // UI plumbing
    // ------------------------------------------------------------------

    private long lastBroadcast = 0;
    private void broadcastStats() {
        long now = System.currentTimeMillis();
        if (now - lastBroadcast < 400) return;
        lastBroadcast = now;
        sendBroadcast(new Intent(ACTION_STATS).setPackage(getPackageName()));
    }

    private Notification buildNotification(String title, String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "AdCease status",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder nb = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return nb.setContentTitle("AdCease — " + title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(title, text));
    }
}
