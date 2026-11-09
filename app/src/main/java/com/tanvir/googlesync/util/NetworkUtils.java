package com.tanvir.googlesync.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

public final class NetworkUtils {
    private NetworkUtils() {}

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        if (cm == null) return false;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public static boolean isUnmetered(Context context) {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        return cm != null && !cm.isActiveNetworkMetered();
    }

    /** Emits true/false as the default network comes and goes. */
    public static final class OnlineLiveData extends LiveData<Boolean> {
        private final Context app;
        private final ConnectivityManager cm;
        private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(@NonNull Network network) { postValue(true); }
            @Override public void onLost(@NonNull Network network) { postValue(isOnline(app)); }
        };

        public OnlineLiveData(Context context) {
            app = context.getApplicationContext();
            cm = app.getSystemService(ConnectivityManager.class);
        }

        @Override protected void onActive() {
            setValue(isOnline(app));
            try {
                cm.registerDefaultNetworkCallback(callback);
            } catch (RuntimeException ignored) {
            }
        }

        @Override protected void onInactive() {
            try {
                cm.unregisterNetworkCallback(callback);
            } catch (RuntimeException ignored) {
            }
        }
    }
}
