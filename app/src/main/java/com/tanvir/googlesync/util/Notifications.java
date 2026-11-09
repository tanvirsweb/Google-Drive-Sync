package com.tanvir.googlesync.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.tanvir.googlesync.R;
import com.tanvir.googlesync.ui.MainActivity;

public final class Notifications {
    public static final String CH_PROGRESS = "sync_progress";
    public static final String CH_WATCHER = "folder_watcher";
    public static final String CH_ALERTS = "sync_alerts";

    public static final int ID_WATCHER = 1;
    private static final int ID_PROGRESS_BASE = 1000;
    private static final int ID_ALERT_BASE = 100000;

    private Notifications() {}

    public static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_PROGRESS,
                ctx.getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW));
        NotificationChannel watcher = new NotificationChannel(CH_WATCHER,
                ctx.getString(R.string.channel_watcher), NotificationManager.IMPORTANCE_MIN);
        watcher.setShowBadge(false);
        nm.createNotificationChannel(watcher);
        nm.createNotificationChannel(new NotificationChannel(CH_ALERTS,
                ctx.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT));
    }

    public static int progressId(long pairId) {
        return ID_PROGRESS_BASE + (int) (pairId % 90000);
    }

    private static PendingIntent openApp(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static NotificationCompat.Builder progress(Context ctx, String title, String text, int percent) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CH_PROGRESS)
                .setSmallIcon(R.drawable.ic_sync)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(openApp(ctx))
                .setColor(ctx.getColor(R.color.brand_green));
        if (percent >= 0) b.setProgress(100, percent, false);
        else b.setProgress(0, 0, true);
        return b;
    }

    public static NotificationCompat.Builder watcher(Context ctx, int folders) {
        return new NotificationCompat.Builder(ctx, CH_WATCHER)
                .setSmallIcon(R.drawable.ic_sync)
                .setContentTitle(ctx.getString(R.string.watcher_title))
                .setContentText(ctx.getResources().getQuantityString(R.plurals.watcher_text, folders, folders))
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(openApp(ctx))
                .setColor(ctx.getColor(R.color.brand_green));
    }

    @SuppressWarnings("MissingPermission")
    public static void alert(Context ctx, long pairId, String title, String text) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
        if (!nm.areNotificationsEnabled()) return;
        nm.notify(ID_ALERT_BASE + (int) (pairId % 90000), new NotificationCompat.Builder(ctx, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_sync)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openApp(ctx))
                .setColor(ctx.getColor(R.color.brand_green))
                .build());
    }
}
