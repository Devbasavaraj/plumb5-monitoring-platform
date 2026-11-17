package com.plumb5.monitor.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.plumb5.monitor.MonitoringConfig;
import com.plumb5.monitor.worker.UploadScheduler;

public class P5BootCompleteReceiver extends BroadcastReceiver {

    private static final String TAG = "P5BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null) {
            Log.w(TAG, "Received null intent");
            return;
        }

        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        Log.d(TAG,
                "Device rebooted. Resuming Plumb5 monitoring tasks...");

        try {

            Context appContext =
                    context.getApplicationContext();

            SdkPreferences prefs =
                    new SdkPreferences(appContext);

            if (!prefs.isConfigured()) {

                Log.d(TAG,
                        "Plumb5 SDK is not configured. Nothing to resume.");

                return;
            }

            String serverUrl =
                    prefs.getServerUrl();

            if (serverUrl == null
                    || serverUrl.trim().isEmpty()) {

                Log.e(TAG,
                        "Saved serverUrl is empty. Cannot resume SDK.");

                return;
            }

            serverUrl =
                    serverUrl.trim();

            /*
             * Development/local servers are allowed to use HTTP.
             *
             * Production servers must use HTTPS.
             */
            boolean localDevelopmentServer =
                    isLocalDevelopmentServer(serverUrl);

            boolean enforceHttps =
                    !localDevelopmentServer;

            Log.d(TAG,
                    "Saved serverUrl = " + serverUrl);

            Log.d(TAG,
                    "Local development server = "
                            + localDevelopmentServer);

            Log.d(TAG,
                    "enforceHttps = " + enforceHttps);

            /*
             * Restore configuration saved by p5Init().
             */
            MonitoringConfig config =
                    new MonitoringConfig.Builder(
                            prefs.getAppId(),
                            serverUrl
                    )
                            .apiKey(
                                    prefs.getApiKey()
                            )
                            .debugLogging(
                                    prefs.isDebugLoggingEnabled()
                            )
                            .batchSize(
                                    prefs.getBatchSize()
                            )
                            .maxRetries(
                                    prefs.getMaxRetries()
                            )
                            .enforceHttps(
                                    enforceHttps
                            )
                            .locationTrackingEnabled(
                                    prefs.isLocationTrackingEnabled()
                            )
                            .build();

            Log.d(TAG,
                    "MonitoringConfig restored successfully.");

            /*
             * Trigger an upload check so events stored before
             * device restart can continue uploading.
             */
            UploadScheduler.scheduleUpload(
                    appContext,
                    config
            );

            // Play services drops geofences on reboot; register them again.
            com.plumb5.monitor.engage.P5GeofenceSync.restore(appContext);

            Log.d(TAG,
                    "Upload scheduler resumed successfully.");

        } catch (Exception e) {

            /*
             * Never allow a boot receiver exception to crash
             * the entire application process.
             */
            Log.e(TAG,
                    "Failed to restore Plumb5 monitoring after boot.",
                    e);
        }
    }

    /**
     * Returns true for local development/emulator servers.
     *
     * These servers are intentionally allowed to use HTTP.
     */
    private static boolean isLocalDevelopmentServer(
            String serverUrl) {

        if (serverUrl == null) {
            return false;
        }

        String url =
                serverUrl.trim().toLowerCase();

        return url.startsWith("http://10.0.2.2")
                || url.startsWith("http://localhost")
                || url.startsWith("http://127.0.0.1")
                || url.startsWith("http://192.168.");
    }
}