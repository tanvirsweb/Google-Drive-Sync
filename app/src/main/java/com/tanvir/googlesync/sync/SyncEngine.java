package com.tanvir.googlesync.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tanvir.googlesync.data.AppDatabase;
import com.tanvir.googlesync.data.SyncMode;
import com.tanvir.googlesync.data.SyncPair;
import com.tanvir.googlesync.data.SyncPairDao;
import com.tanvir.googlesync.data.SyncStatus;
import com.tanvir.googlesync.rclone.DriveRemote;
import com.tanvir.googlesync.rclone.Rclone;
import com.tanvir.googlesync.rclone.RcloneLog;
import com.tanvir.googlesync.util.AppSettings;
import com.tanvir.googlesync.util.NetworkUtils;
import com.tanvir.googlesync.util.StoragePaths;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs one sync of a folder pair with rclone.
 *
 * Offline behaviour: the local folder is always the working copy. If the device is offline, the
 * run stops before touching anything, the pair is marked "waiting for network", and the caller
 * retries later — local files are never removed or rolled back because Drive was unreachable.
 */
public final class SyncEngine {

    public enum Outcome { SUCCESS, RETRY_LATER, FAILED, SKIPPED }

    public interface ProgressListener {
        void onProgress(@NonNull SyncPair pair, @NonNull String text, int percent);
    }

    private static final Set<Long> RUNNING = ConcurrentHashMap.newKeySet();
    private static final long MESSAGE_THROTTLE_MS = 1500;
    private static final int MAX_LOG_LINES = 5000;

    private final Context app;
    private final SyncPairDao dao;
    private final Rclone rclone;

    public SyncEngine(Context context) {
        app = context.getApplicationContext();
        dao = AppDatabase.get(app).pairs();
        rclone = new Rclone(app);
    }

    public static boolean isRunning(long pairId) {
        return RUNNING.contains(pairId);
    }

    public static File logFile(Context ctx, long pairId) {
        File dir = new File(ctx.getFilesDir(), "logs");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "pair-" + pairId + ".log");
    }

    @NonNull public Outcome run(long pairId, @Nullable Rclone.Handle handle, @Nullable ProgressListener listener) {
        if (!RUNNING.add(pairId)) return Outcome.SKIPPED; // already syncing (periodic + manual overlap)
        try {
            return runLocked(pairId, handle == null ? new Rclone.Handle() : handle, listener);
        } finally {
            RUNNING.remove(pairId);
        }
    }

    private Outcome runLocked(long pairId, Rclone.Handle handle, @Nullable ProgressListener listener) {
        SyncPair pair = dao.get(pairId);
        if (pair == null || !pair.enabled) return Outcome.SKIPPED;

        if (!new DriveRemote(app).isConfigured()) {
            dao.setStatus(pairId, SyncStatus.FAILED, "Google account not connected");
            return Outcome.FAILED;
        }
        if (!StoragePaths.hasStorageAccess(app)) {
            dao.setStatus(pairId, SyncStatus.FAILED, "Storage access not granted");
            return Outcome.FAILED;
        }
        if (!NetworkUtils.isOnline(app)) {
            dao.setStatus(pairId, SyncStatus.WAITING_FOR_NETWORK, offlineMessage(pair));
            return Outcome.RETRY_LATER;
        }

        File local = new File(pair.localPath);
        if (!local.isDirectory()) {
            if (pair.mode == SyncMode.UPLOAD) {
                dao.setStatus(pairId, SyncStatus.FAILED, "Local folder not found: " + pair.localPath);
                return Outcome.FAILED;
            }
            //noinspection ResultOfMethodCallIgnored
            local.mkdirs();
            if (!local.isDirectory()) {
                dao.setStatus(pairId, SyncStatus.FAILED, "Cannot create local folder: " + pair.localPath);
                return Outcome.FAILED;
            }
        }

        dao.setStatus(pairId, SyncStatus.RUNNING, "Starting…");
        if (listener != null) listener.onProgress(pair, "Starting…", -1);

        String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File backupDir = backupDirFor(pair, ts);

        try (PrintWriter log = openLog(pairId)) {
            log.println("=== " + pair.name + " · " + new Date() + " ===");
            log.println(pair.mode + " " + pair.localPath + " ⇄ gdrive:" + pair.remotePath);

            // Drive folder must exist for upload and bisync.
            if (pair.mode != SyncMode.DOWNLOAD) {
                Rclone.Result mk = rclone.run(java.util.Arrays.asList("mkdir",
                        DriveRemote.remoteSpec(SyncCommandBuilder.remotePath(pair.remotePath))), null, handle);
                if (!mk.ok() && !mk.cancelled) {
                    return fail(pair, mk, log);
                }
            }

            boolean resync = pair.mode == SyncMode.TWO_WAY && !pair.bisyncInitialized;
            Rclone.Result r = runSync(pair, backupDir, ts, resync, handle, listener, log);

            if (!r.ok() && !r.cancelled && pair.mode == SyncMode.TWO_WAY && !resync && needsResync(r)) {
                // bisync lost its baseline (first run, interrupted run, changed filters…). A resync
                // merges both sides; with backups on, nothing on the device is lost.
                log.println("--- bisync requested --resync, rebuilding baseline ---");
                dao.setMessage(pairId, "Rebuilding sync state…");
                r = runSync(pair, backupDir, ts, true, handle, listener, log);
            }

            if (r.cancelled) {
                dao.setStatus(pairId, SyncStatus.QUEUED, "Stopped");
                log.println("Cancelled");
                return Outcome.RETRY_LATER;
            }
            if (!r.ok()) return fail(pair, r, log);

            SyncPair fresh = dao.get(pairId);
            if (fresh != null) {
                fresh.status = SyncStatus.SUCCESS;
                fresh.statusMessage = lastSummary[0] == null ? "Up to date" : lastSummary[0];
                fresh.lastSyncTime = fresh.lastSuccessTime = System.currentTimeMillis();
                fresh.pendingLocalChanges = false;
                if (fresh.mode == SyncMode.TWO_WAY) fresh.bisyncInitialized = true;
                dao.update(fresh);
            }
            log.println("Finished OK");
            return Outcome.SUCCESS;
        } catch (IOException e) {
            dao.setStatus(pairId, SyncStatus.FAILED, e.getMessage() == null ? e.toString() : e.getMessage());
            return Outcome.FAILED;
        } catch (InterruptedException e) {
            handle.cancel();
            Thread.currentThread().interrupt();
            dao.setStatus(pairId, SyncStatus.QUEUED, "Stopped");
            return Outcome.RETRY_LATER;
        } finally {
            lastSummary[0] = null;
            pruneBackups(pair);
        }
    }

    private final String[] lastSummary = new String[1];

    private Rclone.Result runSync(SyncPair pair, @Nullable File backupDir, String ts, boolean resync,
                                  Rclone.Handle handle, @Nullable ProgressListener listener,
                                  PrintWriter log) throws IOException, InterruptedException {
        SyncCommandBuilder.Options opt = new SyncCommandBuilder.Options();
        opt.localBackupDir = backupDir == null ? null : backupDir.getAbsolutePath();
        opt.bisyncWorkDir = rclone.bisyncWorkDir().getAbsolutePath();
        opt.timestamp = ts;
        opt.resync = resync;
        List<String> args = SyncCommandBuilder.build(pair, opt);
        log.println("$ rclone " + String.join(" ", args));
        log.flush();

        final long[] lastUpdate = {0};
        final int[] lines = {0};
        return rclone.run(args, line -> {
            RcloneLog.Entry e = RcloneLog.parse(line);
            if (e == null) {
                if (lines[0]++ < MAX_LOG_LINES) log.println(line);
                return;
            }
            if (e.stats != null) {
                RcloneLog.Stats s = e.stats;
                String text = s.describe();
                if (s.totalTransfers > 0 || s.deletes > 0) {
                    lastSummary[0] = "Synced · " + s.transfers + " transferred"
                            + (s.deletes > 0 ? ", " + s.deletes + " deleted" : "");
                }
                long now = System.currentTimeMillis();
                if (now - lastUpdate[0] > MESSAGE_THROTTLE_MS) {
                    lastUpdate[0] = now;
                    dao.setMessage(pair.id, text);
                    if (listener != null) listener.onProgress(pair, text, s.percent());
                }
                return;
            }
            if (lines[0]++ < MAX_LOG_LINES) {
                log.println(e.level.toUpperCase(Locale.ROOT) + "  " + RcloneLog.humanize(line));
            }
        }, handle);
    }

    private Outcome fail(SyncPair pair, Rclone.Result r, PrintWriter log) {
        String summary = r.errorSummary();
        log.println("Failed (exit " + r.exitCode + "): " + summary);
        boolean network = r.exitCode == 5 || RcloneLog.looksLikeNetworkError(r.stderrTail)
                || !NetworkUtils.isOnline(app);
        if (network) {
            dao.setStatus(pair.id, SyncStatus.WAITING_FOR_NETWORK, offlineMessage(pair));
            return Outcome.RETRY_LATER;
        }
        SyncPair fresh = dao.get(pair.id);
        if (fresh != null) {
            fresh.status = SyncStatus.FAILED;
            fresh.statusMessage = summary;
            fresh.lastSyncTime = System.currentTimeMillis();
            dao.update(fresh);
        }
        return Outcome.FAILED;
    }

    static boolean needsResync(Rclone.Result r) {
        String t = r.stderrTail;
        return t.contains("--resync") || t.contains("cannot find prior Path1 or Path2 listings");
    }

    @NonNull static String offlineMessage(SyncPair pair) {
        return "Offline — files are kept on this device and will sync when you're back online";
    }

    private PrintWriter openLog(long pairId) throws IOException {
        return new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(logFile(app, pairId), false), StandardCharsets.UTF_8), true);
    }

    // ---- Local backups ----

    @NonNull public static File backupRoot(Context ctx, SyncPair pair) {
        File ext = ctx.getExternalFilesDir(null);
        // rclone refuses a backup dir inside the synced folder, so fall back to internal storage
        // when the user syncs a folder that contains the app's external dir (e.g. storage root).
        File base = ext != null && !StoragePaths.isInside(ext.getAbsolutePath(), pair.localPath)
                ? ext : ctx.getFilesDir();
        return new File(base, "backups/pair-" + pair.id);
    }

    @Nullable private File backupDirFor(SyncPair pair, String ts) {
        if (!pair.keepLocalBackups || pair.mode == SyncMode.UPLOAD) return null;
        return new File(backupRoot(app, pair), ts);
    }

    private void pruneBackups(SyncPair pair) {
        File root = backupRoot(app, pair);
        File[] runs = root.listFiles();
        if (runs == null) return;
        long cutoff = System.currentTimeMillis()
                - new AppSettings(app).backupRetentionDays() * 24L * 3600_000L;
        for (File run : runs) {
            if (run.lastModified() < cutoff) deleteTree(run);
        }
    }

    static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
