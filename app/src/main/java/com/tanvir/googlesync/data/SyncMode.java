package com.tanvir.googlesync.data;

/** Direction of a folder pair. */
public enum SyncMode {
    /** Device → Drive. */
    UPLOAD,
    /** Drive → device. */
    DOWNLOAD,
    /** Changes on either side are propagated to the other (rclone bisync). */
    TWO_WAY
}
