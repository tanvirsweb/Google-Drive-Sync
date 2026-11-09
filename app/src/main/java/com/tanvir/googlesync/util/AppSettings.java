package com.tanvir.googlesync.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** User-supplied runtime settings. Nothing here is baked in at build time. */
public final class AppSettings {
    private static final String PREFS = "settings";
    private static final String KEY_CLIENT_ID = "oauth_client_id";
    private static final String KEY_CLIENT_SECRET = "oauth_client_secret";
    private static final String KEY_EMAIL = "account_email";
    private static final String KEY_BACKUP_DAYS = "backup_retention_days";

    private final SharedPreferences prefs;

    public AppSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull public String clientId() { return prefs.getString(KEY_CLIENT_ID, ""); }
    @NonNull public String clientSecret() { return prefs.getString(KEY_CLIENT_SECRET, ""); }

    public void setClient(@NonNull String id, @NonNull String secret) {
        prefs.edit().putString(KEY_CLIENT_ID, id.trim()).putString(KEY_CLIENT_SECRET, secret.trim()).apply();
    }

    @Nullable public String accountEmail() { return prefs.getString(KEY_EMAIL, null); }
    public void setAccountEmail(@Nullable String email) { prefs.edit().putString(KEY_EMAIL, email).apply(); }

    /** How long local backup copies are kept. */
    public int backupRetentionDays() { return prefs.getInt(KEY_BACKUP_DAYS, 30); }
    public void setBackupRetentionDays(int days) { prefs.edit().putInt(KEY_BACKUP_DAYS, days).apply(); }
}
