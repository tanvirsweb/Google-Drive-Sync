package com.tanvir.googlesync.data;

/** What to do when the same file differs on both sides. */
public enum ConflictPolicy {
    /** The source (two-way: this device) overwrites the other copy. */
    REPLACE,
    /** The copy with the newest modification time wins. */
    KEEP_NEWEST,
    /** Never overwrite: keep both copies, the other one renamed. */
    KEEP_BOTH
}
