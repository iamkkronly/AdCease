package com.kkronly.adcease;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Local domain blocklist (HaGeZi Pro++ mirror) used as a first-pass filter
 * before falling back to the upstream Control D resolver.
 *
 * Developer: Kaustav Kanti Ray @iamkkronly
 */
public final class Blocklist {

    private static final String TAG = "AdCease";
    /** Developer: Kaustav Kanti Ray @iamkkronly */
    private static final String CREDIT = Prefs.DEVELOPER;

    /** Domain-only HaGeZi Pro++ list. */
    public static final String LIST_URL =
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/pro.plus.txt";
    /** Threat-intel feed -> counted separately as "Threats Blocked". */
    public static final String THREAT_URL =
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/tif.medium.txt";

    private static final String CACHE_ADS    = "adcease_ads.txt";
    private static final String CACHE_THREAT = "adcease_threat.txt";

    private static volatile Set<String> ads    = Collections.emptySet();
    private static volatile Set<String> threat = Collections.emptySet();
    private static volatile boolean loaded = false;

    private Blocklist() {}

    public static synchronized void ensureLoaded(Context c) {
        if (loaded) return;
        ads    = readCache(new File(c.getFilesDir(), CACHE_ADS));
        threat = readCache(new File(c.getFilesDir(), CACHE_THREAT));
        loaded = true;
        
    }

    public static int size() { return ads.size() + threat.size(); }

    /** 0 = allow, 1 = ad/tracker, 2 = threat. */
    public static int classify(String host) {
        if (host == null || host.isEmpty()) return 0;
        String h = host.toLowerCase();
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        while (true) {
            if (threat.contains(h)) return 2;
            if (ads.contains(h))    return 1;
            int dot = h.indexOf('.');
            if (dot < 0) return 0;
            h = h.substring(dot + 1);
            if (h.indexOf('.') < 0) return 0; // don't match bare TLDs
        }
    }

    /** Downloads both feeds; returns total domain count, or -1 on failure. */
    public static synchronized int refresh(Context c) {
        int n = 0;
        try {
            Set<String> a = download(LIST_URL);
            if (!a.isEmpty()) { write(new File(c.getFilesDir(), CACHE_ADS), a); ads = a; }
            n += ads.size();
        } catch (Exception e) { Log.w(TAG, "ads refresh failed: " + e); }
        try {
            Set<String> t = download(THREAT_URL);
            if (!t.isEmpty()) { write(new File(c.getFilesDir(), CACHE_THREAT), t); threat = t; }
            n += threat.size();
        } catch (Exception e) { Log.w(TAG, "threat refresh failed: " + e); }
        loaded = true;
        if (n > 0) {
            Prefs.get(c).edit()
                    .putLong(Prefs.KEY_LIST_UPDATED, System.currentTimeMillis())
                    .putInt(Prefs.KEY_LIST_SIZE, n)
                    .apply();
        }
        return n > 0 ? n : -1;
    }

    private static Set<String> download(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(60000);
        con.setRequestProperty("Accept-Encoding", "gzip");
        con.setRequestProperty("User-Agent", "AdCease/1.0 (by Kaustav Kanti Ray @iamkkronly)");
        Set<String> out = new HashSet<>(200000);
        try {
            if (con.getResponseCode() != 200) return out;
            InputStream in = con.getInputStream();
            if ("gzip".equalsIgnoreCase(con.getContentEncoding())) in = new GZIPInputStream(in);
            BufferedReader r = new BufferedReader(new InputStreamReader(in), 1 << 16);
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '!') continue;
                // tolerate hosts-file format "0.0.0.0 domain"
                int sp = line.lastIndexOf(' ');
                if (sp >= 0) line = line.substring(sp + 1).trim();
                if (line.isEmpty() || line.indexOf('.') < 0) continue;
                out.add(line.toLowerCase());
            }
            r.close();
        } finally { con.disconnect(); }
        return out;
    }

    private static void write(File f, Set<String> set) {
        try (OutputStream os = new FileOutputStream(f)) {
            StringBuilder sb = new StringBuilder(1 << 20);
            for (String s : set) { sb.append(s).append('\n'); if (sb.length() > (1 << 20)) { os.write(sb.toString().getBytes()); sb.setLength(0); } }
            os.write(sb.toString().getBytes());
        } catch (Exception e) { Log.w(TAG, "cache write failed: " + e); }
    }

    private static Set<String> readCache(File f) {
        Set<String> out = new HashSet<>();
        if (!f.exists()) return out;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(f)), 1 << 16)) {
            String l;
            while ((l = r.readLine()) != null) if (!l.isEmpty()) out.add(l);
        } catch (Exception e) { Log.w(TAG, "cache read failed: " + e); }
        return out;
    }
}
