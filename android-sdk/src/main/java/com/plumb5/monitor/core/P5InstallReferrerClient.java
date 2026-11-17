package com.plumb5.monitor.core;

import android.content.Context;
import android.util.Log;

import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;
import com.plumb5.monitor.Plumb5Monitor;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles capturing the Google Play Install Referrer for acquisition tracking.
 */
public class P5InstallReferrerClient {

    private static final String TAG = "P5Referrer";
    private final InstallReferrerClient referrerClient;

    public P5InstallReferrerClient(Context context) {
        this.referrerClient = InstallReferrerClient.newBuilder(context).build();
    }

    public void startConnection() {
        referrerClient.startConnection(new InstallReferrerStateListener() {
            @Override
            public void onInstallReferrerSetupFinished(int responseCode) {
                switch (responseCode) {
                    case InstallReferrerClient.InstallReferrerResponse.OK:
                        try {
                            ReferrerDetails details = referrerClient.getInstallReferrer();
                            handleReferrer(details);
                            referrerClient.endConnection();
                        } catch (Exception e) {
                            SdkLogger.error("Failed to get install referrer details", e);
                        }
                        break;
                    case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
                        SdkLogger.debug("Install Referrer not supported on this device");
                        break;
                    case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE:
                        SdkLogger.debug("Install Referrer service unavailable");
                        break;
                }
            }

            @Override
            public void onInstallReferrerServiceDisconnected() {
                // Try to restart connection later if needed
            }
        });
    }

    private void handleReferrer(ReferrerDetails details) {
        String referrerUrl = details.getInstallReferrer();
        long installTimestamp = details.getReferrerClickTimestampSeconds();

        SdkLogger.debug("Captured Install Referrer: " + referrerUrl);

        Map<String, String> props = new HashMap<>();
        props.put("referrer_url", referrerUrl);
        props.put("click_timestamp", String.valueOf(installTimestamp));

        // Track the install event
        Plumb5Monitor.getInstance().trackEvent("APP_INSTALL", null, props);
    }
}
