package com.kkronly.adcease;

import android.content.Context;
import android.content.SharedPreferences;

/** AdCease shared state.  Developer: Kaustav Kanti Ray @iamkkronly */
public final class Prefs {
    /** Developer: Kaustav Kanti Ray @iamkkronly */
    public static final String DEVELOPER = "Kaustav Kanti Ray @iamkkronly";

    private static final String FILE = "adcease_prefs";

    public static final String KEY_ENABLED       = "vpn_enabled";
    public static final String KEY_ONBOARDED     = "onboarded";
    public static final String KEY_TOTAL         = "stat_total";
    public static final String KEY_BLOCKED       = "stat_blocked";
    public static final String KEY_THREATS       = "stat_threats";
    public static final String KEY_LIST_UPDATED  = "list_updated_at";
    public static final String KEY_LIST_SIZE     = "list_size";

    public static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context c)   { return get(c).getBoolean(KEY_ENABLED, false); }
    public static void setEnabled(Context c, boolean v) { get(c).edit().putBoolean(KEY_ENABLED, v).apply(); }

    public static boolean isOnboarded(Context c) { return get(c).getBoolean(KEY_ONBOARDED, false); }
    public static void setOnboarded(Context c)   { get(c).edit().putBoolean(KEY_ONBOARDED, true).apply(); }

    public static void bump(Context c, String key, int by) {
        SharedPreferences p = get(c);
        p.edit().putLong(key, p.getLong(key, 0L) + by).apply();
    }

    public static long stat(Context c, String key) { return get(c).getLong(key, 0L); }

    public static void resetStats(Context c) {
        get(c).edit().putLong(KEY_TOTAL, 0).putLong(KEY_BLOCKED, 0).putLong(KEY_THREATS, 0).apply();
    }

    private Prefs() {}
}
