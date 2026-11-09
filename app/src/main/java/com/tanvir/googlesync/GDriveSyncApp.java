package com.tanvir.googlesync;

import android.app.Application;

import com.tanvir.googlesync.data.AppDatabase;
import com.tanvir.googlesync.data.SyncPair;
import com.tanvir.googlesync.data.SyncStatus;
import com.tanvir.googlesync.sync.SyncEngine;
import com.tanvir.googlesync.sync.SyncScheduler;
import com.tanvir.googlesync.util.Notifications;

public class GDriveSyncApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Notifications.createChannels(this);
        // A process killed mid-sync leaves stale RUNNING rows behind.
        SyncScheduler.runInBackground(() -> {
            for (SyncPair p : AppDatabase.get(this).pairs().getAll()) {
                if (p.status == SyncStatus.RUNNING && !SyncEngine.isRunning(p.id)) {
                    AppDatabase.get(this).pairs().setStatus(p.id, SyncStatus.QUEUED, "Interrupted — will resume");
                }
            }
        });
    }
}
