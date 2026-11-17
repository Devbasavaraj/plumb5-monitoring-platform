package com.plumb5.monitor.location;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.plumb5.monitor.core.SdkLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * Geofencing controller logic (v1.4 Feature).
 */
public class P5GeofenceController {

    private final Context context;
    private final GeofencingClient geofencingClient;
    private PendingIntent geofencePendingIntent;

    public P5GeofenceController(Context context) {
        this.context = context.getApplicationContext();
        this.geofencingClient = LocationServices.getGeofencingClient(this.context);
    }

    @SuppressLint("MissingPermission")
    public void addGeofence(String id, double lat, double lon, float radius) {
        List<Geofence> geofenceList = new ArrayList<>();
        geofenceList.add(new Geofence.Builder()
                .setRequestId(id)
                .setCircularRegion(lat, lon, radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build());

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofenceList)
                .build();

        geofencingClient.addGeofences(request, getGeofencePendingIntent())
                .addOnSuccessListener(aVoid -> SdkLogger.debug("Geofence " + id + " added successfully"))
                .addOnFailureListener(e -> SdkLogger.error("Failed to add geofence " + id, e));
    }

    private PendingIntent getGeofencePendingIntent() {
        if (geofencePendingIntent != null) {
            return geofencePendingIntent;
        }
        Intent intent = new Intent(context, P5GeoMonitorService.class);
        geofencePendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        return geofencePendingIntent;
    }
}
