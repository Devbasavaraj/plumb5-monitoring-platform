package com.plumb5.monitor.worker;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.plumb5.monitor.core.SdkLogger;
import com.plumb5.monitor.database.EventDao;
import com.plumb5.monitor.database.EventEntity;
import com.plumb5.monitor.database.MonitoringDatabase;
import com.plumb5.monitor.network.ApiClient;

import java.util.List;

public class EventUploadWorker extends Worker {

    private static final String KEY_SERVER_URL =
            "server_url";

    private static final String KEY_LICENSE_SERVER_URL =
            "license_server_url";

    private static final String KEY_API_KEY =
            "api_key";

    private static final String KEY_BATCH_SIZE =
            "batch_size";

    private static final String KEY_MAX_RETRIES =
            "max_retries";

    private static final String KEY_GRACE_PERIOD =
            "grace_period";

    private static final String KEY_DEV_MODE =
            "dev_mode";

    public EventUploadWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {

        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {

        String serverUrl =
                getInputData().getString(
                        KEY_SERVER_URL
                );

        String licenseServerUrl =
                getInputData().getString(
                        KEY_LICENSE_SERVER_URL
                );

        String apiKey =
                getInputData().getString(
                        KEY_API_KEY
                );

        int batchSize =
                getInputData().getInt(
                        KEY_BATCH_SIZE,
                        50
                );

        int maxRetries =
                getInputData().getInt(
                        KEY_MAX_RETRIES,
                        3
                );

        int gracePeriod =
                getInputData().getInt(
                        KEY_GRACE_PERIOD,
                        60
                );

        boolean isDevMode =
                getInputData().getBoolean(
                        KEY_DEV_MODE,
                        false
                );

        // ========================================================
        // VALIDATE INPUT
        // ========================================================

        if (serverUrl == null ||
                serverUrl.trim().isEmpty()) {

            SdkLogger.error(
                    "UploadWorker: Server URL missing."
            );

            return Result.failure();
        }

        if (apiKey == null ||
                apiKey.trim().isEmpty()) {

            SdkLogger.error(
                    "UploadWorker: API key missing."
            );

            return Result.failure();
        }

        /*
         * Backward compatibility:
         *
         * If older WorkManager data does not contain
         * licenseServerUrl, use serverUrl.
         */
        if (licenseServerUrl == null ||
                licenseServerUrl.trim().isEmpty()) {

            licenseServerUrl = serverUrl;
        }

        // ========================================================
        // BATTERY
        // ========================================================

        if (!isDevMode &&
                isBatteryCritical()) {

            SdkLogger.debug(
                    "UploadWorker: Battery critical. Skipping upload."
            );

            return Result.retry();
        }

        try {

            Context context =
                    getApplicationContext();

            MonitoringDatabase database =
                    MonitoringDatabase.getInstance(
                            context
                    );

            EventDao dao =
                    database.eventDao();

            // ====================================================
            // API CLIENT
            // ====================================================

            ApiClient apiClient =
                    new ApiClient(
                            context,
                            serverUrl,
                            apiKey,
                            licenseServerUrl
                    );

            SdkLogger.debug(
                    "UploadWorker: Monitoring server: "
                            + serverUrl
            );

            SdkLogger.debug(
                    "UploadWorker: License server: "
                            + licenseServerUrl
            );

            // ====================================================
            // LICENSE VALIDATION
            // ====================================================

            if (!apiClient.validateLicense(
                    gracePeriod
            )) {

                SdkLogger.error(
                        "UploadWorker: License invalid. Stopping uploads."
                );

                return Result.failure();
            }

            int totalUploaded = 0;

            int adaptiveBatchSize =
                    getAdaptiveBatchSize(
                            batchSize
                    );

            SdkLogger.debug(
                    "UploadWorker: Adaptive batch size: "
                            + adaptiveBatchSize
            );

            // ====================================================
            // UPLOAD LOOP
            // ====================================================

            while (!isStopped()) {

                List<EventEntity> events =
                        dao.getPendingEvents(
                                adaptiveBatchSize
                        );

                if (events == null ||
                        events.isEmpty()) {

                    break;
                }

                boolean success =
                        apiClient.uploadEvents(
                                events
                        );

                if (!success) {

                    int attempts =
                            getRunAttemptCount();

                    if (attempts >= maxRetries) {

                        SdkLogger.error(
                                "UploadWorker: Maximum retry attempts reached."
                        );

                        return Result.failure();
                    }

                    return Result.retry();
                }

                /*
                 * Delete only after successful upload.
                 */
                dao.deleteEvents(events);

                totalUploaded +=
                        events.size();

                SdkLogger.debug(
                        "UploadWorker: Batch uploaded. Count: "
                                + events.size()
                );
            }

            SdkLogger.debug(
                    "UploadWorker: Sync complete. Total: "
                            + totalUploaded
            );


            // ====================================================
            // USAGE REPORT
            // ====================================================

            if (totalUploaded > 0) {

                apiClient.reportUsage(totalUploaded);

                SdkLogger.debug(
                        "UploadWorker: Usage report requested. Events uploaded: "
                                + totalUploaded
                );

            } else {

                SdkLogger.debug(
                        "UploadWorker: No events uploaded. Skipping usage report."
                );

            }

            return Result.success();

        } catch (Exception e) {

            SdkLogger.error(
                    "UploadWorker: Unexpected error during upload.",
                    e
            );

            return Result.retry();
        }
    }




            // ============================================================
    // BATTERY
    // ============================================================

    private boolean isBatteryCritical() {

        try {

            Intent intent =
                    getApplicationContext()
                            .registerReceiver(
                                    null,
                                    new IntentFilter(
                                            Intent.ACTION_BATTERY_CHANGED
                                    )
                            );

            if (intent != null) {

                int level =
                        intent.getIntExtra(
                                BatteryManager.EXTRA_LEVEL,
                                -1
                        );

                int scale =
                        intent.getIntExtra(
                                BatteryManager.EXTRA_SCALE,
                                -1
                        );

                if (level >= 0 &&
                        scale > 0) {

                    int pct =
                            (int)
                                    ((level /
                                            (float) scale)
                                            * 100);

                    return pct < 10;
                }
            }

        } catch (Exception ignored) {
        }

        return false;
    }

    // ============================================================
    // ADAPTIVE BATCH SIZE
    // ============================================================

    private int getAdaptiveBatchSize(
            int maxBatchSize) {

        try {

            ConnectivityManager cm =
                    (ConnectivityManager)
                            getApplicationContext()
                                    .getSystemService(
                                            Context.CONNECTIVITY_SERVICE
                                    );

            if (cm == null) {

                return maxBatchSize;
            }

            NetworkCapabilities nc =
                    cm.getNetworkCapabilities(
                            cm.getActiveNetwork()
                    );

            if (nc != null) {

                int speedKbps =
                        nc.getLinkDownstreamBandwidthKbps();

                if (speedKbps < 1000) {

                    return Math.min(
                            10,
                            maxBatchSize
                    );
                }

                if (speedKbps < 5000) {

                    return Math.min(
                            50,
                            maxBatchSize
                    );
                }
            }

        } catch (Exception ignored) {
        }

        return maxBatchSize;
    }
}