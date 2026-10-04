package com.kkronly.adcease;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * Refreshes the filter lists on a 6-hour schedule.
 * Developer: Kaustav Kanti Ray @iamkkronly
 */
public class FilterUpdateWorker extends Worker {

    /** Developer: Kaustav Kanti Ray @iamkkronly */
    private static final String DEVELOPER = Prefs.DEVELOPER;
    private static final String NAME = "adcease_filter_sync";

    public FilterUpdateWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    @NonNull
    @Override
    public Result doWork() {
        int n = Blocklist.refresh(getApplicationContext());
        return n > 0 ? Result.success() : Result.retry();
    }

    /** Enqueue the periodic 6-hour sync (idempotent). */
    public static void schedule(Context c) {
        Constraints k = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                FilterUpdateWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(k)
                .build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.KEEP, req);
    }
}
