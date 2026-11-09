package com.tanvir.googlesync.rclone;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google sign-in through {@code rclone authorize drive}.
 *
 * rclone starts a loopback web server on 127.0.0.1:53682 and prints an auth URL; the user opens
 * it in their browser, Google redirects back to the loopback server, and rclone prints the token.
 * This works with a user-supplied "Desktop app" OAuth client entered at runtime, or with rclone's
 * built-in client when none is given.
 */
public final class RcloneAuthorizer {

    public interface Callback {
        /** Open this URL in the browser. */
        void onAuthUrl(@NonNull String url);

        void onToken(@NonNull String tokenJson);

        void onError(@NonNull String message);
    }

    private static final Pattern URL = Pattern.compile("(http://127\\.0\\.0\\.1:\\d+/auth\\?\\S+)");
    private static final String BEGIN = "Paste the following into your remote machine --->";
    private static final String END = "<---End paste";

    private final Rclone rclone;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Rclone.Handle handle = new Rclone.Handle();
    private volatile boolean running;

    public RcloneAuthorizer(Rclone rclone) {
        this.rclone = rclone;
    }

    public boolean isRunning() {
        return running;
    }

    public void start(@Nullable String clientId, @Nullable String clientSecret, @NonNull Callback cb) {
        if (running) return;
        running = true;
        new Thread(() -> {
            List<String> args = new ArrayList<>();
            args.add("authorize");
            args.add("drive");
            if (clientId != null && !clientId.trim().isEmpty()) {
                args.add(clientId.trim());
                args.add(clientSecret == null ? "" : clientSecret.trim());
            }
            args.add("--auth-no-open-browser");
            final boolean[] urlSent = {false};
            try {
                Rclone.Result r = rclone.run(args, line -> {
                    Matcher m = URL.matcher(line);
                    if (!urlSent[0] && m.find()) {
                        urlSent[0] = true;
                        String url = m.group(1);
                        main.post(() -> cb.onAuthUrl(url));
                    }
                }, handle);
                if (r.cancelled) {
                    post(cb, null, "Sign-in cancelled");
                    return;
                }
                String token = extractToken(r.stdout);
                if (r.ok() && token != null) {
                    post(cb, token, null);
                } else {
                    post(cb, null, r.errorSummary());
                }
            } catch (Exception e) {
                post(cb, null, e.getMessage() == null ? e.toString() : e.getMessage());
            } finally {
                running = false;
            }
        }, "rclone-authorize").start();
    }

    public void cancel() {
        handle.cancel();
    }

    private void post(Callback cb, @Nullable String token, @Nullable String error) {
        main.post(() -> {
            if (token != null) cb.onToken(token);
            else cb.onError(error == null ? "Unknown error" : error);
        });
    }

    @Nullable static String extractToken(@NonNull String stdout) {
        int b = stdout.indexOf(BEGIN);
        int e = stdout.indexOf(END);
        if (b < 0 || e < 0 || e <= b) return null;
        String token = stdout.substring(b + BEGIN.length(), e).trim();
        return token.startsWith("{") ? token : null;
    }
}
