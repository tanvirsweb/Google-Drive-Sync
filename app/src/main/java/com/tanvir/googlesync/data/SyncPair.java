package com.tanvir.googlesync.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A local folder paired with a Google Drive folder. */
@Entity(tableName = "sync_pairs")
public class SyncPair {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull public String name = "";
    /** Absolute filesystem path, e.g. /storage/emulated/0/DCIM/Camera. */
    @NonNull public String localPath = "";
    /** Path inside Drive without the remote prefix, e.g. "Backups/Camera" ("" = My Drive root). */
    @NonNull public String remotePath = "";

    @NonNull public SyncMode mode = SyncMode.TWO_WAY;
    @NonNull public ConflictPolicy conflictPolicy = ConflictPolicy.KEEP_NEWEST;
    /** One-way modes only: also delete files on the destination that were removed on the source. */
    public boolean mirrorDeletions = false;
    /** Move local files that a sync would overwrite or delete into the app's backup folder. */
    public boolean keepLocalBackups = true;

    // ---- Filters ----
    /** Comma separated, e.g. "jpg, png". Empty = everything. */
    @NonNull public String includeExtensions = "";
    /** Comma separated, e.g. "tmp, log". */
    @NonNull public String excludeExtensions = "";
    /** 0 = no limit. */
    public long minSizeKb = 0;
    /** 0 = no limit. */
    public long maxSizeKb = 0;
    /** Extra rclone filter rules, one per line ("+ pattern" / "- pattern"). */
    @NonNull public String customRules = "";
    public boolean skipHidden = false;

    // ---- Schedule ----
    @NonNull public ScheduleType scheduleType = ScheduleType.INTERVAL;
    public int intervalMinutes = 60;
    public boolean wifiOnly = false;
    public boolean chargingOnly = false;
    public boolean enabled = true;

    // ---- State ----
    @NonNull public SyncStatus status = SyncStatus.NEVER;
    @NonNull public String statusMessage = "";
    public long lastSyncTime = 0;
    public long lastSuccessTime = 0;
    /** rclone bisync needs one --resync run before regular runs. */
    public boolean bisyncInitialized = false;
    /** Local files changed since the last successful sync (seen by the folder watcher). */
    public boolean pendingLocalChanges = false;
    public long createdAt = System.currentTimeMillis();
}
