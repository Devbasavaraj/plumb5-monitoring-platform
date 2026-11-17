package com.plumb5.monitor.transaction;

import android.content.Context;

import androidx.annotation.NonNull;

import com.plumb5.monitor.MonitoringConfig;
import com.plumb5.monitor.core.SdkLogger;
import com.plumb5.monitor.database.MonitoringDatabase;
import com.plumb5.monitor.database.TransactionDao;
import com.plumb5.monitor.database.TransactionEntity;
import com.plumb5.monitor.worker.UploadScheduler;

import org.json.JSONArray;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TransactionManager {

    private final Context context;
    private final MonitoringConfig config;
    private final ExecutorService executor;

    private volatile String sessionId;
    private volatile String userId;

    public TransactionManager(
            @NonNull Context context,
            @NonNull MonitoringConfig config,
            @NonNull String sessionId) {

        this.context = context.getApplicationContext();
        this.config = config;
        this.sessionId = sessionId;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void setSessionId(String sessionId) {
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            this.sessionId = sessionId;
        }
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Stores transaction data locally and schedules background upload.
     */
    public void trackTransaction(JSONArray transactionData) {

        if (transactionData == null) {
            return;
        }

        if (!config.isEnabled()) {
            return;
        }

        final String jsonData = transactionData.toString();

        executor.execute(() -> {

            try {

                MonitoringDatabase database =
                        MonitoringDatabase.getInstance(context);

                TransactionDao dao =
                        database.transactionDao();

                /*
                 * Prevent unlimited local database growth.
                 */
                if (dao.getTransactionCount()
                        >= config.getMaxDatabaseSize()) {

                    dao.deleteOldest(100);
                }

                TransactionEntity transaction =
                        new TransactionEntity();

                transaction.setTransactionId(
                        UUID.randomUUID().toString()
                );

                transaction.setAppId(
                        config.getAppId()
                );

                transaction.setSessionId(
                        sessionId
                );

                transaction.setUserId(
                        userId
                );

                transaction.setTimestamp(
                        System.currentTimeMillis()
                );

                transaction.setTransactionData(
                        jsonData
                );

                transaction.setRetryCount(0);

                dao.insertTransaction(transaction);

                SdkLogger.debug(
                        "Transaction stored locally: "
                                + transaction.getTransactionId()
                );

                /*
                 * Schedule upload.
                 */
                UploadScheduler.scheduleTransactionUpload(
                        context,
                        config
                );

            } catch (Exception e) {

                SdkLogger.error(
                        "TransactionManager: Failed to store transaction",
                        e
                );
            }
        });
    }

    public void shutdown() {

        try {
            executor.shutdown();
        } catch (Exception e) {
            SdkLogger.error(
                    "TransactionManager shutdown failed",
                    e
            );
        }
    }
}