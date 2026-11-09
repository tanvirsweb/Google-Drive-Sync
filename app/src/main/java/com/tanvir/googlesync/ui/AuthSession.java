package com.tanvir.googlesync.ui;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tanvir.googlesync.rclone.DriveRemote;
import com.tanvir.googlesync.rclone.RcloneAuthorizer;
import com.tanvir.googlesync.sync.SyncScheduler;
import com.tanvir.googlesync.util.AppSettings;

/** Holds the sign-in flow across activity recreation (the user leaves for the browser). */
final class AuthSession {

    enum Phase { IDLE, STARTING, WAITING_FOR_BROWSER, DONE, ERROR }

    static final class State {
        @NonNull final Phase phase;
        @Nullable final String url;
        @Nullable final String error;

        State(@NonNull Phase phase, @Nullable String url, @Nullable String error) {
            this.phase = phase;
            this.url = url;
            this.error = error;
        }
    }

    private static final MutableLiveData<State> STATE = new MutableLiveData<>(new State(Phase.IDLE, null, null));
    @Nullable private static RcloneAuthorizer current;

    private AuthSession() {}

    static LiveData<State> state() {
        return STATE;
    }

    static void start(Context ctx, String clientId, String clientSecret) {
        Context app = ctx.getApplicationContext();
        if (current != null && current.isRunning()) return;
        DriveRemote drive = new DriveRemote(app);
        current = new RcloneAuthorizer(drive.rclone());
        STATE.setValue(new State(Phase.STARTING, null, null));
        current.start(clientId, clientSecret, new RcloneAuthorizer.Callback() {
            @Override public void onAuthUrl(@NonNull String url) {
                STATE.setValue(new State(Phase.WAITING_FOR_BROWSER, url, null));
            }

            @Override public void onToken(@NonNull String tokenJson) {
                SyncScheduler.runInBackground(() -> {
                    try {
                        drive.saveRemote(tokenJson, clientId, clientSecret);
                        DriveRemote.AccountInfo info = drive.accountInfo();
                        new AppSettings(app).setAccountEmail(info.email);
                        STATE.postValue(new State(Phase.DONE, null, null));
                        SyncScheduler.rescheduleAll(app);
                    } catch (Exception e) {
                        STATE.postValue(new State(Phase.ERROR, null, String.valueOf(e.getMessage())));
                    }
                });
            }

            @Override public void onError(@NonNull String message) {
                STATE.setValue(new State(Phase.ERROR, null, message));
            }
        });
    }

    static void cancel() {
        if (current != null) current.cancel();
    }

    static void reset() {
        STATE.setValue(new State(Phase.IDLE, null, null));
    }
}
