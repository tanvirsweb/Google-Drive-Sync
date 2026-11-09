package com.tanvir.googlesync.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tanvir.googlesync.data.ConflictPolicy;
import com.tanvir.googlesync.data.SyncMode;
import com.tanvir.googlesync.data.SyncPair;
import com.tanvir.googlesync.rclone.DriveRemote;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Translates a {@link SyncPair} into rclone arguments. Pure Java so it can be unit tested.
 *
 * Offline safety is built in here: one-way downloads use {@code copy} unless the user opted into
 * mirroring deletions, and whenever local files would be overwritten or deleted they are moved to
 * a backup folder first (or renamed in place for "keep both").
 */
public final class SyncCommandBuilder {

    /** Suffix stem for conflict copies, e.g. "report.conflict-20261109-101500.pdf". */
    public static final String CONFLICT_SUFFIX = ".conflict-";

    /**
     * bisync names its state files "<path1>..<path2>.path1.lst-dry-new" etc. from both full paths,
     * and fails once that exceeds the 255-byte filename limit. Keep headroom for the suffixes.
     */
    public static final int MAX_BISYNC_SESSION_NAME = 225;

    private SyncCommandBuilder() {}

    /** Length of the name bisync derives from the pair's two paths (see rclone bilib.SessionName). */
    public static int bisyncSessionNameLength(@NonNull SyncPair pair) {
        String l = canonical(localPath(pair.localPath));
        String r = canonical(DriveRemote.remoteSpec(remotePath(pair.remotePath)));
        return (l + ".." + r).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private static String canonical(String p) {
        String t = p.replaceAll("^[\\\\/]+|[\\\\/]+$", "");
        return t.replaceAll("[\\s\\\\/:?*]", "_");
    }

    public static final class Options {
        /** Where local files that would be overwritten/deleted are moved. Null disables backups. */
        @Nullable public String localBackupDir;
        @NonNull public String bisyncWorkDir = "";
        /** Run timestamp, used for conflict suffixes: yyyyMMdd-HHmmss. */
        @NonNull public String timestamp = "";
        /** Two-way only: rebuild the bisync baseline. */
        public boolean resync;
    }

    @NonNull public static List<String> build(@NonNull SyncPair pair, @NonNull Options opt) {
        List<String> a = new ArrayList<>();
        String local = localPath(pair.localPath);
        String remote = DriveRemote.remoteSpec(remotePath(pair.remotePath));
        ConflictPolicy policy = pair.conflictPolicy;
        boolean backups = pair.keepLocalBackups && opt.localBackupDir != null;

        if (pair.mode == SyncMode.TWO_WAY) {
            a.add("bisync");
            a.add(local);
            a.add(remote);
            a.add("--workdir");
            a.add(opt.bisyncWorkDir);
            a.add("--resilient");
            a.add("--recover");
            a.add("--max-lock");
            a.add("2m");
            a.add("--create-empty-src-dirs");
            // Native Google Docs have no fixed size and can't round-trip as files.
            a.add("--drive-skip-gdocs");
            switch (policy) {
                case REPLACE:
                    add(a, "--conflict-resolve", "path1", "--conflict-loser", "delete");
                    break;
                case KEEP_NEWEST:
                    add(a, "--conflict-resolve", "newer", "--conflict-loser", "delete");
                    break;
                case KEEP_BOTH:
                default:
                    add(a, "--conflict-resolve", "none", "--conflict-loser", "num");
                    break;
            }
            add(a, "--conflict-suffix", "conflict");
            if (backups) add(a, "--backup-dir1", opt.localBackupDir);
            if (opt.resync) {
                a.add("--resync");
                add(a, "--resync-mode", policy == ConflictPolicy.REPLACE ? "path1" : "newer");
            }
        } else {
            a.add(pair.mirrorDeletions ? "sync" : "copy");
            if (pair.mode == SyncMode.UPLOAD) {
                a.add(local);
                a.add(remote);
            } else {
                a.add(remote);
                a.add(local);
            }
            a.add("--create-empty-src-dirs");
            switch (policy) {
                case KEEP_NEWEST:
                    a.add("--update");
                    break;
                case KEEP_BOTH:
                    // Files that would be overwritten are renamed in place instead.
                    add(a, "--suffix", CONFLICT_SUFFIX + opt.timestamp);
                    a.add("--suffix-keep-extension");
                    break;
                case REPLACE:
                default:
                    break;
            }
            if (pair.mode == SyncMode.DOWNLOAD && backups && policy != ConflictPolicy.KEEP_BOTH) {
                add(a, "--backup-dir", opt.localBackupDir);
            }
        }

        a.addAll(filterArgs(pair));
        a.addAll(commonArgs());
        return a;
    }

    @NonNull static List<String> commonArgs() {
        List<String> a = new ArrayList<>();
        a.add("--use-json-log");
        a.add("-v");
        add(a, "--stats", "2s");
        add(a, "--retries", "3");
        add(a, "--low-level-retries", "10");
        add(a, "--contimeout", "30s");
        add(a, "--timeout", "5m");
        add(a, "--transfers", "4");
        add(a, "--checkers", "8");
        a.add("--fast-list");
        return a;
    }

    @NonNull static List<String> filterArgs(@NonNull SyncPair pair) {
        List<String> rules = new ArrayList<>();
        // rclone's in-progress downloads.
        rules.add("- *.partial");
        for (String line : pair.customRules.split("\n")) {
            String r = line.trim();
            if (r.isEmpty() || r.startsWith("#")) continue;
            if (r.startsWith("+ ") || r.startsWith("- ")) rules.add(r);
            else rules.add("- " + r);
        }
        if (pair.mode != SyncMode.TWO_WAY && pair.mirrorDeletions
                && pair.conflictPolicy == ConflictPolicy.KEEP_BOTH) {
            // rclone docs: exclude the suffix with sync --suffix, or the next run deletes the copies.
            rules.add("- *" + CONFLICT_SUFFIX + "*");
        }
        Set<String> exclude = parseExtensions(pair.excludeExtensions);
        if (!exclude.isEmpty()) rules.add("- " + extensionGlob(exclude));
        if (pair.skipHidden) {
            rules.add("- .*");
            rules.add("- .*/**");
        }
        Set<String> include = parseExtensions(pair.includeExtensions);
        if (!include.isEmpty()) {
            rules.add("+ " + extensionGlob(include));
            rules.add("- **");
        }

        List<String> a = new ArrayList<>();
        for (String r : rules) add(a, "--filter", r);
        if (!include.isEmpty() || !exclude.isEmpty()) a.add("--ignore-case");
        if (pair.minSizeKb > 0) add(a, "--min-size", pair.minSizeKb + "K");
        if (pair.maxSizeKb > 0) add(a, "--max-size", pair.maxSizeKb + "K");
        return a;
    }

    /** "jpg, .PNG; *.gif" → {jpg, png, gif}. */
    @NonNull public static Set<String> parseExtensions(@NonNull String raw) {
        Set<String> out = new LinkedHashSet<>();
        for (String p : raw.split("[,;\\s]+")) {
            String e = p.trim().toLowerCase(Locale.ROOT);
            if (e.startsWith("*")) e = e.substring(1);
            while (e.startsWith(".")) e = e.substring(1);
            if (!e.isEmpty() && e.matches("[a-z0-9_+\\-.]+")) out.add(e);
        }
        return out;
    }

    @NonNull static String extensionGlob(@NonNull Set<String> exts) {
        if (exts.size() == 1) return "*." + exts.iterator().next();
        return "*.{" + String.join(",", exts) + "}";
    }

    /** Absolute local path without a trailing slash. */
    @NonNull public static String localPath(@NonNull String p) {
        String s = p.trim();
        while (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** Drive path relative to My Drive, without leading or trailing slashes. */
    @NonNull public static String remotePath(@NonNull String p) {
        String s = p.trim();
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static void add(List<String> a, String... v) {
        for (String s : v) a.add(s);
    }
}
