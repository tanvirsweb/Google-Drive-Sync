package com.tanvir.googlesync.sync;

import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tanvir.googlesync.R;
import com.tanvir.googlesync.data.AppDatabase;
import com.tanvir.googlesync.data.SyncPair;
import com.tanvir.googlesync.data.SyncStatus;
import com.tanvir.googlesync.rclone.Rclone;
import com.tanvir.googlesync.util.Notifications;

/** WorkManager entry point for one sync run; shows a foreground progress notification. */
public class SyncWorker extends Worker {

    public static final String KEY_PAIR_ID = "pair_id";
    public static final String KEY_MANUAL = "manual";
    /** After this many attempts a failing sync waits for its next scheduled run instead. */
    private static final int MAX_RETRIES = 8;

    private final Rclone.Handle handle = new Rclone.Handle();

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static Data input(long pairId, boolean manual) {
        return new Data.Builder().putLong(KEY_PAIR_ID, pairId).putBoolean(KEY_MANUAL, manual).build();
    }

    @NonNull @Override
    public Result doWork() {
        long id = getInputData().getLong(KEY_PAIR_ID, -1);
        if (id < 0) return Result.failure();
        SyncPair pair = AppDatabase.get(getApplicationContext()).pairs().get(id);
        if (pair == null) return Result.success();

        try {
            setForegroundAsync(foregroundInfo(pair, getApplicationContext().getString(R.string.sync_starting), -1));
        } catch (Exception ignored) {
            // Foreground not allowed right now (background start restrictions); run anyway.
        }

        SyncEngine.Outcome outcome = new SyncEngine(getApplicationContext()).run(id, handle,
                (p, text, percent) -> {
                    try {
                        setForegroundAsync(foregroundInfo(p, text, percent));
                    } catch (Exception ignored) {
                    }
                });

        switch (outcome) {
            case SUCCESS:
            case SKIPPED:
                return Result.success();
            case RETRY_LATER:
                return getRunAttemptCount() < MAX_RETRIES ? Result.retry() : Result.failure();
            case FAILED:
            default:
                SyncPair after = AppDatabase.get(getApplicationContext()).pairs().get(id);
                if (after != null && after.status == SyncStatus.FAILED) {
                    Notifications.alert(getApplicationContext(), id,
                            getApplicationContext().getString(R.string.sync_failed_title, after.name),
                            after.statusMessage);
                }
                return Result.failure();
        }
    }

    @Override
    public void onStopped() {
        handle.cancel();
    }

    /** Needed for expedited work on Android 11 and older. */
    @NonNull @Override
    public ForegroundInfo getForegroundInfo() {
        SyncPair stub = new SyncPair();
        stub.id = getInputData().getLong(KEY_PAIR_ID, -1);
        stub.name = getApplicationContext().getString(R.string.app_name);
        return foregroundInfo(stub, getApplicationContext().getString(R.string.sync_starting), -1);
    }

    private ForegroundInfo foregroundInfo(SyncPair pair, String text, int percent) {
        Context ctx = getApplicationContext();
        NotificationCompat.Builder b = Notifications.progress(ctx,
                ctx.getString(R.string.syncing_title, pair.name), text, percent);
        int nid = Notifications.progressId(pair.id);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new ForegroundInfo(nid, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }
        return new ForegroundInfo(nid, b.build());
    }
}
