package com.plumb5.monitor.event;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.plumb5.monitor.MonitoringConfig;
import com.plumb5.monitor.core.SdkLogger;
import com.plumb5.monitor.database.EventEntity;
import com.plumb5.monitor.database.MonitoringDatabase;
import com.plumb5.monitor.worker.UploadScheduler;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EventManager {

    private final Context context;
    private final MonitoringConfig config;
    private final MonitoringDatabase database;
    private final ExecutorService executor;

    private volatile String sessionId;

    public EventManager(
            @NonNull Context context,
            @NonNull MonitoringConfig config) {

        this.context =
                context.getApplicationContext();

        this.config = config;

        this.database =
                MonitoringDatabase.getInstance(
                        this.context
                );

        this.executor =
                Executors.newSingleThreadExecutor();

        this.sessionId =
                UUID.randomUUID().toString();
    }

    public void logEvent(
            @NonNull String eventName) {

        logEvent(
                eventName,
                null
        );
    }

    public void logEvent(
            @NonNull String eventName,
            String screenName) {

        if (!config.isEnabled()) {
            return;
        }

        executor.execute(() -> {

            try {

                EventEntity event =
                        new EventEntity();

                event.setEventId(
                        UUID.randomUUID().toString()
                );

                event.setAppId(
                        config.getAppId()
                );

                event.setSessionId(
                        sessionId
                );

                event.setEventName(
                        eventName
                );

                event.setScreenName(
                        screenName
                );

                event.setDeviceModel(
                        Build.MODEL
                );

                event.setAndroidVersion(
                        Build.VERSION.RELEASE
                );

                event.setSdkVersion(
                        "1.0.0"
                );

                event.setTimestamp(
                        System.currentTimeMillis()
                );

                event.setRetryCount(0);

                database
                        .eventDao()
                        .insertEvent(event);

                SdkLogger.debug(
                        "Event stored successfully: " +
                                eventName
                );

                UploadScheduler.scheduleUpload(
                        context,
                        config
                );

            } catch (Exception e) {

                SdkLogger.error(
                        "Failed to store event",
                        e
                );
            }
        });
    }

    public void startNewSession() {

        sessionId =
                UUID.randomUUID().toString();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void getEventCount(
            EventCountCallback callback) {

        executor.execute(() -> {

            int count =
                    database
                            .eventDao()
                            .getEventCount();

            if (callback != null) {

                callback.onResult(count);
            }
        });
    }

    public void clearEvents() {

        executor.execute(() ->
                database
                        .eventDao()
                        .deleteAll()
        );
    }

    public void shutdown() {
        executor.shutdown();
    }

    public interface EventCountCallback {
        void onResult(int count);
    }
}
