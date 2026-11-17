package com.plumb5.monitor.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility to capture detailed device and environment information.
 */
public class P5DeviceInfo {

    public static Map<String, String> getAdvancedInfo(Context context) {
        Map<String, String> info = new HashMap<>();

        // Display Info
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        info.put("screen_resolution", dm.widthPixels + "x" + dm.heightPixels);
        info.put("screen_density", String.valueOf(dm.densityDpi));

        // System Info
        info.put("manufacturer", Build.MANUFACTURER);
        info.put("hardware", Build.HARDWARE);
        info.put("fingerprint", Build.FINGERPRINT);
        info.put("bootloader", Build.BOOTLOADER);

        // Sensor Availability
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sm != null) {
            info.put("has_accelerometer", String.valueOf(sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null));
            info.put("has_gyroscope", String.valueOf(sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null));
        }

        return info;
    }

    public static int getAppVersionCode(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) pInfo.getLongVersionCode();
            } else {
                return pInfo.versionCode;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }
}
