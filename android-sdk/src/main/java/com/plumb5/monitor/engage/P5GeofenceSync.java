package com.plumb5.monitor.engage;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.plumb5.monitor.core.SdkLogger;
import com.plumb5.monitor.core.SdkPreferences;
import com.plumb5.monitor.location.P5GeofenceController;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Keeps the last geofence list the server sent, so geofences can be
 * registered again at app start and after a device reboot. Before this the
 * list was only registered by the 15-minute config sync, and Google Play
 * services forgets all geofences on reboot.
 */
public final class P5GeofenceSync {

    private static final String TAG = "P5GeofenceSync";

    private P5GeofenceSync() {
    }

    /** Called by ConfigSyncWorker with the raw "geofences" array. */
    public static void save(@NonNull Context context, @NonNull JSONArray geofences) {
        new P5EngagePrefs(context).putString(P5EngagePrefs.KEY_GEOFENCES_JSON, geofences.toString());
    }

    /** Registers the saved geofences again. Safe to call any time. */
    public static void restore(@NonNull Context context) {
        final Context app = context.getApplicationContext();
        P5Engage.io().execute(() -> {
            try {
                SdkPreferences prefs = new SdkPreferences(app);
                if (!prefs.isConfigured() || !prefs.isEnabled() || !prefs.isGeofenceEnabled()) {
                    return;
                }
                if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    SdkLogger.debug(TAG + ": location permission missing; geofences not restored");
                    return;
                }
                String json = new P5EngagePrefs(app).getString(P5EngagePrefs.KEY_GEOFENCES_JSON);
                if (json == null) {
                    return;
                }
                JSONArray list = new JSONArray(json);
                P5GeofenceController controller = new P5GeofenceController(app);
                int count = 0;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject g = list.optJSONObject(i);
                    if (g == null) {
                        continue;
                    }
                    String id = g.optString("id", "").trim();
                    double lat = g.optDouble("lat", Double.NaN);
                    double lon = g.optDouble("lon", Double.NaN);
                    double radius = g.optDouble("radius", Double.NaN);
                    if (id.isEmpty() || Double.isNaN(lat) || Double.isNaN(lon) || Double.isNaN(radius)
                            || lat < -90 || lat > 90 || lon < -180 || lon > 180 || radius <= 0) {
                        continue;
                    }
                    controller.addGeofence(id, lat, lon, (float) radius);
                    count++;
                }
                SdkLogger.debug(TAG + ": restored " + count + " geofences");
            } catch (JSONException | SecurityException e) {
                SdkLogger.error(TAG + ": restore failed", e);
            }
        });
    }
}
