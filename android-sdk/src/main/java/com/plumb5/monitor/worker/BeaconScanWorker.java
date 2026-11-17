
package com.plumb5.monitor.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.plumb5.monitor.beacon.P5Beacon;
import com.plumb5.monitor.core.SdkLogger;
import com.plumb5.monitor.core.SdkPreferences;

import java.util.concurrent.TimeUnit;

public class BeaconScanWorker extends Worker {

    private static final long SCAN_DURATION_MS = 30_000L;
    private static final long WAIT_TIMEOUT_SECONDS = 32L;

    public BeaconScanWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        SdkPreferences prefs = new SdkPreferences(context);

        if (!prefs.isEnabled() || !prefs.isBeaconEnabled()) {
            SdkLogger.debug(
                    "BeaconScanWorker: SDK or beacon monitoring disabled"
            );
            return Result.success();
        }

        P5Beacon beacon = new P5Beacon(context);

        try {
            SdkLogger.debug(
                    "BeaconScanWorker: Starting 30-second scan"
            );

            beacon.startScanning(SCAN_DURATION_MS);

            // Keep this Worker running during the scan window.
            // P5Beacon also schedules its own stop callback.
            TimeUnit.SECONDS.sleep(WAIT_TIMEOUT_SECONDS);

            SdkLogger.debug(
                    "BeaconScanWorker: Scan window completed"
            );

            return Result.success();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            SdkLogger.debug(
                    "BeaconScanWorker: Scan interrupted"
            );

            return Result.retry();

        } catch (SecurityException e) {
            SdkLogger.debug(
                    "BeaconScanWorker: Bluetooth permission denied"
            );

            return Result.failure();

        } catch (RuntimeException e) {
            SdkLogger.debug(
                    "BeaconScanWorker: Scan failed"
            );

            return Result.retry();

        } finally {
            beacon.stopScanning();
        }
    }
}
