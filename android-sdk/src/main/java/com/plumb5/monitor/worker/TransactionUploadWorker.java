package com.plumb5.monitor.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.plumb5.monitor.core.SdkLogger;
import com.plumb5.monitor.database.MonitoringDatabase;
import com.plumb5.monitor.database.TransactionDao;
import com.plumb5.monitor.database.TransactionEntity;
import com.plumb5.monitor.network.ApiClient;

import java.util.List;

public class TransactionUploadWorker
        extends Worker {

    public static final String KEY_SERVER_URL =
            "server_url";

    public static final String KEY_API_KEY =
            "api_key";

    public static final String KEY_LICENSE_SERVER_URL =
            "license_server_url";

    public static final String KEY_BATCH_SIZE =
            "batch_size";

    public static final String KEY_MAX_RETRIES =
            "max_retries";

    public static final String KEY_GRACE_PERIOD =
            "grace_period";

    public static final String KEY_DEV_MODE =
            "dev_mode";


    public TransactionUploadWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams) {

        super(context, workerParams);
    }


    @NonNull
    @Override
    public Result doWork() {

        Context context =
                getApplicationContext();


        String serverUrl =
                getInputData().getString(
                        KEY_SERVER_URL
                );

        String apiKey =
                getInputData().getString(
                        KEY_API_KEY
                );

        String licenseServerUrl =
                getInputData().getString(
                        KEY_LICENSE_SERVER_URL
                );


        int batchSize =
                getInputData().getInt(
                        KEY_BATCH_SIZE,
                        10
                );

        int maxRetries =
                getInputData().getInt(
                        KEY_MAX_RETRIES,
                        3
                );

        int gracePeriod =
                getInputData().getInt(
                        KEY_GRACE_PERIOD,
                        1440
                );


        if (serverUrl == null ||
                serverUrl.trim().isEmpty()) {

            SdkLogger.error(
                    "TransactionUploadWorker: " +
                            "Server URL missing"
            );

            return Result.failure();
        }


        if (apiKey == null ||
                apiKey.trim().isEmpty()) {

            SdkLogger.error(
                    "TransactionUploadWorker: " +
                            "API key missing"
            );

            return Result.failure();
        }


        if (licenseServerUrl == null ||
                licenseServerUrl.trim().isEmpty()) {

            licenseServerUrl = serverUrl;
        }


        SdkLogger.debug(
                "TransactionUploadWorker started"
        );

        SdkLogger.debug(
                "Monitoring server: " +
                        serverUrl
        );

        SdkLogger.debug(
                "License server: " +
                        licenseServerUrl
        );


        ApiClient apiClient =
                new ApiClient(
                        context,
                        serverUrl,
                        apiKey,
                        licenseServerUrl
                );


        boolean licenseValid =
                apiClient.validateLicense(
                        gracePeriod
                );


        if (!licenseValid) {

            SdkLogger.error(
                    "TransactionUploadWorker: " +
                            "License invalid. " +
                            "Stopping uploads."
            );

            return Result.failure();
        }


        MonitoringDatabase database =
                MonitoringDatabase.getInstance(
                        context
                );


        TransactionDao dao =
                database.transactionDao();


        int totalUploaded = 0;


        while (true) {

            List<TransactionEntity> transactions =
                    dao.getPendingTransactions(
                            batchSize
                    );


            if (transactions == null ||
                    transactions.isEmpty()) {

                break;
            }


            SdkLogger.debug(
                    "Uploading transaction batch. " +
                            "Count=" +
                            transactions.size()
            );


            boolean uploaded =
                    apiClient.uploadTransactions(
                            transactions
                    );


            if (!uploaded) {

                int attempt =
                        getRunAttemptCount();


                SdkLogger.error(
                        "Transaction upload failed. " +
                                "Attempt=" +
                                attempt
                );


                if (attempt >= maxRetries) {

                    return Result.failure();
                }


                return Result.retry();
            }


            try {

                dao.deleteTransactions(
                        transactions
                );

            } catch (Exception e) {

                SdkLogger.error(
                        "Transaction upload succeeded " +
                                "but local deletion failed",
                        e
                );

                return Result.failure();
            }


            totalUploaded +=
                    transactions.size();


            SdkLogger.debug(
                    "Transaction batch uploaded " +
                            "and deleted successfully. " +
                            "Count=" +
                            transactions.size()
            );
        }


        SdkLogger.debug(
                "Transaction sync complete. " +
                        "Total uploaded=" +
                        totalUploaded
        );


        return Result.success();
    }
}