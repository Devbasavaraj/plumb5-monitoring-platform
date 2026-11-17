
package com.plumb5.monitor.beacon;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.plumb5.monitor.Plumb5Monitor;
import com.plumb5.monitor.core.SdkLogger;
import com.plumb5.monitor.core.SdkPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class P5Beacon {

    private static final int IBEACON_COMPANY_ID = 0x004C;
    private static final int IBEACON_PREFIX_1 = 0x02;
    private static final int IBEACON_PREFIX_2 = 0x15;

    private final Context context;
    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanning = false;

    private final Runnable stopScanRunnable =
            this::stopScanning;

    public P5Beacon(Context context) {
        this.context = context.getApplicationContext();
    }

    @SuppressLint("MissingPermission")
    public synchronized void startScanning() {
        startScanning(0);
    }

    @SuppressLint("MissingPermission")
    public synchronized void startScanning(long durationMs) {
        if (scanning) {
            SdkLogger.debug("P5Beacon: Scan already running");
            return;
        }

        SdkPreferences prefs = new SdkPreferences(context);

        if (!prefs.isEnabled() || !prefs.isBeaconEnabled()) {
            SdkLogger.debug(
                    "P5Beacon: Scanning skipped because SDK or beacon monitoring is disabled"
            );
            return;
        }

        if (!hasPermissions()) {
            SdkLogger.error(
                    "P5Beacon: Missing Bluetooth scanning permissions"
            );
            return;
        }

        try {
            BluetoothManager manager =
                    (BluetoothManager) context.getSystemService(
                            Context.BLUETOOTH_SERVICE
                    );

            if (manager == null) {
                SdkLogger.error(
                        "P5Beacon: Bluetooth service unavailable"
                );
                return;
            }

            BluetoothAdapter adapter = manager.getAdapter();

            if (adapter == null || !adapter.isEnabled()) {
                SdkLogger.debug(
                        "P5Beacon: Bluetooth is unavailable or disabled"
                );
                return;
            }

            bluetoothLeScanner = adapter.getBluetoothLeScanner();

            if (bluetoothLeScanner == null) {
                SdkLogger.error(
                        "P5Beacon: Bluetooth LE scanner unavailable"
                );
                return;
            }

            bluetoothLeScanner.startScan(leScanCallback);
            scanning = true;

            SdkLogger.debug("P5Beacon: Bluetooth scan started");

            handler.removeCallbacks(stopScanRunnable);

            if (durationMs > 0) {
                handler.postDelayed(stopScanRunnable, durationMs);
            }

        } catch (SecurityException e) {
            scanning = false;
            SdkLogger.error(
                    "P5Beacon: Bluetooth permission denied while starting scan",
                    e
            );
        } catch (RuntimeException e) {
            scanning = false;
            SdkLogger.error(
                    "P5Beacon: Unable to start Bluetooth scan",
                    e
            );
        }
    }

    @SuppressLint("MissingPermission")
    public synchronized void stopScanning() {
        handler.removeCallbacks(stopScanRunnable);

        if (!scanning) {
            return;
        }

        scanning = false;

        try {
            if (bluetoothLeScanner != null) {
                bluetoothLeScanner.stopScan(leScanCallback);
            }

            SdkLogger.debug("P5Beacon: Bluetooth scan stopped");

        } catch (SecurityException e) {
            SdkLogger.error(
                    "P5Beacon: Bluetooth permission denied while stopping scan",
                    e
            );
        } catch (RuntimeException e) {
            SdkLogger.error(
                    "P5Beacon: Error while stopping Bluetooth scan",
                    e
            );
        }
    }

    private final ScanCallback leScanCallback =
            new ScanCallback() {

                @Override
                public void onScanResult(
                        int callbackType,
                        ScanResult result) {

                    super.onScanResult(callbackType, result);

                    if (result != null) {
                        processScanResult(result);
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    super.onScanFailed(errorCode);

                    SdkLogger.error(
                            "P5Beacon: Bluetooth scan failed with code "
                                    + errorCode
                    );

                    stopScanning();
                }
            };

    private void processScanResult(ScanResult result) {
        try {
            ScanRecord record = result.getScanRecord();

            if (record == null) {
                return;
            }

            byte[] data =
                    record.getManufacturerSpecificData(
                            IBEACON_COMPANY_ID
                    );

            // iBeacon manufacturer data:
            // 0x02, 0x15, UUID (16 bytes),
            // major (2 bytes), minor (2 bytes), measured power (1 byte).
            if (!isValidIBeaconData(data)) {
                return;
            }

            String uuid = extractUuid(data);
            int major = extractUnsignedShort(data, 18);
            int minor = extractUnsignedShort(data, 20);
            int txPower = data[22];

            if (!matchesConfiguredFilters(uuid, major, minor)) {
                return;
            }

            int rssi = result.getRssi();
            double distance = calculateDistance(rssi, txPower);

            Map<String, String> props = new HashMap<>();
            props.put("uuid", uuid);
            props.put("major", String.valueOf(major));
            props.put("minor", String.valueOf(minor));
            props.put("rssi", String.valueOf(rssi));
            props.put("distance", String.valueOf(distance));

            try {
                props.put(
                        "address",
                        result.getDevice().getAddress()
                );
            } catch (SecurityException e) {
                // Device address is optional; retain the beacon event.
                props.put("address", "unavailable");
            }

            SdkLogger.debug(
                    "P5Beacon: Matching beacon detected: UUID="
                            + uuid
                            + ", major=" + major
                            + ", minor=" + minor
                            + ", RSSI=" + rssi
            );

            Plumb5Monitor.getInstance().trackEvent(
                    "BEACON_DETECTED",
                    null,
                    props
            );

        } catch (RuntimeException e) {
            SdkLogger.error(
                    "P5Beacon: Failed to process scan result",
                    e
            );
        }
    }

    private boolean isValidIBeaconData(byte[] data) {
        return data != null
                && data.length >= 23
                && (data[0] & 0xFF) == IBEACON_PREFIX_1
                && (data[1] & 0xFF) == IBEACON_PREFIX_2;
    }

    private String extractUuid(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data, 2, 16);

        long mostSignificantBits = buffer.getLong();
        long leastSignificantBits = buffer.getLong();

        return new UUID(
                mostSignificantBits,
                leastSignificantBits
        ).toString();
    }

    private int extractUnsignedShort(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8)
                | (data[offset + 1] & 0xFF);
    }

    private boolean matchesConfiguredFilters(
            String uuid,
            int major,
            int minor) {

        SdkPreferences prefs = new SdkPreferences(context);
        String filtersJson = prefs.getBeaconFilters();

        // No configured filters means accept any valid iBeacon.
        if (filtersJson == null || filtersJson.trim().isEmpty()) {
            return true;
        }

        try {
            JSONArray filters = new JSONArray(filtersJson);

            // An empty filter list means no restrictions.
            if (filters.length() == 0) {
                return true;
            }

            for (int i = 0; i < filters.length(); i++) {
                JSONObject filter = filters.optJSONObject(i);

                if (filter == null) {
                    // Older servers send "beacons" as a plain list of UUID
                    // strings. Accept those as UUID-only filters.
                    String plainUuid = filters.optString(i, "").trim();
                    if (!plainUuid.isEmpty()
                            && normalizeUuid(plainUuid).equals(
                            normalizeUuid(uuid))) {
                        return true;
                    }
                    continue;
                }

                String configuredUuid =
                        filter.optString(
                                "uuid",
                                filter.optString("beaconUuid", "")
                        ).trim();

                // Ignore malformed entries without a UUID.
                if (configuredUuid.isEmpty()) {
                    continue;
                }

                if (!normalizeUuid(configuredUuid).equals(
                        normalizeUuid(uuid))) {
                    continue;
                }

                if (filter.has("major")
                        && !filter.isNull("major")
                        && filter.optInt("major", -1) != major) {
                    continue;
                }

                if (filter.has("minor")
                        && !filter.isNull("minor")
                        && filter.optInt("minor", -1) != minor) {
                    continue;
                }

                return true;
            }

            return false;

        } catch (JSONException e) {
            SdkLogger.error(
                    "P5Beacon: Invalid saved beacon filter JSON",
                    e
            );
            return false;
        }
    }

    private String normalizeUuid(String uuid) {
        return uuid.replace("-", "")
                .replace("{", "")
                .replace("}", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private double calculateDistance(int rssi, int txPower) {
        if (rssi == 0 || txPower == 0) {
            return -1.0;
        }

        double ratio = (double) rssi / (double) txPower;

        if (ratio <= 0) {
            return -1.0;
        }

        if (ratio < 1.0) {
            return Math.pow(ratio, 10);
        }

        return 0.89976 * Math.pow(ratio, 7.7095) + 0.111;
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(
                    android.Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED;
        }

        return context.checkSelfPermission(
                android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }
}
