package com.tanvir.googlesync.ui;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.tanvir.googlesync.R;
import com.tanvir.googlesync.data.SyncMode;
import com.tanvir.googlesync.data.SyncPair;

final class Labels {
    private Labels() {}

    static final int[] INTERVALS = {15, 30, 60, 120, 360, 720, 1440};

    @NonNull static String interval(int minutes) {
        if (minutes < 60) return minutes + " min";
        if (minutes % 1440 == 0) return minutes / 1440 == 1 ? "24 h" : (minutes / 1440) + " days";
        return (minutes / 60) + " h";
    }

    @NonNull static String mode(Context c, SyncMode m) {
        switch (m) {
            case UPLOAD: return c.getString(R.string.mode_upload);
            case DOWNLOAD: return c.getString(R.string.mode_download);
            default: return c.getString(R.string.mode_two_way);
        }
    }

    @DrawableRes static int modeIcon(SyncMode m) {
        switch (m) {
            case UPLOAD: return R.drawable.ic_upload;
            case DOWNLOAD: return R.drawable.ic_download;
            default: return R.drawable.ic_swap;
        }
    }

    @NonNull static String modeSummary(Context c, SyncPair p) {
        StringBuilder sb = new StringBuilder(mode(c, p.mode));
        switch (p.scheduleType) {
            case MANUAL:
                sb.append(" · ").append(c.getString(R.string.schedule_manual));
                break;
            case INTERVAL:
                sb.append(" · ").append(c.getString(R.string.schedule_interval, interval(p.intervalMinutes)));
                break;
            case REALTIME:
                sb.append(" · ").append(c.getString(R.string.schedule_realtime));
                break;
        }
        if (p.wifiOnly) sb.append(" · Wi-Fi");
        return sb.toString();
    }
}
