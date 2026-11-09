package com.tanvir.googlesync.rclone;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Runs the bundled rclone binary.
 *
 * The binary ships as {@code lib/<abi>/librclone.so}, which the package installer extracts into
 * {@code nativeLibraryDir} — the only location an app may execute files from on Android 10+.
 * All state (config with the OAuth token, bisync listings, cache) lives in app-private storage.
 */
public final class Rclone {

    /** Name of the Google Drive remote in rclone.conf. */
    public static final String REMOTE = "gdrive";

    private static final int STDERR_TAIL_LINES = 200;

    private final Context app;

    public Rclone(Context context) {
        this.app = context.getApplicationContext();
    }

    public File binary() {
        return new File(app.getApplicationInfo().nativeLibraryDir, "librclone.so");
    }

    public boolean isAvailable() {
        File f = binary();
        return f.exists() && f.canExecute();
    }

    public File configFile() {
        File dir = new File(app.getFilesDir(), "rclone");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "rclone.conf");
    }

    /** Working directory for rclone bisync state (listings, locks). */
    public File bisyncWorkDir() {
        File dir = new File(app.getFilesDir(), "bisync");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    /** Receives stderr lines while a command runs. */
    public interface LineListener {
        void onLine(@NonNull String line);
    }

    public static final class Result {
        public final int exitCode;
        @NonNull public final String stdout;
        /** The last lines of stderr. */
        @NonNull public final String stderrTail;
        public final boolean cancelled;

        Result(int exitCode, @NonNull String stdout, @NonNull String stderrTail, boolean cancelled) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderrTail = stderrTail;
            this.cancelled = cancelled;
        }

        public boolean ok() {
            return exitCode == 0 && !cancelled;
        }

        /** Best single-line description of a failure. */
        @NonNull public String errorSummary() {
            String[] lines = stderrTail.split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String l = lines[i].trim();
                if (l.contains("ERROR") || l.contains("Failed") || l.contains("error")) {
                    return RcloneLog.humanize(l);
                }
            }
            for (int i = lines.length - 1; i >= 0; i--) {
                if (!lines[i].trim().isEmpty()) return RcloneLog.humanize(lines[i].trim());
            }
            return "rclone exited with code " + exitCode;
        }
    }

    /** A started rclone process that can be stopped from another thread. */
    public static final class Handle {
        private volatile Process process;
        private volatile boolean cancelled;

        public void cancel() {
            cancelled = true;
            Process p = process;
            if (p != null) p.destroy();
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    public Result run(@NonNull List<String> args) throws IOException, InterruptedException {
        return run(args, null, null);
    }

    /**
     * Runs rclone synchronously. Blocks the calling thread; never call it on the main thread.
     */
    public Result run(@NonNull List<String> args, @Nullable LineListener stderrListener,
                      @Nullable Handle handle) throws IOException, InterruptedException {
        if (!isAvailable()) {
            throw new IOException("rclone binary is missing from this build (" + binary() + ")");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(binary().getAbsolutePath());
        cmd.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        Map<String, String> env = pb.environment();
        env.put("RCLONE_CONFIG", configFile().getAbsolutePath());
        env.put("HOME", app.getFilesDir().getAbsolutePath());
        env.put("TMPDIR", app.getCacheDir().getAbsolutePath());
        env.put("RCLONE_CACHE_DIR", new File(app.getCacheDir(), "rclone").getAbsolutePath());
        // Never prompt: a prompt would block the process forever.
        env.put("RCLONE_ASK_PASSWORD", "false");
        pb.directory(app.getFilesDir());

        Process process = pb.start();
        if (handle != null) {
            handle.process = process;
            if (handle.cancelled) process.destroy();
        }
        process.getOutputStream().close();

        Deque<String> tail = new ArrayDeque<>();
        Thread errThread = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    synchronized (tail) {
                        tail.addLast(line);
                        if (tail.size() > STDERR_TAIL_LINES) tail.removeFirst();
                    }
                    if (stderrListener != null) {
                        try {
                            stderrListener.onLine(line);
                        } catch (RuntimeException ignored) {
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }, "rclone-stderr");
        errThread.start();

        String stdout = readAll(process.getInputStream());
        int code = process.waitFor();
        errThread.join(5000);

        StringBuilder sb = new StringBuilder();
        synchronized (tail) {
            for (String l : tail) sb.append(l).append('\n');
        }
        boolean cancelled = handle != null && handle.cancelled;
        return new Result(code, stdout, sb.toString(), cancelled);
    }

    public Result run(String... args) throws IOException, InterruptedException {
        return run(Arrays.asList(args));
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }
}
