package com.tanvir.googlesync.data;

public enum SyncStatus {
    NEVER,
    QUEUED,
    RUNNING,
    SUCCESS,
    /** Device offline: local files are kept as-is and the sync runs once a network is back. */
    WAITING_FOR_NETWORK,
    FAILED
}
