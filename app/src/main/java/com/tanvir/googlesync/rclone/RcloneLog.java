package com.tanvir.googlesync.rclone;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;

/** Parses rclone's {@code --use-json-log} output lines. */
public final class RcloneLog {

    private RcloneLog() {}

    public static final class Entry {
        @NonNull public String level = "";
        @NonNull public String msg = "";
        @NonNull public String object = "";
        @Nullable public Stats stats;
    }

    public static final class Stats {
        public long bytes;
        public long totalBytes;
        public long transfers;
        public long totalTransfers;
        public long checks;
        public long deletes;
        public long errors;
        public double speed;
        public long eta = -1;

        public int percent() {
            if (totalBytes > 0) return (int) Math.min(100, bytes * 100 / totalBytes);
            if (totalTransfers > 0) return (int) Math.min(100, transfers * 100 / totalTransfers);
            return -1;
        }

        @NonNull public String describe() {
            StringBuilder sb = new StringBuilder();
            if (totalTransfers > 0) {
                sb.append(transfers).append('/').append(totalTransfers).append(" files");
            } else {
                sb.append(checks).append(" checked");
            }
            if (totalBytes > 0) {
                sb.append(" · ").append(formatBytes(bytes)).append(" of ").append(formatBytes(totalBytes));
            }
            if (speed > 1024) sb.append(" · ").append(formatBytes((long) speed)).append("/s");
            return sb.toString();
        }
    }

    /** Returns null for lines that are not JSON log entries. */
    @Nullable public static Entry parse(@NonNull String line) {
        String t = line.trim();
        if (!t.startsWith("{")) return null;
        try {
            JsonObject o = JsonParser.parseString(t).getAsJsonObject();
            Entry e = new Entry();
            e.level = str(o, "level");
            e.msg = str(o, "msg").trim();
            e.object = str(o, "object");
            if (o.has("stats") && o.get("stats").isJsonObject()) {
                JsonObject s = o.getAsJsonObject("stats");
                Stats st = new Stats();
                st.bytes = lng(s, "bytes");
                st.totalBytes = lng(s, "totalBytes");
                st.transfers = lng(s, "transfers");
                st.totalTransfers = lng(s, "totalTransfers");
                st.checks = lng(s, "checks");
                st.deletes = lng(s, "deletes");
                st.errors = lng(s, "errors");
                st.speed = s.has("speed") && !s.get("speed").isJsonNull() ? s.get("speed").getAsDouble() : 0;
                st.eta = s.has("eta") && !s.get("eta").isJsonNull() ? s.get("eta").getAsLong() : -1;
                e.stats = st;
            }
            return e;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Turns a raw stderr line (JSON or text) into something readable. */
    @NonNull public static String humanize(@NonNull String line) {
        Entry e = parse(line);
        if (e == null) return line;
        if (!e.object.isEmpty() && !e.msg.startsWith(e.object)) return e.object + ": " + e.msg;
        return e.msg;
    }

    /** True when the failure looks like a connectivity problem rather than a real error. */
    public static boolean looksLikeNetworkError(@NonNull String text) {
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("no such host") || t.contains("dial tcp") || t.contains("network is unreachable")
                || t.contains("i/o timeout") || t.contains("connection refused") || t.contains("connection reset")
                || t.contains("tls handshake timeout") || t.contains("temporary failure in name resolution")
                || t.contains("context deadline exceeded") || t.contains("couldn't connect")
                || t.contains("server misbehaving");
    }

    @NonNull public static String formatBytes(long b) {
        if (b < 1024) return b + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double v = b;
        int i = -1;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(Locale.US, v >= 100 ? "%.0f %s" : "%.1f %s", v, units[i]);
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    private static long lng(JsonObject o, String k) {
        JsonElement e = o.get(k);
        try {
            return e == null || e.isJsonNull() ? 0 : e.getAsLong();
        } catch (RuntimeException ex) {
            return 0;
        }
    }
}
