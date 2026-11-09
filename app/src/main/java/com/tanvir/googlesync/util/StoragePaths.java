package com.tanvir.googlesync.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;

/**
 * rclone works on real filesystem paths, so the app needs broad storage access
 * (All files access on Android 11+, legacy storage permissions before that).
 */
public final class StoragePaths {
    private StoragePaths() {}

    public static boolean hasStorageAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull public static File defaultSyncRoot() {
        return new File(Environment.getExternalStorageDirectory(), "GoogleDriveSync");
    }

    /**
     * Converts a SAF tree URI from the system folder picker into a filesystem path.
     * Returns null for providers that are not backed by a plain path (e.g. cloud providers).
     */
    @Nullable public static String treeUriToPath(@NonNull Uri uri) {
        if (!"com.android.externalstorage.documents".equals(uri.getAuthority())) return null;
        String docId;
        try {
            docId = DocumentsContract.getTreeDocumentId(uri);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int colon = docId.indexOf(':');
        if (colon < 0) return null;
        String volume = docId.substring(0, colon);
        String rel = docId.substring(colon + 1);
        String base = "primary".equalsIgnoreCase(volume)
                ? Environment.getExternalStorageDirectory().getAbsolutePath()
                : "/storage/" + volume;
        return rel.isEmpty() ? base : base + "/" + rel;
    }

    /** "/storage/emulated/0/DCIM/Camera" → "Internal storage/DCIM/Camera". */
    @NonNull public static String pretty(@NonNull String path) {
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (path.equals(root)) return "Internal storage";
        if (path.startsWith(root + "/")) return "Internal storage/" + path.substring(root.length() + 1);
        return path;
    }

    /** True if {@code child} is {@code parent} or lies inside it. */
    public static boolean isInside(@NonNull String child, @NonNull String parent) {
        String c = new File(child).getAbsolutePath();
        String p = new File(parent).getAbsolutePath();
        return c.equals(p) || c.startsWith(p.endsWith("/") ? p : p + "/");
    }
}
