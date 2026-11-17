
package com.plumb5.monitor.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.plumb5.monitor.core.SdkLogger;
import com.plumb5.monitor.core.SdkPreferences;
import com.plumb5.monitor.location.P5GeofenceController;
import com.plumb5.monitor.network.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

public class ConfigSyncWorker extends Worker {

    public ConfigSyncWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams
    ) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {

        Context context = getApplicationContext();
        SdkPreferences prefs = new SdkPreferences(context);

        if (!prefs.isConfigured()) {
            SdkLogger.error(
                    "ConfigSyncWorker: SDK is not configured"
            );
            return Result.failure();
        }

        String serverUrl = prefs.getServerUrl();
        String licenseServerUrl = prefs.getLicenseServerUrl();
        String apiKey = prefs.getApiKey();

        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            SdkLogger.error(
                    "ConfigSyncWorker: Server URL missing"
            );
            return Result.failure();
        }

        if (licenseServerUrl == null
                || licenseServerUrl.trim().isEmpty()) {
            SdkLogger.error(
                    "ConfigSyncWorker: License server URL missing"
            );
            return Result.failure();
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            SdkLogger.error(
                    "ConfigSyncWorker: API key missing"
            );
            return Result.failure();
        }

        try {

            ApiClient apiClient = new ApiClient(
                    context,
                    serverUrl,
                    apiKey,
                    licenseServerUrl
            );

            String configJson = apiClient.fetchRemoteConfig();

            if (configJson == null || configJson.trim().isEmpty()) {
                SdkLogger.debug(
                        "ConfigSyncWorker: Empty remote config"
                );
                return Result.success();
            }

            JSONObject json = new JSONObject(configJson);

            // =====================================================
            // GEOFENCES
            // =====================================================

            if (prefs.isGeofenceEnabled() && json.has("geofences")) {

                JSONArray geofences = json.optJSONArray("geofences");

                if (geofences != null) {
                    com.plumb5.monitor.engage.P5GeofenceSync.save(context, geofences);

                    P5GeofenceController controller =
                            new P5GeofenceController(context);

                    int validGeofenceCount = 0;

                    for (int i = 0; i < geofences.length(); i++) {

                        JSONObject geofence =
                                geofences.optJSONObject(i);

                        if (geofence == null) {
                            SdkLogger.error(
                                    "ConfigSyncWorker: Invalid geofence object at index " + i
                            );
                            continue;
                        }

                        String id = geofence
                                .optString("id", "")
                                .trim();

                        if (id.isEmpty()) {
                            SdkLogger.error(
                                    "ConfigSyncWorker: Geofence ID is missing at index " + i
                            );
                            continue;
                        }

                        double lat = geofence.optDouble(
                                "lat",
                                Double.NaN
                        );

                        double lon = geofence.optDouble(
                                "lon",
                                Double.NaN
                        );

                        double radiusValue = geofence.optDouble(
                                "radius",
                                Double.NaN
                        );

                        if (Double.isNaN(lat)
                                || Double.isInfinite(lat)
                                || lat < -90.0
                                || lat > 90.0
                                || Double.isNaN(lon)
                                || Double.isInfinite(lon)
                                || lon < -180.0
                                || lon > 180.0
                                || Double.isNaN(radiusValue)
                                || Double.isInfinite(radiusValue)
                                || radiusValue <= 0
                                || radiusValue > Float.MAX_VALUE) {

                            SdkLogger.error(
                                    "ConfigSyncWorker: Invalid geofence coordinates or radius for ID "
                                            + id
                            );
                            continue;
                        }

                        float radius = (float) radiusValue;

                        controller.addGeofence(
                                id,
                                lat,
                                lon,
                                radius
                        );

                        validGeofenceCount++;
                    }

                    SdkLogger.debug(
                            "ConfigSyncWorker: Submitted "
                                    + validGeofenceCount
                                    + " valid geofences for registration out of "
                                    + geofences.length()
                    );
                } else {
                    SdkLogger.error(
                            "ConfigSyncWorker: 'geofences' is not a JSON array"
                    );
                }
            }


            // =====================================================
            // BEACONS
            // =====================================================

            String beaconKey = json.has("beaconFilters")
                    ? "beaconFilters" : "beacons";
            if (json.has(beaconKey)) {

                JSONArray beacons = json.optJSONArray(beaconKey);

                if (beacons != null) {
                    prefs.saveBeaconFilters(beacons.toString());

                    SdkLogger.debug(
                            "ConfigSyncWorker: Saved "
                                    + beacons.length()
                                    + " beacon filters"
                    );
                } else {
                    SdkLogger.error(
                            "ConfigSyncWorker: 'beacons' is not a JSON array"
                    );
                }
            }

            // =====================================================
            // SDK SETTINGS
            // =====================================================

            JSONObject settings = json.optJSONObject("sdkSettings");

            if (settings != null) {

                int batchSize = settings.optInt(
                        "batchSize",
                        prefs.getBatchSize()
                );

                int syncIntervalMinutes = settings.optInt(
                        "syncIntervalMinutes",
                        prefs.getSyncIntervalMinutes()
                );

                prefs.saveRemoteSdkSettings(
                        batchSize,
                        syncIntervalMinutes
                );

                UploadScheduler.scheduleConfigSync(
                        context,
                        Math.max(
                                15,
                                Math.min(syncIntervalMinutes, 1440)
                        )
                );

                SdkLogger.debug(
                        "ConfigSyncWorker: Remote SDK settings saved"
                );
            }

            SdkLogger.debug(
                    "ConfigSyncWorker: Remote configuration sync completed"
            );

            return Result.success();

        } catch (Exception e) {

            SdkLogger.error(
                    "ConfigSyncWorker: Remote config sync failed",
                    e
            );

            return Result.retry();
        }
    }
}
