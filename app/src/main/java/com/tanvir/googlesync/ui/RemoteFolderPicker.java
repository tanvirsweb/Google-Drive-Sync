package com.tanvir.googlesync.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tanvir.googlesync.R;
import com.tanvir.googlesync.databinding.DialogRemotePickerBinding;
import com.tanvir.googlesync.databinding.DialogTextInputBinding;
import com.tanvir.googlesync.databinding.ItemRemoteFolderBinding;
import com.tanvir.googlesync.rclone.DriveRemote;
import com.tanvir.googlesync.sync.SyncCommandBuilder;
import com.tanvir.googlesync.sync.SyncScheduler;
import com.tanvir.googlesync.util.StoragePaths;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Dialog for browsing Google Drive folders through rclone lsjson.
 *
 * In multi-select mode each row has a checkbox; ticked folders are remembered while browsing, so
 * folders from different parents can be picked together. With nothing ticked, the folder being
 * viewed is returned, as in single-select mode.
 */
final class RemoteFolderPicker {

    interface Callback {
        void onPicked(@NonNull List<String> paths);
    }

    private final Activity activity;
    private final DriveRemote drive;
    private final Callback callback;
    private final boolean multiSelect;
    private final DialogRemotePickerBinding b;
    private final FolderAdapter adapter = new FolderAdapter();
    private final Set<String> selected = new LinkedHashSet<>();
    private String path;
    private int generation;
    @Nullable private AlertDialog dialog;

    private RemoteFolderPicker(Activity activity, String initial, List<String> preselected,
                               boolean multiSelect, Callback callback) {
        this.activity = activity;
        this.drive = new DriveRemote(activity);
        this.callback = callback;
        this.multiSelect = multiSelect;
        this.path = SyncCommandBuilder.remotePath(initial);
        if (multiSelect) selected.addAll(preselected);
        this.b = DialogRemotePickerBinding.inflate(LayoutInflater.from(activity));
    }

    static void show(Activity activity, String initial, Callback callback) {
        new RemoteFolderPicker(activity, initial, Collections.emptyList(), false, callback).show();
    }

    static void showMulti(Activity activity, String initial, List<String> preselected, Callback callback) {
        new RemoteFolderPicker(activity, initial, preselected, true, callback).show();
    }

    private void show() {
        b.list.setLayoutManager(new LinearLayoutManager(activity));
        b.list.setAdapter(adapter);
        b.btnUp.setOnClickListener(v -> {
            if (path.isEmpty()) return;
            int i = path.lastIndexOf('/');
            load(i < 0 ? "" : path.substring(0, i));
        });
        b.btnNewFolder.setOnClickListener(v -> askName(R.string.new_folder, R.string.folder_name, "", name -> {
            String clean = name.replace("/", "").trim();
            if (clean.isEmpty()) return;
            String target = path.isEmpty() ? clean : path + "/" + clean;
            setLoading(true);
            SyncScheduler.runInBackground(() -> {
                try {
                    drive.mkdir(target);
                    activity.runOnUiThread(() -> load(target));
                } catch (Exception e) {
                    activity.runOnUiThread(() -> showError(e.getMessage()));
                }
            });
        }));
        b.btnTypePath.setOnClickListener(v -> askName(R.string.type_path, R.string.drive_path_hint, path, typed ->
                load(SyncCommandBuilder.remotePath(typed))));
        b.btnClearSelection.setOnClickListener(v -> {
            selected.clear();
            adapter.refresh();
            updateSelectionUi();
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.choose_drive_folder)
                .setView(b.getRoot())
                .setNegativeButton(R.string.cancel, null)
                // Listener is replaced in onShow so a failed validation keeps the dialog open.
                .setPositiveButton(R.string.select_this_folder, null);
        dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            ok.setOnClickListener(v -> {
                if (finish()) dialog.dismiss();
            });
            updateSelectionUi();
        });
        dialog.show();
        load(path);
    }

    /** Returns false (and explains why) if the selection can't be used. */
    private boolean finish() {
        if (selected.isEmpty()) {
            callback.onPicked(Collections.singletonList(path));
            return true;
        }
        List<String> list = new ArrayList<>(selected);
        // A folder and one of its sub-folders would sync the same files twice.
        for (String a : list) {
            for (String c : list) {
                if (!a.equals(c) && (a.isEmpty() || StoragePaths.isInside("/" + c, "/" + a))) {
                    new MaterialAlertDialogBuilder(activity)
                            .setMessage(activity.getString(R.string.error_nested_selection, display(c), display(a)))
                            .setPositiveButton(R.string.ok, null)
                            .show();
                    return false;
                }
            }
        }
        callback.onPicked(list);
        return true;
    }

    private String display(String p) {
        return p.isEmpty() ? activity.getString(R.string.my_drive) : activity.getString(R.string.my_drive) + "/" + p;
    }

    private void updateSelectionUi() {
        if (!multiSelect) return;
        int n = selected.size();
        b.selectionBar.setVisibility(View.VISIBLE);
        b.btnClearSelection.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        if (n == 0) {
            b.selectionText.setText(R.string.multi_select_hint);
        } else {
            List<String> names = new ArrayList<>();
            for (String s : selected) names.add(s.substring(s.lastIndexOf('/') + 1));
            b.selectionText.setText(activity.getResources().getQuantityString(R.plurals.folders_selected, n, n)
                    + ": " + String.join(", ", names));
        }
        if (dialog != null) {
            Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (ok != null) {
                ok.setText(n == 0 ? activity.getString(R.string.select_this_folder)
                        : activity.getResources().getQuantityString(R.plurals.select_n_folders, n, n));
            }
        }
    }

    private void load(String p) {
        path = p;
        int gen = ++generation;
        b.currentPath.setText(display(p));
        b.btnUp.setEnabled(!p.isEmpty());
        b.btnUp.setAlpha(p.isEmpty() ? 0.4f : 1f);
        adapter.set(new ArrayList<>());
        setLoading(true);
        SyncScheduler.runInBackground(() -> {
            try {
                List<DriveRemote.DriveEntry> dirs = drive.listFolders(p);
                activity.runOnUiThread(() -> {
                    if (gen != generation) return;
                    setLoading(false);
                    adapter.set(dirs);
                    if (dirs.isEmpty()) {
                        b.empty.setText(R.string.no_subfolders);
                        b.empty.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (gen == generation) showError(e.getMessage());
                });
            }
        });
    }

    private void showError(String message) {
        setLoading(false);
        String m = message != null && message.contains("directory not found")
                ? activity.getString(R.string.no_subfolders)
                : activity.getString(R.string.drive_offline) + (message == null ? "" : "\n\n" + message);
        b.empty.setText(m);
        b.empty.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        b.loading.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) b.empty.setVisibility(View.GONE);
    }

    interface TextCallback {
        void onText(String text);
    }

    private void askName(int title, int hint, String initial, TextCallback cb) {
        DialogTextInputBinding ib = DialogTextInputBinding.inflate(LayoutInflater.from(activity));
        ib.inputLayout.setHint(activity.getString(hint));
        ib.input.setText(initial);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(ib.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    CharSequence t = ib.input.getText();
                    cb.onText(t == null ? "" : t.toString());
                })
                .show();
    }

    private final class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.VH> {
        private List<DriveRemote.DriveEntry> items = new ArrayList<>();

        @SuppressWarnings("NotifyDataSetChanged")
        void set(List<DriveRemote.DriveEntry> list) {
            items = list;
            notifyDataSetChanged();
        }

        @SuppressWarnings("NotifyDataSetChanged")
        void refresh() {
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemRemoteFolderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            DriveRemote.DriveEntry e = items.get(position);
            h.b.name.setText(e.name);
            h.b.getRoot().setOnClickListener(v -> load(e.path));
            h.b.check.setVisibility(multiSelect ? View.VISIBLE : View.GONE);
            h.b.check.setOnCheckedChangeListener(null);
            h.b.check.setChecked(selected.contains(e.path));
            h.b.check.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) selected.add(e.path);
                else selected.remove(e.path);
                updateSelectionUi();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            final ItemRemoteFolderBinding b;

            VH(ItemRemoteFolderBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
