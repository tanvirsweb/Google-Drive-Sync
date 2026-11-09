package com.tanvir.googlesync.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tanvir.googlesync.BuildConfig;
import com.tanvir.googlesync.R;
import com.tanvir.googlesync.data.AppDatabase;
import com.tanvir.googlesync.data.SyncMode;
import com.tanvir.googlesync.data.SyncPair;
import com.tanvir.googlesync.data.SyncPairDao;
import com.tanvir.googlesync.databinding.ActivityMainBinding;
import com.tanvir.googlesync.databinding.DialogLogBinding;
import com.tanvir.googlesync.rclone.DriveRemote;
import com.tanvir.googlesync.rclone.RcloneLog;
import com.tanvir.googlesync.sync.SyncEngine;
import com.tanvir.googlesync.sync.SyncScheduler;
import com.tanvir.googlesync.util.AppSettings;
import com.tanvir.googlesync.util.NetworkUtils;
import com.tanvir.googlesync.util.StoragePaths;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements PairAdapter.Listener {

    private ActivityMainBinding b;
    private PairAdapter adapter;
    private SyncPairDao dao;
    private DriveRemote drive;
    private AppSettings settings;
    private List<SyncPair> pairs = new ArrayList<>();

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});
    private final ActivityResultLauncher<String[]> legacyStoragePermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> refreshCards());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        dao = AppDatabase.get(this).pairs();
        drive = new DriveRemote(this);
        settings = new AppSettings(this);

        b.toolbar.inflateMenu(R.menu.menu_main);
        b.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_sync_all) {
                syncAll();
            } else if (id == R.id.action_account) {
                startActivity(new Intent(this, AccountActivity.class));
            } else if (id == R.id.action_about) {
                showAbout();
            }
            return true;
        });

        adapter = new PairAdapter(this);
        b.rvPairs.setLayoutManager(new LinearLayoutManager(this));
        b.rvPairs.setAdapter(adapter);
        b.rvPairs.setItemAnimator(null); // progress updates would otherwise flash

        dao.observeAll().observe(this, list -> {
            pairs = list;
            adapter.submitList(new ArrayList<>(list));
            b.emptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
        new NetworkUtils.OnlineLiveData(this).observe(this, online ->
                b.offlineBanner.setVisibility(Boolean.TRUE.equals(online) ? View.GONE : View.VISIBLE));

        b.fabAdd.setOnClickListener(v -> startActivity(new Intent(this, PairEditorActivity.class)));
        b.btnAccount.setOnClickListener(v -> startActivity(new Intent(this, AccountActivity.class)));
        b.accountCard.setOnClickListener(v -> startActivity(new Intent(this, AccountActivity.class)));
        b.btnGrantStorage.setOnClickListener(v -> requestStorageAccess());
        b.swipeRefresh.setColorSchemeColors(getColor(R.color.brand_green));
        b.swipeRefresh.setOnRefreshListener(() -> {
            syncAll();
            b.swipeRefresh.setRefreshing(false);
        });

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        // Make sure schedules and the folder watcher match the stored pairs.
        SyncScheduler.runInBackground(() -> SyncScheduler.rescheduleAll(getApplicationContext()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCards();
    }

    private void refreshCards() {
        b.rcloneMissing.setVisibility(drive.rclone().isAvailable() ? View.GONE : View.VISIBLE);
        b.permissionCard.setVisibility(StoragePaths.hasStorageAccess(this) ? View.GONE : View.VISIBLE);

        boolean connected = drive.isConfigured();
        if (connected) {
            String email = settings.accountEmail();
            b.accountTitle.setText(R.string.account_connected);
            b.accountSubtitle.setText(email != null ? email : getString(R.string.signed_in));
            b.btnAccount.setText(R.string.manage);
        } else {
            b.accountTitle.setText(R.string.account_not_connected);
            b.accountSubtitle.setText(R.string.account_not_connected_text);
            b.btnAccount.setText(R.string.connect);
        }
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            legacyStoragePermission.launch(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE});
        }
    }

    private void syncAll() {
        if (!drive.isConfigured()) {
            startActivity(new Intent(this, AccountActivity.class));
            return;
        }
        for (SyncPair p : pairs) if (p.enabled) SyncScheduler.syncNow(this, p.id, true);
    }

    // ---- PairAdapter.Listener ----

    @Override
    public void onOpen(SyncPair pair) {
        startActivity(new Intent(this, PairEditorActivity.class).putExtra(PairEditorActivity.EXTRA_ID, pair.id));
    }

    @Override
    public void onSync(SyncPair pair) {
        if (!drive.isConfigured()) {
            startActivity(new Intent(this, AccountActivity.class));
            return;
        }
        if (!StoragePaths.hasStorageAccess(this)) {
            requestStorageAccess();
            return;
        }
        SyncScheduler.syncNow(this, pair.id, true);
    }

    @Override
    public void onStop(SyncPair pair) {
        SyncScheduler.stop(this, pair.id);
    }

    @Override
    public void onMore(SyncPair pair, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.inflate(R.menu.menu_pair);
        menu.getMenu().findItem(R.id.action_reset).setVisible(pair.mode == SyncMode.TWO_WAY);
        menu.getMenu().findItem(R.id.action_backups).setVisible(pair.mode != SyncMode.UPLOAD);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_edit) onOpen(pair);
            else if (id == R.id.action_log) showLog(pair);
            else if (id == R.id.action_backups) showBackups(pair);
            else if (id == R.id.action_reset) confirmReset(pair);
            else if (id == R.id.action_delete) confirmDelete(pair);
            return true;
        });
        menu.show();
    }

    private void showLog(SyncPair pair) {
        DialogLogBinding lb = DialogLogBinding.inflate(LayoutInflater.from(this));
        File f = SyncEngine.logFile(this, pair.id);
        String text;
        try {
            text = f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)
                    : getString(R.string.no_log);
        } catch (Exception e) {
            text = e.toString();
        }
        lb.logText.setText(text);
        lb.scroll.post(() -> lb.scroll.fullScroll(View.FOCUS_DOWN));
        String finalText = text;
        new MaterialAlertDialogBuilder(this)
                .setTitle(pair.name)
                .setView(lb.getRoot())
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(android.R.string.copy, (d, w) -> copy(finalText))
                .show();
    }

    private void showBackups(SyncPair pair) {
        File root = SyncEngine.backupRoot(this, pair);
        SyncScheduler.runInBackground(() -> {
            long size = sizeOf(root);
            int runs = root.list() == null ? 0 : root.list().length;
            runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.open_backups)
                    .setMessage(getString(R.string.keep_backups_desc) + "\n\n" + root.getAbsolutePath()
                            + "\n\n" + runs + " backup(s) · " + RcloneLog.formatBytes(size)
                            + "\nKept for " + settings.backupRetentionDays() + " days.")
                    .setPositiveButton(R.string.close, null)
                    .setNeutralButton(android.R.string.copy, (d, w) -> copy(root.getAbsolutePath()))
                    .show());
        });
    }

    private static long sizeOf(File f) {
        if (f.isFile()) return f.length();
        long total = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) total += sizeOf(k);
        return total;
    }

    private void confirmReset(SyncPair pair) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_two_way)
                .setMessage(R.string.reset_two_way_text)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (d, w) -> SyncScheduler.runInBackground(() ->
                        dao.setBisyncInitialized(pair.id, false)))
                .show();
    }

    private void confirmDelete(SyncPair pair) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.delete_pair_title, pair.name))
                .setMessage(R.string.delete_pair_text)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    SyncScheduler.cancel(this, pair.id);
                    SyncScheduler.runInBackground(() -> {
                        dao.delete(pair);
                        SyncScheduler.refreshWatcher(getApplicationContext(), dao.getAll());
                    });
                })
                .show();
    }

    private void copy(String text) {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text));
        Toast.makeText(this, android.R.string.copy, Toast.LENGTH_SHORT).show();
    }

    private void showAbout() {
        SyncScheduler.runInBackground(() -> {
            String version = "rclone";
            try {
                String out = drive.rclone().run("version").stdout;
                version = out.split("\n")[0].trim();
            } catch (Exception ignored) {
            }
            String v = version;
            runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(getString(R.string.about_text, BuildConfig.VERSION_NAME, v))
                    .setPositiveButton(R.string.ok, null)
                    .show());
        });
    }
}
