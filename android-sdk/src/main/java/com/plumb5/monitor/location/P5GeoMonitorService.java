package com.plumb5.monitor.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.plumb5.monitor.Plumb5Monitor;
import com.plumb5.monitor.core.SdkLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service (Receiver) to handle geofence transitions.
 */
public class P5GeoMonitorService extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // A geofence transition can start a fresh process where the SDK was
        // never initialised; restore it from the saved config instead of crashing.
        com.plumb5.monitor.engage.P5Engage.ensureInitialized(context);
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent == null || geofencingEvent.hasError()) {
            SdkLogger.error("GeofencingEvent error: " + (geofencingEvent != null ? geofencingEvent.getErrorCode() : "null"));
            return;
        }

        int geofenceTransition = geofencingEvent.getGeofenceTransition();

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
                geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();
            if (triggeringGeofences != null) {
                for (Geofence geofence : triggeringGeofences) {
                    String requestId = geofence.getRequestId();
                    String transitionString = geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ? "ENTER" : "EXIT";

                    SdkLogger.debug("Geofence transition: " + requestId + " - " + transitionString);

                    Map<String, String> props = new HashMap<>();
                    props.put("geofence_id", requestId);
                    props.put("transition", transitionString);

                    try {
                        Plumb5Monitor.getInstance().trackEvent("GEOFENCE_" + transitionString, null, props);
                    } catch (IllegalStateException e) {
                        SdkLogger.error("Geofence event dropped: SDK not initialised", e);
                    }
                }
            }
        }
    }
}
