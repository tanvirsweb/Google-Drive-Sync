package com.tanvir.googlesync.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tanvir.googlesync.R;
import com.tanvir.googlesync.databinding.ActivityAccountBinding;
import com.tanvir.googlesync.rclone.DriveRemote;
import com.tanvir.googlesync.rclone.RcloneLog;
import com.tanvir.googlesync.sync.SyncScheduler;
import com.tanvir.googlesync.util.AppSettings;

public class AccountActivity extends AppCompatActivity {

    private ActivityAccountBinding b;
    private DriveRemote drive;
    private AppSettings settings;
    private String lastUrl;
    private String openedUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAccountBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        b.toolbar.setNavigationOnClickListener(v -> finish());
        drive = new DriveRemote(this);
        settings = new AppSettings(this);

        b.clientId.setText(settings.clientId());
        b.clientSecret.setText(settings.clientSecret());
        b.retentionDays.setText(String.valueOf(settings.backupRetentionDays()));

        b.btnSignIn.setOnClickListener(v -> {
            if (!drive.rclone().isAvailable()) {
                Toast.makeText(this, R.string.rclone_missing, Toast.LENGTH_LONG).show();
                return;
            }
            String id = str(b.clientId.getText());
            String secret = str(b.clientSecret.getText());
            settings.setClient(id, secret);
            openedUrl = null;
            AuthSession.start(this, id, secret);
        });
        b.btnOpenAgain.setOnClickListener(v -> openBrowser(lastUrl));
        b.btnCancelSignIn.setOnClickListener(v -> AuthSession.cancel());
        b.btnDisconnect.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.disconnect)
                .setMessage(R.string.disconnect_text)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.disconnect, (d, w) -> {
                    drive.deleteRemote();
                    settings.setAccountEmail(null);
                    refresh();
                })
                .show());

        AuthSession.state().observe(this, this::onAuthState);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            int days = Integer.parseInt(str(b.retentionDays.getText()));
            if (days > 0) settings.setBackupRetentionDays(days);
        } catch (NumberFormatException ignored) {
        }
    }

    private void onAuthState(AuthSession.State s) {
        boolean busy = s.phase == AuthSession.Phase.STARTING || s.phase == AuthSession.Phase.WAITING_FOR_BROWSER;
        b.waitingSection.setVisibility(busy ? View.VISIBLE : View.GONE);
        b.btnSignIn.setEnabled(!busy);
        b.btnOpenAgain.setVisibility(s.url != null ? View.VISIBLE : View.GONE);
        if (s.phase == AuthSession.Phase.WAITING_FOR_BROWSER && s.url != null) {
            lastUrl = s.url;
            if (!s.url.equals(openedUrl)) openBrowser(s.url);
        } else if (s.phase == AuthSession.Phase.DONE) {
            AuthSession.reset();
            Toast.makeText(this, R.string.signed_in, Toast.LENGTH_SHORT).show();
            refresh();
        } else if (s.phase == AuthSession.Phase.ERROR) {
            AuthSession.reset();
            new MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.sign_in_failed, s.error))
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    private void openBrowser(String url) {
        if (url == null) return;
        openedUrl = url;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No browser installed", Toast.LENGTH_LONG).show();
        }
    }

    private void refresh() {
        boolean connected = drive.isConfigured();
        b.connectedCard.setVisibility(connected ? View.VISIBLE : View.GONE);
        b.signInSection.setVisibility(connected ? View.GONE : View.VISIBLE);
        if (!connected) return;
        String email = settings.accountEmail();
        b.email.setText(email != null ? email : getString(R.string.account_connected));
        SyncScheduler.runInBackground(() -> {
            try {
                DriveRemote.AccountInfo info = drive.accountInfo();
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (info.email != null) {
                        settings.setAccountEmail(info.email);
                        b.email.setText(info.email);
                    }
                    if (info.used >= 0 && info.total > 0) {
                        b.quota.setText(getString(R.string.storage_used,
                                RcloneLog.formatBytes(info.used), RcloneLog.formatBytes(info.total)));
                        b.quotaBar.setVisibility(View.VISIBLE);
                        b.quotaBar.setProgress((int) (info.used * 100 / info.total));
                    }
                });
            } catch (InterruptedException ignored) {
            }
        });
    }

    private static String str(CharSequence s) {
        return s == null ? "" : s.toString().trim();
    }
}
