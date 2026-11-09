package com.tanvir.googlesync.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the folder watcher after reboot / app update. WorkManager restores periodic jobs itself. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        SyncScheduler.runInBackground(() -> {
            try {
                SyncScheduler.rescheduleAll(app);
            } finally {
                pending.finish();
            }
        });
    }
}
