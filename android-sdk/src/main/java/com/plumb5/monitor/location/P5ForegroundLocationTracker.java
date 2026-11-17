
package com.plumb5.monitor.location;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.plumb5.monitor.Plumb5Monitor;

import java.util.HashMap;
import java.util.Map;

public final class P5ForegroundLocationTracker {

    private static final String TAG = "P5LocationTracker";

    // Keep the existing tracking intervals.
    private static final long UPDATE_INTERVAL_MS = 5 * 60 * 1000L;
    private static final long MIN_UPDATE_INTERVAL_MS = 2 * 60 * 1000L;

    private final Context context;
    private final FusedLocationProviderClient locationClient;

    private boolean trackingRequested = false;

    private final LocationCallback locationCallback = new LocationCallback() {

        @Override
        public void onLocationAvailability(
                @NonNull LocationAvailability availability) {

            Log.d(TAG, "Location availability: "
                    + availability.isLocationAvailable());
        }

        @Override
        public void onLocationResult(@NonNull LocationResult result) {

            Log.d(TAG, "Location callback received. Count="
                    + result.getLocations().size());

            Location location = result.getLastLocation();

            if (location == null) {
                Log.w(TAG,
                        "Location callback returned no last location.");
                return;
            }

            long ageMs = System.currentTimeMillis() - location.getTime();

            Log.d(TAG, "Location fix received. Accuracy="
                    + location.getAccuracy()
                    + "m, ageMs=" + ageMs);

            Plumb5Monitor sdk = Plumb5Monitor.getInstance();

            if (sdk == null
                    || !sdk.isForegroundLocationTrackingAllowed()) {

                Log.w(TAG,
                        "SDK is no longer allowed to track location.");

                stop();
                return;
            }

            // Preserve the existing DEVICE_LOCATION event properties.
            Map<String, String> properties = new HashMap<>();

            properties.put(
                    "latitude",
                    String.valueOf(location.getLatitude()));

            properties.put(
                    "longitude",
                    String.valueOf(location.getLongitude()));

            properties.put(
                    "accuracy",
                    String.valueOf(location.getAccuracy()));

            properties.put(
                    "timestamp",
                    String.valueOf(location.getTime()));

            properties.put(
                    "provider",
                    location.getProvider() == null
                            ? "fused"
                            : location.getProvider());

            sdk.trackEvent("DEVICE_LOCATION", null, properties);

            Log.d(TAG,
                    "DEVICE_LOCATION submitted to SDK event pipeline.");
        }
    };

    public P5ForegroundLocationTracker(@NonNull Context context) {
        this.context = context.getApplicationContext();

        this.locationClient =
                LocationServices.getFusedLocationProviderClient(
                        this.context);
    }

    /**
     * Start continuous location updates when SDK state,
     * consent, and foreground permission allow tracking.
     */
    public synchronized void start() {

        Log.d(TAG,
                "start() called. trackingRequested=" + trackingRequested);

        if (trackingRequested) {
            Log.d(TAG, "Location updates are already requested.");
            return;
        }

        Plumb5Monitor sdk = Plumb5Monitor.getInstance();

        if (sdk == null) {
            Log.w(TAG, "Cannot start: SDK instance is null.");
            return;
        }

        boolean allowed = sdk.isForegroundLocationTrackingAllowed();

        Log.d(TAG, "Foreground location tracking allowed=" + allowed);

        if (!allowed) {
            Log.w(TAG,
                    "Cannot start: SDK is disabled, location tracking "
                            + "is disabled, or consent is unavailable.");
            return;
        }

        boolean hasPermission = hasLocationPermission();

        Log.d(TAG, "Location permission granted=" + hasPermission);

        if (!hasPermission) {
            Log.w(TAG,
                    "Location permission is missing. The host app "
                            + "must request foreground permission.");
            return;
        }

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                UPDATE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
                .build();

        try {
            trackingRequested = true;

            Log.d(TAG, "Requesting fused location updates.");

            // Keep the existing continuous tracking request.
            requestLocationUpdates(request);

            // Diagnostic only: test whether a current location
            // can be obtained independently of continuous updates.
            runOneTimeLocationDiagnostic();

        } catch (SecurityException exception) {
            trackingRequested = false;

            Log.e(TAG,
                    "Location permission was revoked.",
                    exception);
        }
    }

    @SuppressLint("MissingPermission")
    private void requestLocationUpdates(
            @NonNull LocationRequest request) {

        if (!hasLocationPermission()) {
            trackingRequested = false;

            Log.w(TAG,
                    "Permission missing before location request.");

            return;
        }

        try {
            locationClient.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
            ).addOnSuccessListener(unused ->
                    Log.d(TAG,
                            "Google Play Services accepted location updates.")
            ).addOnFailureListener(error -> {

                synchronized (P5ForegroundLocationTracker.this) {
                    trackingRequested = false;
                }

                Log.e(TAG,
                        "Could not start location updates.",
                        error);
            });

        } catch (SecurityException exception) {

            synchronized (this) {
                trackingRequested = false;
            }

            Log.e(TAG,
                    "Security exception while requesting location updates.",
                    exception);
        }
    }

    /**
     * Perform one current-location check for diagnosis.
     *
     * This does not submit a DEVICE_LOCATION event and does not
     * change the regular update interval.
     */
    @SuppressLint("MissingPermission")
    private void runOneTimeLocationDiagnostic() {

        if (!hasLocationPermission()) {
            Log.w(TAG,
                    "Diagnostic skipped: location permission missing.");
            return;
        }

        try {
            locationClient.getLocationAvailability()
                    .addOnSuccessListener(availability ->
                            Log.d(TAG,
                                    "One-time availability check: "
                                            + availability
                                            .isLocationAvailable())
                    )
                    .addOnFailureListener(error ->
                            Log.e(TAG,
                                    "Location availability check failed.",
                                    error));

            CancellationTokenSource cancellation =
                    new CancellationTokenSource();

            locationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellation.getToken()
            ).addOnSuccessListener(location -> {

                if (location == null) {
                    Log.w(TAG,
                            "One-time current-location check returned null.");
                    return;
                }

                long ageMs =
                        System.currentTimeMillis() - location.getTime();

                Log.d(TAG,
                        "One-time current location received. Accuracy="
                                + location.getAccuracy()
                                + "m, ageMs=" + ageMs);

            }).addOnFailureListener(error ->
                    Log.e(TAG,
                            "One-time current-location request failed.",
                            error));

        } catch (SecurityException exception) {
            Log.e(TAG,
                    "Permission error during location diagnostic.",
                    exception);
        }
    }

    /**
     * Stop continuous updates when the app enters the background
     * or location tracking is no longer permitted.
     */
    public synchronized void stop() {

        trackingRequested = false;

        Log.d(TAG, "Stopping foreground location updates.");

        locationClient.removeLocationUpdates(locationCallback)
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Location updates removed."))
                .addOnFailureListener(error ->
                        Log.w(TAG,
                                "Could not remove location updates.",
                                error));
    }

    private boolean hasLocationPermission() {

        return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

                || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }
}
