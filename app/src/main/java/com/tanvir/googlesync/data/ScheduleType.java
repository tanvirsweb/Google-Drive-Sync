package com.tanvir.googlesync.data;

/** When a folder pair syncs automatically. */
public enum ScheduleType {
    /** Only when the user taps "Sync now". */
    MANUAL,
    /** Periodically, every {@link SyncPair#intervalMinutes}. */
    INTERVAL,
    /** As soon as local files change, plus periodically to pick up Drive-side changes. */
    REALTIME
}
