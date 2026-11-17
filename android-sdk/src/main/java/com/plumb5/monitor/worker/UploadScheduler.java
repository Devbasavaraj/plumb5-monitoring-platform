
package com.plumb5.monitor.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.plumb5.monitor.MonitoringConfig;
import com.plumb5.monitor.core.SdkLogger;

import java.util.concurrent.TimeUnit;

public final class UploadScheduler {

    private static final String WORK_NAME =
            "monitoring_event_upload";

    private static final String TRANSACTION_WORK_NAME =
            "monitoring_transaction_upload";

    private static final String SYNC_WORK_NAME =
            "monitoring_config_sync";

    private static final String BEACON_WORK_NAME =
            "monitoring_beacon_scan";

    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_LICENSE_SERVER_URL = "license_server_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_BATCH_SIZE = "batch_size";
    private static final String KEY_MAX_RETRIES = "max_retries";
    private static final String KEY_GRACE_PERIOD = "grace_period";
    private static final String KEY_DEV_MODE = "dev_mode";

    private static final long BACKOFF_DELAY_SECONDS = 10L;

    private static final int MIN_SYNC_INTERVAL_MINUTES = 15;
    private static final int MAX_SYNC_INTERVAL_MINUTES = 1440;
    private static final int BEACON_SCAN_INTERVAL_MINUTES = 15;

    private UploadScheduler() {
        // Utility class.
    }

    // ============================================================
    // EVENT UPLOAD
    // ============================================================

    public static void scheduleUpload(
            @NonNull Context context,
            @NonNull MonitoringConfig config) {

        if (!config.isEnabled()) {
            return;
        }

        Context appContext = context.getApplicationContext();
        OneTimeWorkRequest request = createUploadRequest(config);

        WorkManager.getInstance(appContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
        );

        SdkLogger.debug("UploadScheduler: upload scheduled");
    }

    public static void scheduleImmediateUpload(
            @NonNull Context context,
            @NonNull MonitoringConfig config) {

        if (!config.isEnabled()) {
            return;
        }

        Context appContext = context.getApplicationContext();
        OneTimeWorkRequest request = createUploadRequest(config);

        WorkManager.getInstance(appContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
        );

        SdkLogger.debug("UploadScheduler: immediate upload scheduled");
    }

    @NonNull
    private static OneTimeWorkRequest createUploadRequest(
            @NonNull MonitoringConfig config) {

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Data inputData = createWorkerInputData(config);

        return new OneTimeWorkRequest.Builder(EventUploadWorker.class)
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        BACKOFF_DELAY_SECONDS,
                        TimeUnit.SECONDS
                )
                .build();
    }

    // ============================================================
    // TRANSACTION UPLOAD
    // ============================================================

    public static void scheduleTransactionUpload(
            @NonNull Context context,
            @NonNull MonitoringConfig config) {

        if (!config.isEnabled()) {
            return;
        }

        Context appContext = context.getApplicationContext();

        OneTimeWorkRequest request =
                createTransactionUploadRequest(config);

        WorkManager.getInstance(appContext).enqueueUniqueWork(
                TRANSACTION_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
        );

        SdkLogger.debug(
                "UploadScheduler: transaction upload scheduled"
        );
    }

    public static void scheduleImmediateTransactionUpload(
            @NonNull Context context,
            @NonNull MonitoringConfig config) {

        if (!config.isEnabled()) {
            return;
        }

        Context appContext = context.getApplicationContext();

        OneTimeWorkRequest request =
                createTransactionUploadRequest(config);

        WorkManager.getInstance(appContext).enqueueUniqueWork(
                TRANSACTION_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
        );

        SdkLogger.debug(
                "UploadScheduler: immediate transaction upload scheduled"
        );
    }

    @NonNull
    private static OneTimeWorkRequest createTransactionUploadRequest(
            @NonNull MonitoringConfig config) {

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Data inputData = createWorkerInputData(config);

        return new OneTimeWorkRequest.Builder(
                TransactionUploadWorker.class
        )
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        BACKOFF_DELAY_SECONDS,
                        TimeUnit.SECONDS
                )
                .build();
    }

    // ============================================================
    // SHARED WORKER INPUT
    // ============================================================

    @NonNull
    private static Data createWorkerInputData(
            @NonNull MonitoringConfig config) {

        return new Data.Builder()
                .putString(
                        KEY_SERVER_URL,
                        config.getServerUrl()
                )
                .putString(
                        KEY_LICENSE_SERVER_URL,
                        config.getLicenseServerUrl()
                )
                .putString(
                        KEY_API_KEY,
                        config.getApiKey()
                )
                .putInt(
                        KEY_BATCH_SIZE,
                        config.getBatchSize()
                )
                .putInt(
                        KEY_MAX_RETRIES,
                        config.getMaxRetries()
                )
                .putInt(
                        KEY_GRACE_PERIOD,
                        config.getLicenseGracePeriodMinutes()
                )
                .putBoolean(
                        KEY_DEV_MODE,
                        config.isDevelopmentMode()
                )
                .build();
    }

    // ============================================================
    // REMOTE CONFIG SYNC
    // ============================================================

    public static void scheduleConfigSync(
            @NonNull Context context) {

        scheduleConfigSync(
                context,
                MIN_SYNC_INTERVAL_MINUTES
        );
    }

    public static void scheduleConfigSync(
            @NonNull Context context,
            int intervalMinutes) {

        int safeInterval = Math.max(
                MIN_SYNC_INTERVAL_MINUTES,
                Math.min(
                        MAX_SYNC_INTERVAL_MINUTES,
                        intervalMinutes
                )
        );

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                        ConfigSyncWorker.class,
                        safeInterval,
                        TimeUnit.MINUTES
                )
                        .setConstraints(constraints)
                        .build();

        WorkManager.getInstance(
                context.getApplicationContext()
        ).enqueueUniquePeriodicWork(
                SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );

        SdkLogger.debug(
                "UploadScheduler: Config sync scheduled every "
                        + safeInterval
                        + " minutes"
        );
    }

    // ============================================================
    // BEACON SCANNING
    // ============================================================

    /**
     * Schedule periodic beacon scanning only when both the SDK and
     * beacon monitoring are enabled.
     *
     * Existing beacon work is cancelled when either setting is disabled.
     */
    public static void scheduleBeaconScan(
            @NonNull Context context,
            @NonNull MonitoringConfig config) {

        Context appContext = context.getApplicationContext();
        WorkManager workManager = WorkManager.getInstance(appContext);

        if (!config.isEnabled() || !config.isBeaconEnabled()) {
            cancelBeaconScan(appContext);

            SdkLogger.debug(
                    "UploadScheduler: Beacon scan not scheduled; "
                            + "SDK or beacon monitoring is disabled"
            );
            return;
        }

        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                        BeaconScanWorker.class,
                        BEACON_SCAN_INTERVAL_MINUTES,
                        TimeUnit.MINUTES
                )
                        .build();

        workManager.enqueueUniquePeriodicWork(
                BEACON_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );

        SdkLogger.debug(
                "UploadScheduler: Beacon scan scheduled every "
                        + BEACON_SCAN_INTERVAL_MINUTES
                        + " minutes"
        );
    }

    /**
     * Cancel any previously scheduled periodic beacon scan.
     */
    public static void cancelBeaconScan(
            @NonNull Context context) {

        WorkManager.getInstance(
                context.getApplicationContext()
        ).cancelUniqueWork(BEACON_WORK_NAME);

        SdkLogger.debug(
                "UploadScheduler: Beacon scan cancellation requested"
        );
    }
}
