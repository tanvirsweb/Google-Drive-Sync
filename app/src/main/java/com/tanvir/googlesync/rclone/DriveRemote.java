package com.tanvir.googlesync.rclone;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Manages the "gdrive" remote in rclone.conf and wraps the Drive queries the UI needs. */
public final class DriveRemote {

    private final Rclone rclone;

    public DriveRemote(Context context) {
        this.rclone = new Rclone(context);
    }

    public Rclone rclone() {
        return rclone;
    }

    public boolean isConfigured() {
        File f = rclone.configFile();
        if (!f.exists()) return false;
        try {
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            return s.contains("[" + Rclone.REMOTE + "]") && s.contains("token");
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Writes the remote section. rclone refreshes the token in place afterwards.
     * Blank client id/secret means rclone's built-in client is used.
     */
    public void saveRemote(@NonNull String tokenJson, @Nullable String clientId,
                           @Nullable String clientSecret) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(Rclone.REMOTE).append("]\n");
        sb.append("type = drive\n");
        if (clientId != null && !clientId.trim().isEmpty()) {
            sb.append("client_id = ").append(clientId.trim()).append('\n');
            if (clientSecret != null && !clientSecret.trim().isEmpty()) {
                sb.append("client_secret = ").append(clientSecret.trim()).append('\n');
            }
        }
        sb.append("scope = drive\n");
        sb.append("token = ").append(tokenJson.trim().replace("\n", "")).append('\n');

        File f = rclone.configFile();
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            w.write(sb.toString());
        }
        if (!tmp.renameTo(f)) throw new IOException("Could not write " + f);
    }

    public void deleteRemote() {
        //noinspection ResultOfMethodCallIgnored
        rclone.configFile().delete();
    }

    public static final class DriveEntry {
        @NonNull public final String name;
        @NonNull public final String path;
        public final boolean isDir;

        DriveEntry(@NonNull String name, @NonNull String path, boolean isDir) {
            this.name = name;
            this.path = path;
            this.isDir = isDir;
        }
    }

    /** Lists the sub-folders of {@code path} ("" = My Drive root). */
    public List<DriveEntry> listFolders(@NonNull String path) throws IOException, InterruptedException {
        Rclone.Result r = rclone.run("lsjson", remoteSpec(path), "--dirs-only", "--no-modtime",
                "--no-mimetype", "--contimeout", "20s", "--timeout", "60s", "--retries", "1");
        if (!r.ok()) throw new IOException(r.errorSummary());
        List<DriveEntry> out = new ArrayList<>();
        JsonArray arr = JsonParser.parseString(r.stdout.isEmpty() ? "[]" : r.stdout).getAsJsonArray();
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            String name = o.get("Name").getAsString();
            String child = path.isEmpty() ? name : path + "/" + name;
            out.add(new DriveEntry(name, child, true));
        }
        Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    public void mkdir(@NonNull String path) throws IOException, InterruptedException {
        Rclone.Result r = rclone.run("mkdir", remoteSpec(path));
        if (!r.ok()) throw new IOException(r.errorSummary());
    }

    public static final class AccountInfo {
        @Nullable public String email;
        public long used = -1;
        public long total = -1;
    }

    /** Fetches the account e-mail and storage quota. Fields stay unset if a call fails. */
    public AccountInfo accountInfo() throws InterruptedException {
        AccountInfo info = new AccountInfo();
        try {
            Rclone.Result r = rclone.run("config", "userinfo", Rclone.REMOTE + ":", "--json",
                    "--contimeout", "20s");
            if (r.ok()) {
                JsonObject o = JsonParser.parseString(r.stdout).getAsJsonObject();
                if (o.has("email")) info.email = o.get("email").getAsString();
            }
        } catch (IOException | RuntimeException ignored) {
        }
        try {
            Rclone.Result r = rclone.run("about", Rclone.REMOTE + ":", "--json", "--contimeout", "20s");
            if (r.ok()) {
                JsonObject o = JsonParser.parseString(r.stdout).getAsJsonObject();
                if (o.has("used")) info.used = o.get("used").getAsLong();
                if (o.has("total")) info.total = o.get("total").getAsLong();
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return info;
    }

    @NonNull public static String remoteSpec(@NonNull String path) {
        return Rclone.REMOTE + ":" + path;
    }
}
