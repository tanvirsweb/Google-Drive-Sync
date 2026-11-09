package com.tanvir.googlesync.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.tanvir.googlesync.R;
import com.tanvir.googlesync.data.SyncPair;
import com.tanvir.googlesync.data.SyncStatus;
import com.tanvir.googlesync.databinding.ItemPairBinding;
import com.tanvir.googlesync.util.StoragePaths;

import java.util.Objects;

public class PairAdapter extends ListAdapter<SyncPair, PairAdapter.VH> {

    public interface Listener {
        void onOpen(SyncPair pair);
        void onSync(SyncPair pair);
        void onStop(SyncPair pair);
        void onMore(SyncPair pair, View anchor);
    }

    private final Listener listener;

    public PairAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<SyncPair> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull SyncPair a, @NonNull SyncPair b) {
            return a.id == b.id;
        }

        @Override public boolean areContentsTheSame(@NonNull SyncPair a, @NonNull SyncPair b) {
            return a.name.equals(b.name) && a.localPath.equals(b.localPath) && a.remotePath.equals(b.remotePath)
                    && a.mode == b.mode && a.scheduleType == b.scheduleType && a.intervalMinutes == b.intervalMinutes
                    && a.wifiOnly == b.wifiOnly && a.enabled == b.enabled && a.status == b.status
                    && Objects.equals(a.statusMessage, b.statusMessage) && a.lastSuccessTime == b.lastSuccessTime
                    && a.pendingLocalChanges == b.pendingLocalChanges;
        }
    };

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemPairBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SyncPair p = getItem(position);
        Context c = h.b.getRoot().getContext();
        h.b.name.setText(p.name);
        h.b.localPath.setText(StoragePaths.pretty(p.localPath));
        h.b.remotePath.setText(p.remotePath.isEmpty()
                ? c.getString(R.string.my_drive) : c.getString(R.string.my_drive) + "/" + p.remotePath);
        h.b.modeLabel.setText(Labels.modeSummary(c, p));
        h.b.modeIcon.setImageResource(Labels.modeIcon(p.mode));

        boolean running = p.status == SyncStatus.RUNNING;
        h.b.progress.setVisibility(running ? View.VISIBLE : View.GONE);

        int icon;
        int tint;
        String title;
        switch (p.status) {
            case RUNNING:
                icon = R.drawable.ic_sync; tint = R.color.brand_green_text;
                title = c.getString(R.string.status_running);
                break;
            case QUEUED:
                icon = R.drawable.ic_schedule; tint = R.color.text_muted;
                title = c.getString(R.string.status_queued);
                break;
            case WAITING_FOR_NETWORK:
                icon = R.drawable.ic_cloud_off; tint = R.color.status_warn;
                title = c.getString(R.string.status_waiting);
                break;
            case FAILED:
                icon = R.drawable.ic_error; tint = R.color.status_error;
                title = c.getString(R.string.status_failed);
                break;
            case SUCCESS:
                icon = R.drawable.ic_check; tint = R.color.status_ok;
                title = c.getString(R.string.last_synced, DateUtils.getRelativeTimeSpanString(
                        p.lastSuccessTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
                break;
            case NEVER:
            default:
                icon = R.drawable.ic_schedule; tint = R.color.text_muted;
                title = c.getString(R.string.status_never);
                break;
        }
        if (!p.enabled) {
            title = c.getString(R.string.paused);
            tint = R.color.text_muted;
        }
        h.b.statusIcon.setImageResource(icon);
        h.b.statusIcon.setImageTintList(ColorStateList.valueOf(c.getColor(tint)));
        h.b.statusTitle.setText(title);

        String msg = p.status == SyncStatus.SUCCESS || p.status == SyncStatus.NEVER ? "" : p.statusMessage;
        if (p.status == SyncStatus.SUCCESS && p.statusMessage.startsWith("Synced")) msg = p.statusMessage;
        if (p.pendingLocalChanges && !running) {
            String pending = c.getString(R.string.pending_local_changes);
            msg = msg.isEmpty() ? pending : pending + " · " + msg;
        }
        h.b.statusMessage.setText(msg);
        h.b.statusMessage.setVisibility(msg.isEmpty() ? View.GONE : View.VISIBLE);

        h.b.btnSync.setText(running ? R.string.stop : R.string.sync_now);
        h.b.btnSync.setIconResource(running ? R.drawable.ic_stop : R.drawable.ic_sync);
        h.b.btnSync.setEnabled(p.enabled);
        h.b.btnSync.setOnClickListener(v -> {
            if (running) listener.onStop(p);
            else listener.onSync(p);
        });
        h.b.btnMore.setOnClickListener(v -> listener.onMore(p, v));
        h.b.getRoot().setOnClickListener(v -> listener.onOpen(p));
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ItemPairBinding b;

        VH(ItemPairBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

}
