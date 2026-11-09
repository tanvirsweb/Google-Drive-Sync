package com.tanvir.googlesync.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.tanvir.googlesync.R;
import com.tanvir.googlesync.data.AppDatabase;
import com.tanvir.googlesync.data.ConflictPolicy;
import com.tanvir.googlesync.data.ScheduleType;
import com.tanvir.googlesync.data.SyncMode;
import com.tanvir.googlesync.data.SyncPair;
import com.tanvir.googlesync.data.SyncPairDao;
import com.tanvir.googlesync.databinding.ActivityPairEditorBinding;
import com.tanvir.googlesync.rclone.DriveRemote;
import com.tanvir.googlesync.sync.SyncCommandBuilder;
import com.tanvir.googlesync.sync.SyncScheduler;
import com.tanvir.googlesync.util.StoragePaths;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Creates or edits folder pairs.
 *
 * When creating, several Drive folders can be picked at once. Each becomes its own pair stored in
 * {@code <destination>/<Drive folder name>}, sharing the mode, filter and schedule settings.
 */
public class PairEditorActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "pair_id";

    private ActivityPairEditorBinding b;
    private SyncPairDao dao;
    private SyncPair pair = new SyncPair();
    private boolean isNew = true;
    /** Device folder (single pair) or destination parent folder (several Drive folders). */
    @Nullable private String localPath;
    /** Chosen Drive folders; empty until picked. */
    private final List<String> remotePaths = new ArrayList<>();
    /** Snapshot of fields that invalidate the bisync baseline when changed. */
    private String baselineKey = "";

    private final ActivityResultLauncher<Uri> pickFolder =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) return;
                String path = StoragePaths.treeUriToPath(uri);
                if (path == null) {
                    Toast.makeText(this, R.string.error_unsupported_folder, Toast.LENGTH_LONG).show();
                    return;
                }
                setLocal(path);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPairEditorBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        b.toolbar.setNavigationOnClickListener(v -> finish());
        dao = AppDatabase.get(this).pairs();

        String[] labels = new String[Labels.INTERVALS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = Labels.interval(Labels.INTERVALS[i]);
        b.interval.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));

        b.modeGroup.addOnButtonCheckedListener((g, id, checked) -> { if (checked) updateModeUi(); });
        b.scheduleGroup.addOnButtonCheckedListener((g, id, checked) -> { if (checked) updateScheduleUi(); });
        b.btnLocal.setOnClickListener(v -> {
            if (!StoragePaths.hasStorageAccess(this)) {
                Toast.makeText(this, R.string.storage_permission_text, Toast.LENGTH_LONG).show();
                return;
            }
            pickFolder.launch(null);
        });
        b.btnDefaultLocal.setOnClickListener(v -> {
            if (isMulti()) {
                setLocal(StoragePaths.defaultSyncRoot().getAbsolutePath());
                return;
            }
            String n = safeName(text(b.name));
            if (n.isEmpty() && remotePaths.size() == 1) n = safeName(leafName(remotePaths.get(0)));
            if (n.isEmpty()) n = "Drive";
            setLocal(new File(StoragePaths.defaultSyncRoot(), n).getAbsolutePath());
        });
        b.btnRemote.setOnClickListener(v -> {
            if (!new DriveRemote(this).isConfigured()) {
                startActivity(new Intent(this, AccountActivity.class));
                return;
            }
            String start = remotePaths.size() == 1 ? remotePaths.get(0) : "";
            if (isNew) {
                RemoteFolderPicker.showMulti(this, start, isMulti() ? remotePaths : new ArrayList<>(), this::setRemotes);
            } else {
                RemoteFolderPicker.show(this, start, this::setRemotes);
            }
        });
        b.btnSave.setOnClickListener(v -> save());

        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        if (id >= 0) {
            isNew = false;
            b.toolbar.setTitle(R.string.edit_pair);
            SyncScheduler.runInBackground(() -> {
                SyncPair p = dao.get(id);
                runOnUiThread(() -> {
                    if (p == null) {
                        finish();
                        return;
                    }
                    pair = p;
                    bind();
                });
            });
        } else {
            bind();
        }
    }

    private boolean isMulti() {
        return remotePaths.size() > 1;
    }

    private void bind() {
        b.name.setText(pair.name);
        if (!isNew) {
            setLocal(pair.localPath);
            List<String> one = new ArrayList<>();
            one.add(pair.remotePath);
            setRemotes(one);
        }
        b.modeGroup.check(pair.mode == SyncMode.UPLOAD ? R.id.modeUpload
                : pair.mode == SyncMode.DOWNLOAD ? R.id.modeDownload : R.id.modeTwoWay);
        b.mirrorDeletions.setChecked(pair.mirrorDeletions);
        b.conflictGroup.check(pair.conflictPolicy == ConflictPolicy.REPLACE ? R.id.conflictReplace
                : pair.conflictPolicy == ConflictPolicy.KEEP_BOTH ? R.id.conflictBoth : R.id.conflictNewest);
        b.keepBackups.setChecked(pair.keepLocalBackups);
        b.includeExt.setText(pair.includeExtensions);
        b.excludeExt.setText(pair.excludeExtensions);
        b.minSize.setText(pair.minSizeKb > 0 ? String.valueOf(pair.minSizeKb) : "");
        b.maxSize.setText(pair.maxSizeKb > 0 ? String.valueOf(pair.maxSizeKb) : "");
        b.skipHidden.setChecked(pair.skipHidden);
        b.customRules.setText(pair.customRules);
        b.scheduleGroup.check(pair.scheduleType == ScheduleType.MANUAL ? R.id.scheduleManual
                : pair.scheduleType == ScheduleType.REALTIME ? R.id.scheduleRealtime : R.id.scheduleInterval);
        b.interval.setText(Labels.interval(nearestInterval(pair.intervalMinutes)), false);
        b.wifiOnly.setChecked(pair.wifiOnly);
        b.chargingOnly.setChecked(pair.chargingOnly);
        b.enabled.setChecked(pair.enabled);
        baselineKey = baselineKey(pair);
        updateModeUi();
        updateScheduleUi();
    }

    private void setLocal(String path) {
        localPath = path;
        b.localPath.setText(StoragePaths.pretty(path));
    }

    private void setRemotes(@NonNull List<String> paths) {
        remotePaths.clear();
        for (String p : paths) remotePaths.add(SyncCommandBuilder.remotePath(p));
        if (remotePaths.size() == 1) {
            String r = remotePaths.get(0);
            b.remotePath.setText(r.isEmpty() ? getString(R.string.my_drive) : getString(R.string.my_drive) + "/" + r);
            // Suggest a name from the folder when the user hasn't typed one.
            if (isNew && text(b.name).isEmpty() && !r.isEmpty()) b.name.setText(leafName(r));
        } else {
            List<String> names = new ArrayList<>();
            for (String r : remotePaths) names.add(leafName(r));
            b.remotePath.setText(getResources().getQuantityString(R.plurals.drive_folders_count,
                    remotePaths.size(), remotePaths.size()) + ": " + String.join(", ", names));
        }
        boolean multi = isMulti();
        b.nameLayout.setVisibility(multi ? View.GONE : View.VISIBLE);
        b.multiNote.setVisibility(multi ? View.VISIBLE : View.GONE);
        b.localLabel.setText(multi ? R.string.destination_folder : R.string.local_folder);
    }

    private SyncMode selectedMode() {
        int id = b.modeGroup.getCheckedButtonId();
        return id == R.id.modeUpload ? SyncMode.UPLOAD : id == R.id.modeDownload ? SyncMode.DOWNLOAD : SyncMode.TWO_WAY;
    }

    private ScheduleType selectedSchedule() {
        int id = b.scheduleGroup.getCheckedButtonId();
        return id == R.id.scheduleManual ? ScheduleType.MANUAL
                : id == R.id.scheduleRealtime ? ScheduleType.REALTIME : ScheduleType.INTERVAL;
    }

    private void updateModeUi() {
        SyncMode m = selectedMode();
        b.modeDesc.setText(m == SyncMode.UPLOAD ? R.string.mode_upload_desc
                : m == SyncMode.DOWNLOAD ? R.string.mode_download_desc : R.string.mode_two_way_desc);
        int oneWay = m == SyncMode.TWO_WAY ? View.GONE : View.VISIBLE;
        b.mirrorDeletions.setVisibility(oneWay);
        b.mirrorDeletionsDesc.setVisibility(oneWay);
        int backups = m == SyncMode.UPLOAD ? View.GONE : View.VISIBLE;
        b.keepBackups.setVisibility(backups);
        b.keepBackupsDesc.setVisibility(backups);
    }

    private void updateScheduleUi() {
        ScheduleType s = selectedSchedule();
        b.intervalLayout.setVisibility(s == ScheduleType.MANUAL ? View.GONE : View.VISIBLE);
        b.realtimeDesc.setVisibility(s == ScheduleType.REALTIME ? View.VISIBLE : View.GONE);
        b.chargingOnly.setVisibility(s == ScheduleType.MANUAL ? View.GONE : View.VISIBLE);
        b.wifiOnly.setVisibility(s == ScheduleType.MANUAL ? View.GONE : View.VISIBLE);
    }

    private static int nearestInterval(int minutes) {
        int best = Labels.INTERVALS[0];
        for (int v : Labels.INTERVALS) if (Math.abs(v - minutes) < Math.abs(best - minutes)) best = v;
        return best;
    }

    private int selectedInterval() {
        String t = b.interval.getText().toString();
        for (int v : Labels.INTERVALS) if (Labels.interval(v).equals(t)) return v;
        return 60;
    }

    private static String text(com.google.android.material.textfield.TextInputEditText e) {
        Editable t = e.getText();
        return t == null ? "" : t.toString().trim();
    }

    private static long number(com.google.android.material.textfield.TextInputEditText e) {
        try {
            return Math.max(0, Long.parseLong(text(e)));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @NonNull private String leafName(String remote) {
        return remote.isEmpty() ? getString(R.string.my_drive) : remote.substring(remote.lastIndexOf('/') + 1);
    }

    @NonNull static String safeName(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static String baselineKey(SyncPair p) {
        return p.mode + "|" + p.localPath + "|" + p.remotePath + "|" + p.includeExtensions + "|"
                + p.excludeExtensions + "|" + p.minSizeKb + "|" + p.maxSizeKb + "|" + p.customRules + "|" + p.skipHidden;
    }

    /** Copies the shared settings (everything except name and folders) from the form into {@code p}. */
    private void applyForm(SyncPair p, long minKb, long maxKb) {
        p.mode = selectedMode();
        p.mirrorDeletions = b.mirrorDeletions.isChecked();
        int c = b.conflictGroup.getCheckedRadioButtonId();
        p.conflictPolicy = c == R.id.conflictReplace ? ConflictPolicy.REPLACE
                : c == R.id.conflictBoth ? ConflictPolicy.KEEP_BOTH : ConflictPolicy.KEEP_NEWEST;
        p.keepLocalBackups = b.keepBackups.isChecked();
        p.includeExtensions = text(b.includeExt);
        p.excludeExtensions = text(b.excludeExt);
        p.minSizeKb = minKb;
        p.maxSizeKb = maxKb;
        p.skipHidden = b.skipHidden.isChecked();
        p.customRules = b.customRules.getText() == null ? "" : b.customRules.getText().toString();
        p.scheduleType = selectedSchedule();
        p.intervalMinutes = selectedInterval();
        p.wifiOnly = b.wifiOnly.isChecked();
        p.chargingOnly = b.chargingOnly.isChecked();
        p.enabled = b.enabled.isChecked();
    }

    private void save() {
        b.nameLayout.setError(null);
        b.minSizeLayout.setError(null);
        boolean multi = isMulti();
        String name = text(b.name);
        if (!multi && name.isEmpty()) {
            b.nameLayout.setError(getString(R.string.error_name_required));
            return;
        }
        if (remotePaths.isEmpty()) {
            Toast.makeText(this, R.string.error_remote_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (localPath == null) {
            Toast.makeText(this, multi ? R.string.error_destination_required : R.string.error_local_required,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        long min = number(b.minSize), max = number(b.maxSize);
        if (min > 0 && max > 0 && min >= max) {
            b.minSizeLayout.setError(getString(R.string.error_size_range));
            return;
        }

        // Build the pair(s) to save.
        List<SyncPair> toSave = new ArrayList<>();
        if (!multi) {
            pair.name = name;
            pair.localPath = SyncCommandBuilder.localPath(localPath);
            pair.remotePath = remotePaths.get(0);
            applyForm(pair, min, max);
            // Changing what is synced makes bisync's saved listings meaningless.
            if (!baselineKey(pair).equals(baselineKey)) pair.bisyncInitialized = false;
            toSave.add(pair);
        } else {
            String dest = SyncCommandBuilder.localPath(localPath);
            Set<String> used = new HashSet<>();
            for (String remote : remotePaths) {
                String leaf = leafName(remote);
                // Two Drive folders with the same name (in different parents) get distinct sub-folders.
                String folder = safeName(leaf);
                for (int i = 2; !used.add(folder.toLowerCase(Locale.ROOT)); i++) folder = safeName(leaf) + " (" + i + ")";
                SyncPair p = new SyncPair();
                p.name = folder;
                p.localPath = dest + "/" + folder;
                p.remotePath = remote;
                applyForm(p, min, max);
                toSave.add(p);
            }
        }

        for (SyncPair p : toSave) {
            if (p.mode == SyncMode.TWO_WAY
                    && SyncCommandBuilder.bisyncSessionNameLength(p) > SyncCommandBuilder.MAX_BISYNC_SESSION_NAME) {
                Toast.makeText(this, R.string.error_path_too_long, Toast.LENGTH_LONG).show();
                return;
            }
        }

        b.btnSave.setEnabled(false);
        SyncScheduler.runInBackground(() -> {
            List<SyncPair> existing = dao.getAll();
            for (SyncPair p : toSave) {
                for (SyncPair other : existing) {
                    if (other.id == p.id) continue;
                    if (StoragePaths.isInside(p.localPath, other.localPath)
                            || StoragePaths.isInside(other.localPath, p.localPath)) {
                        runOnUiThread(() -> {
                            b.btnSave.setEnabled(true);
                            Toast.makeText(this, getString(R.string.error_overlap, other.name), Toast.LENGTH_LONG).show();
                        });
                        return;
                    }
                }
            }
            boolean connected = new DriveRemote(this).isConfigured();
            for (SyncPair p : toSave) {
                boolean created = isNew;
                if (created) p.id = dao.insert(p);
                else dao.update(p);
                SyncScheduler.schedule(getApplicationContext(), p);
                // First sync fetches the Drive folder into the device folder right away.
                if (created && p.enabled && connected) SyncScheduler.syncNow(getApplicationContext(), p.id, true);
            }
            SyncScheduler.refreshWatcher(getApplicationContext(), dao.getAll());
            runOnUiThread(this::finish);
        });
    }
}
