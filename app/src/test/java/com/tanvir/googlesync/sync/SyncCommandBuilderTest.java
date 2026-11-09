package com.tanvir.googlesync.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.tanvir.googlesync.data.ConflictPolicy;
import com.tanvir.googlesync.data.SyncMode;
import com.tanvir.googlesync.data.SyncPair;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class SyncCommandBuilderTest {

    private static SyncPair pair(SyncMode mode, ConflictPolicy policy) {
        SyncPair p = new SyncPair();
        p.localPath = "/storage/emulated/0/DCIM/Camera/";
        p.remotePath = "/Backups/Camera/";
        p.mode = mode;
        p.conflictPolicy = policy;
        return p;
    }

    private static SyncCommandBuilder.Options opts() {
        SyncCommandBuilder.Options o = new SyncCommandBuilder.Options();
        o.localBackupDir = "/data/backups/pair-1/20261109-101500";
        o.bisyncWorkDir = "/data/bisync";
        o.timestamp = "20261109-101500";
        return o;
    }

    private static boolean hasPair(List<String> args, String flag, String value) {
        for (int i = 0; i < args.size() - 1; i++) {
            if (args.get(i).equals(flag) && args.get(i + 1).equals(value)) return true;
        }
        return false;
    }

    @Test public void uploadUsesCopyFromLocalToDrive() {
        List<String> a = SyncCommandBuilder.build(pair(SyncMode.UPLOAD, ConflictPolicy.REPLACE), opts());
        assertEquals(Arrays.asList("copy", "/storage/emulated/0/DCIM/Camera", "gdrive:Backups/Camera"), a.subList(0, 3));
        // Upload never touches local files, so no local backups.
        assertFalse(a.contains("--backup-dir"));
        assertFalse(a.contains("--update"));
    }

    @Test public void downloadNeverDeletesLocalFilesByDefault() {
        List<String> a = SyncCommandBuilder.build(pair(SyncMode.DOWNLOAD, ConflictPolicy.REPLACE), opts());
        assertEquals("copy", a.get(0));
        assertEquals("gdrive:Backups/Camera", a.get(1));
        assertEquals("/storage/emulated/0/DCIM/Camera", a.get(2));
        assertTrue(hasPair(a, "--backup-dir", "/data/backups/pair-1/20261109-101500"));
    }

    @Test public void mirrorDeletionsUsesSync() {
        SyncPair p = pair(SyncMode.DOWNLOAD, ConflictPolicy.KEEP_NEWEST);
        p.mirrorDeletions = true;
        List<String> a = SyncCommandBuilder.build(p, opts());
        assertEquals("sync", a.get(0));
        assertTrue(a.contains("--update"));
        assertTrue(a.contains("--backup-dir"));
    }

    @Test public void keepBothRenamesInPlaceAndProtectsCopiesFromSync() {
        SyncPair p = pair(SyncMode.DOWNLOAD, ConflictPolicy.KEEP_BOTH);
        p.mirrorDeletions = true;
        List<String> a = SyncCommandBuilder.build(p, opts());
        assertTrue(hasPair(a, "--suffix", ".conflict-20261109-101500"));
        assertTrue(a.contains("--suffix-keep-extension"));
        assertFalse(a.contains("--backup-dir"));
        assertTrue(hasPair(a, "--filter", "- *.conflict-*"));
    }

    @Test public void backupsCanBeDisabled() {
        SyncPair p = pair(SyncMode.DOWNLOAD, ConflictPolicy.REPLACE);
        p.keepLocalBackups = false;
        assertFalse(SyncCommandBuilder.build(p, opts()).contains("--backup-dir"));
    }

    @Test public void twoWayUsesBisyncWithSafety() {
        List<String> a = SyncCommandBuilder.build(pair(SyncMode.TWO_WAY, ConflictPolicy.KEEP_NEWEST), opts());
        assertEquals(Arrays.asList("bisync", "/storage/emulated/0/DCIM/Camera", "gdrive:Backups/Camera"), a.subList(0, 3));
        assertTrue(hasPair(a, "--workdir", "/data/bisync"));
        assertTrue(a.contains("--resilient"));
        assertTrue(a.contains("--recover"));
        assertTrue(hasPair(a, "--conflict-resolve", "newer"));
        assertTrue(hasPair(a, "--backup-dir1", "/data/backups/pair-1/20261109-101500"));
        assertFalse(a.contains("--resync"));
    }

    @Test public void twoWayConflictPolicies() {
        List<String> replace = SyncCommandBuilder.build(pair(SyncMode.TWO_WAY, ConflictPolicy.REPLACE), opts());
        assertTrue(hasPair(replace, "--conflict-resolve", "path1"));
        assertTrue(hasPair(replace, "--conflict-loser", "delete"));
        List<String> both = SyncCommandBuilder.build(pair(SyncMode.TWO_WAY, ConflictPolicy.KEEP_BOTH), opts());
        assertTrue(hasPair(both, "--conflict-resolve", "none"));
        assertTrue(hasPair(both, "--conflict-loser", "num"));
    }

    @Test public void resyncFlag() {
        SyncCommandBuilder.Options o = opts();
        o.resync = true;
        List<String> a = SyncCommandBuilder.build(pair(SyncMode.TWO_WAY, ConflictPolicy.KEEP_NEWEST), o);
        assertTrue(a.contains("--resync"));
        assertTrue(hasPair(a, "--resync-mode", "newer"));
    }

    @Test public void filters() {
        SyncPair p = pair(SyncMode.UPLOAD, ConflictPolicy.REPLACE);
        p.includeExtensions = "JPG, .png; *.heic";
        p.excludeExtensions = "tmp";
        p.minSizeKb = 10;
        p.maxSizeKb = 2048;
        p.skipHidden = true;
        p.customRules = "# comment\n- cache/**\n+ keep.txt\nthumbs.db\n";
        List<String> a = SyncCommandBuilder.build(p, opts());
        assertTrue(hasPair(a, "--filter", "- *.partial"));
        assertTrue(hasPair(a, "--filter", "- cache/**"));
        assertTrue(hasPair(a, "--filter", "+ keep.txt"));
        assertTrue(hasPair(a, "--filter", "- thumbs.db"));
        assertTrue(hasPair(a, "--filter", "- *.tmp"));
        assertTrue(hasPair(a, "--filter", "- .*"));
        assertTrue(hasPair(a, "--filter", "+ *.{jpg,png,heic}"));
        assertTrue(hasPair(a, "--filter", "- **"));
        assertTrue(a.contains("--ignore-case"));
        assertTrue(hasPair(a, "--min-size", "10K"));
        assertTrue(hasPair(a, "--max-size", "2048K"));
        // Include rule must come after excludes, and the catch-all last of the filters.
        assertTrue(a.indexOf("- *.tmp") < a.indexOf("+ *.{jpg,png,heic}"));
        assertTrue(a.indexOf("+ *.{jpg,png,heic}") < a.indexOf("- **"));
    }

    @Test public void noFiltersMeansNoCatchAll() {
        List<String> a = SyncCommandBuilder.build(pair(SyncMode.UPLOAD, ConflictPolicy.REPLACE), opts());
        assertFalse(a.contains("- **"));
        assertFalse(a.contains("--ignore-case"));
    }

    @Test public void parseExtensions() {
        Set<String> s = SyncCommandBuilder.parseExtensions(" *.JPG,,png ; .tar.gz bad/ext ");
        assertEquals(Arrays.asList("jpg", "png", "tar.gz"), Arrays.asList(s.toArray(new String[0])));
    }

    @Test public void driveRootPath() {
        SyncPair p = pair(SyncMode.UPLOAD, ConflictPolicy.REPLACE);
        p.remotePath = "";
        assertEquals("gdrive:", SyncCommandBuilder.build(p, opts()).get(2));
    }

    @Test public void bisyncSessionNameMatchesRclone() {
        SyncPair p = pair(SyncMode.TWO_WAY, ConflictPolicy.KEEP_NEWEST);
        // rclone: "storage_emulated_0_DCIM_Camera..gdrive_Backups_Camera"
        assertEquals("storage_emulated_0_DCIM_Camera..gdrive_Backups_Camera".length(),
                SyncCommandBuilder.bisyncSessionNameLength(p));
        StringBuilder deep = new StringBuilder("/storage/emulated/0");
        for (int i = 0; i < 30; i++) deep.append("/folder").append(i);
        p.localPath = deep.toString();
        assertTrue(SyncCommandBuilder.bisyncSessionNameLength(p) > SyncCommandBuilder.MAX_BISYNC_SESSION_NAME);
    }
}
