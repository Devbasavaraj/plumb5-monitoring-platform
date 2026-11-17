package com.plumb5.monitor.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;

/**
 * Monitors network connectivity to optimize data usage.
 */
public class P5ConnectionDetector {

    public interface OnNetworkRestoredListener {
        void onNetworkRestored();
    }

    private final ConnectivityManager connectivityManager;
    private OnNetworkRestoredListener listener;
    private boolean isWifi = false;
    private boolean isMetered = true;
    private boolean wasOffline = true;

    public P5ConnectionDetector(@NonNull Context context) {
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        registerNetworkCallback();
    }

    public void setOnNetworkRestoredListener(OnNetworkRestoredListener listener) {
        this.listener = listener;
    }

    private void registerNetworkCallback() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        connectivityManager.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                boolean isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

                isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

                if (isConnected && wasOffline) {
                    wasOffline = false;
                    if (listener != null) {
                        listener.onNetworkRestored();
                    }
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                isWifi = false;
                isMetered = true;
                wasOffline = true;
            }
        });
    }

    public boolean isWifi() {
        return isWifi;
    }

    public boolean isMetered() {
        return isMetered;
    }

    public boolean canPerformImmediateUpload() {
        // Only allow immediate upload on WiFi to save user data plan.
        return isWifi && !isMetered;
    }
}
