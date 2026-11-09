package com.tanvir.googlesync.sync;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.tanvir.googlesync.data.AppDatabase;
import com.tanvir.googlesync.data.SyncPair;
import com.tanvir.googlesync.util.Notifications;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Foreground service backing "real-time" pairs: watches local folders and queues a sync a few
 * seconds after changes settle. If the device is offline the queued sync waits for the network,
 * and the change stays flagged as pending in the meantime.
 */
public class FolderWatcherService extends Service {

    public static final String ACTION_REFRESH = "com.tanvir.googlesync.WATCHER_REFRESH";
    private static final long DEBOUNCE_MS = 10_000;
    private static final int MAX_DIRS_PER_PAIR = 4000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Long, PairWatch> watches = new HashMap<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground(watches.size());
        SyncScheduler.runInBackground(() -> {
            List<SyncPair> pairs = AppDatabase.get(this).pairs().getAll();
            List<SyncPair> wanted = new ArrayList<>();
            for (SyncPair p : pairs) if (SyncScheduler.needsWatcher(p)) wanted.add(p);
            main.post(() -> apply(wanted));
        });
        return START_STICKY;
    }

    private void startInForeground(int count) {
        android.app.Notification n = Notifications.watcher(this, count).build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Notifications.ID_WATCHER, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(Notifications.ID_WATCHER, n);
        }
    }

    private void apply(List<SyncPair> wanted) {
        Map<Long, SyncPair> byId = new HashMap<>();
        for (SyncPair p : wanted) byId.put(p.id, p);
        for (Long id : new ArrayList<>(watches.keySet())) {
            PairWatch w = watches.get(id);
            SyncPair p = byId.get(id);
            if (p == null || !p.localPath.equals(w.root)) {
                w.stop();
                watches.remove(id);
            }
        }
        for (SyncPair p : wanted) {
            if (!watches.containsKey(p.id)) {
                PairWatch w = new PairWatch(p.id, p.localPath);
                w.start();
                watches.put(p.id, w);
            }
        }
        if (watches.isEmpty()) {
            stopSelf();
        } else {
            startInForeground(watches.size());
        }
    }

    @Override
    public void onDestroy() {
        for (PairWatch w : watches.values()) w.stop();
        watches.clear();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Recursive watch of one folder tree (FileObserver is per-directory). */
    private final class PairWatch {
        final long pairId;
        final String root;
        final Map<String, FileObserver> observers = new HashMap<>();
        final Runnable fire;

        PairWatch(long pairId, String root) {
            this.pairId = pairId;
            this.root = root;
            this.fire = () -> SyncScheduler.syncNow(FolderWatcherService.this, pairId, false);
        }

        void start() {
            ArrayDeque<File> queue = new ArrayDeque<>();
            queue.add(new File(root));
            while (!queue.isEmpty() && observers.size() < MAX_DIRS_PER_PAIR) {
                File dir = queue.poll();
                watchDir(dir);
                File[] kids = dir.listFiles(File::isDirectory);
                if (kids != null) for (File k : kids) queue.add(k);
            }
        }

        void stop() {
            main.removeCallbacks(fire);
            for (FileObserver o : observers.values()) o.stopWatching();
            observers.clear();
        }

        private static final int MASK = FileObserver.CLOSE_WRITE | FileObserver.CREATE | FileObserver.DELETE
                | FileObserver.MOVED_FROM | FileObserver.MOVED_TO | FileObserver.DELETE_SELF;

        @SuppressWarnings("deprecation")
        private void watchDir(File dir) {
            String path = dir.getAbsolutePath();
            if (observers.containsKey(path)) return;
            FileObserver o = new FileObserver(path, MASK) {
                @Override
                public void onEvent(int event, @Nullable String name) {
                    int ev = event & FileObserver.ALL_EVENTS;
                    if ((ev & FileObserver.DELETE_SELF) != 0) {
                        main.post(() -> {
                            FileObserver gone = observers.remove(path);
                            if (gone != null) gone.stopWatching();
                        });
                        return;
                    }
                    if (name == null || name.endsWith(".partial")) return;
                    if ((ev & (FileObserver.CREATE | FileObserver.MOVED_TO)) != 0) {
                        File child = new File(path, name);
                        if (child.isDirectory()) main.post(() -> watchDir(child));
                    }
                    onChange();
                }
            };
            o.startWatching();
            observers.put(path, o);
        }

        private void onChange() {
            // rclone itself writes into the folder during a sync; ignore our own changes.
            if (SyncEngine.isRunning(pairId)) return;
            main.post(() -> {
                main.removeCallbacks(fire);
                main.postDelayed(fire, DEBOUNCE_MS);
            });
            SyncScheduler.runInBackground(() ->
                    AppDatabase.get(FolderWatcherService.this).pairs().setPendingLocalChanges(pairId, true));
        }
    }
}
