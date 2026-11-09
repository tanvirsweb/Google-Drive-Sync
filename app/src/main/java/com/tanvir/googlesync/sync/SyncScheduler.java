package com.tanvir.googlesync.sync;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.tanvir.googlesync.data.AppDatabase;
import com.tanvir.googlesync.data.ScheduleType;
import com.tanvir.googlesync.data.SyncMode;
import com.tanvir.googlesync.data.SyncPair;
import com.tanvir.googlesync.data.SyncStatus;
import com.tanvir.googlesync.util.NetworkUtils;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Schedules sync work. Every request carries a network constraint, so while the device is offline
 * the work simply waits in WorkManager's queue and runs as soon as connectivity returns.
 */
public final class SyncScheduler {

    public static final String TAG_SYNC = "gdrivesync";
    private static final ExecutorService IO = Executors.newCachedThreadPool();

    private SyncScheduler() {}

    public static String periodicName(long id) { return "periodic-" + id; }
    public static String oneTimeName(long id) { return "sync-" + id; }
    public static String pairTag(long id) { return "pair-" + id; }

    private static Constraints constraints(SyncPair pair, boolean manual) {
        Constraints.Builder c = new Constraints.Builder()
                .setRequiredNetworkType(!manual && pair.wifiOnly ? NetworkType.UNMETERED : NetworkType.CONNECTED);
        if (!manual && pair.chargingOnly) c.setRequiresCharging(true);
        return c.build();
    }

    /** (Re)creates the periodic job of a pair to match its settings. */
    public static void schedule(Context ctx, SyncPair pair) {
        WorkManager wm = WorkManager.getInstance(ctx);
        if (!pair.enabled || pair.scheduleType == ScheduleType.MANUAL) {
            wm.cancelUniqueWork(periodicName(pair.id));
            return;
        }
        long minutes = Math.max(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS / 60000, pair.intervalMinutes);
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(SyncWorker.class, minutes, TimeUnit.MINUTES)
                .setConstraints(constraints(pair, false))
                .setInputData(SyncWorker.input(pair.id, false))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .addTag(TAG_SYNC).addTag(pairTag(pair.id))
                .build();
        wm.enqueueUniquePeriodicWork(periodicName(pair.id), ExistingPeriodicWorkPolicy.UPDATE, req);
    }

    /**
     * Queues a sync now. Changes made while offline are kept locally and this request waits for
     * the network. Requests made while a sync is running are appended, so nothing is missed.
     */
    public static void syncNow(Context ctx, long pairId, boolean manual) {
        Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            SyncPair pair = AppDatabase.get(app).pairs().get(pairId);
            if (pair == null) return;
            OneTimeWorkRequest.Builder b = new OneTimeWorkRequest.Builder(SyncWorker.class)
                    .setConstraints(constraints(pair, manual))
                    .setInputData(SyncWorker.input(pairId, manual))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .addTag(TAG_SYNC).addTag(pairTag(pairId));
            if (manual) b.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
            WorkManager.getInstance(app).enqueueUniqueWork(oneTimeName(pairId),
                    ExistingWorkPolicy.APPEND_OR_REPLACE, b.build());
            if (!SyncEngine.isRunning(pairId)) {
                boolean online = NetworkUtils.isOnline(app);
                AppDatabase.get(app).pairs().setStatus(pairId,
                        online ? SyncStatus.QUEUED : SyncStatus.WAITING_FOR_NETWORK,
                        online ? "Queued" : SyncEngine.offlineMessage(pair));
            }
        });
    }

    public static void cancel(Context ctx, long pairId) {
        WorkManager wm = WorkManager.getInstance(ctx);
        wm.cancelUniqueWork(periodicName(pairId));
        wm.cancelUniqueWork(oneTimeName(pairId));
    }

    /** Stops the running sync of a pair; it stays scheduled. */
    public static void stop(Context ctx, long pairId) {
        WorkManager.getInstance(ctx).cancelUniqueWork(oneTimeName(pairId));
    }

    /** Re-applies all schedules and starts/stops the folder watcher. Call off the main thread. */
    public static void rescheduleAll(Context ctx) {
        List<SyncPair> pairs = AppDatabase.get(ctx).pairs().getAll();
        for (SyncPair p : pairs) schedule(ctx, p);
        refreshWatcher(ctx, pairs);
    }

    public static void refreshWatcher(Context ctx, List<SyncPair> pairs) {
        boolean need = false;
        for (SyncPair p : pairs) need |= needsWatcher(p);
        Intent i = new Intent(ctx, FolderWatcherService.class);
        if (need) {
            try {
                ContextCompat.startForegroundService(ctx, i.setAction(FolderWatcherService.ACTION_REFRESH));
            } catch (RuntimeException ignored) {
                // Background start not allowed right now; it starts next time the app opens.
            }
        } else {
            ctx.stopService(i);
        }
    }

    static boolean needsWatcher(SyncPair p) {
        return p.enabled && p.scheduleType == ScheduleType.REALTIME && p.mode != SyncMode.DOWNLOAD;
    }

    public static void runInBackground(Runnable r) {
        IO.execute(r);
    }
}
