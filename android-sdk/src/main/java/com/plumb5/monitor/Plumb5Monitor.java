
package com.plumb5.monitor;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.plumb5.monitor.campaign.LocalCampaignManager;
import com.plumb5.monitor.core.CommunicationMode;
import com.plumb5.monitor.core.P5ConnectionDetector;
import com.plumb5.monitor.core.P5DeviceInfo;
import com.plumb5.monitor.core.P5ErrorSeverity;
import com.plumb5.monitor.core.P5InstallReferrerClient;
import com.plumb5.monitor.core.SdkLogger;
import com.plumb5.monitor.core.SdkPreferences;
import com.plumb5.monitor.database.EventEntity;
import com.plumb5.monitor.database.MonitoringDatabase;
import com.plumb5.monitor.location.P5GeofenceController;
import com.plumb5.monitor.network.ApiClient;
import com.plumb5.monitor.network.ServerHealthResult;
import com.plumb5.monitor.transaction.TransactionManager;
import com.plumb5.monitor.ui.P5ChatEngine;
import com.plumb5.monitor.worker.UploadScheduler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class Plumb5Monitor {

    private static final String SDK_VERSION = "1.4.0";
    private static volatile Plumb5Monitor INSTANCE;

    private final Context context;
    private volatile MonitoringConfig config;
    private final ExecutorService databaseExecutor;
    private final LocalCampaignManager campaignManager;
    private final P5ConnectionDetector connectionDetector;
    private final TransactionManager transactionManager;

    private volatile String sessionId;
    private volatile boolean initialized;
    private volatile String userId;

    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private final Map<String, Long> timers = new ConcurrentHashMap<>();
    private final Map<String, String> sessionProperties = new ConcurrentHashMap<>();

    private volatile String userName;
    private volatile String userEmail;
    private volatile String userPhone;

    private final LinkedList<String> breadcrumbs = new LinkedList<>();
    private static final int MAX_BREADCRUMBS = 5;

    private volatile Thread.UncaughtExceptionHandler previousCrashHandler;
    private volatile Thread.UncaughtExceptionHandler sdkCrashHandler;

    public interface HealthCheckCallback {
        void onResult(boolean reachable, int httpCode, String message);
    }

    private Plumb5Monitor(
            @NonNull Context context,
            @NonNull MonitoringConfig initialConfig) {

        this.context = context.getApplicationContext();

        SdkPreferences prefs = new SdkPreferences(this.context);
        MonitoringConfig.Builder builder = initialConfig.toBuilder();

        if (prefs.isConfigured()
                && initialConfig.getAppId().equals(prefs.getAppId())) {
            builder.enabled(prefs.isEnabled());
            builder.batchSize(prefs.getBatchSize());
            builder.maxRetries(prefs.getMaxRetries());
            builder.debugLogging(prefs.isDebugLoggingEnabled());
            builder.maxDatabaseSize(prefs.getMaxDatabaseSize());
            builder.developmentMode(prefs.isDevelopmentMode());
            // Preserve the beacon setting supplied by the app; remote config may override it.
            builder.geofenceEnabled(prefs.isGeofenceEnabled());
            builder.syncIntervalMinutes(prefs.getSyncIntervalMinutes());
        }

        this.config = builder.build();
        this.databaseExecutor = Executors.newSingleThreadExecutor();
        this.campaignManager = new LocalCampaignManager(this.context);
        this.connectionDetector = new P5ConnectionDetector(this.context);
        this.connectionDetector.setOnNetworkRestoredListener(this::uploadNow);

        this.sessionId = UUID.randomUUID().toString();
        this.initialized = true;

        this.transactionManager = new TransactionManager(
                this.context,
                this.config,
                this.sessionId
        );

        loadSavedUserInfo();
        checkAppUpdate();
        checkInstallReferrer();

        SdkLogger.initialize(this.config.isDebugLoggingEnabled());
        setupCrashReporting();
        printInitializationLog();
        pruneOldEvents();

        UploadScheduler.scheduleConfigSync(
                this.context,
                this.config.getSyncIntervalMinutes()
        );

        syncRemoteConfig();

        // Fixed: scheduler checks both SDK-enabled and beacon-enabled states.
        UploadScheduler.scheduleBeaconScan(this.context, this.config);

        if (this.config.isGeofenceEnabled()) {
            new P5GeofenceController(this.context);
            // Re-register geofences saved by the last config sync.
            com.plumb5.monitor.engage.P5GeofenceSync.restore(this.context);
        }
    }

    private void loadSavedUserInfo() {
        SdkPreferences prefs = new SdkPreferences(context);
        userName = prefs.getUserName();
        userEmail = prefs.getUserEmail();
        userPhone = prefs.getUserPhone();
    }

    private void checkAppUpdate() {
        SdkPreferences prefs = new SdkPreferences(context);
        int currentVersion = P5DeviceInfo.getAppVersionCode(context);
        int lastVersion = prefs.getLastAppVersion();

        if (lastVersion != -1 && currentVersion > lastVersion) {
            Map<String, String> props = new HashMap<>();
            props.put("old_version", String.valueOf(lastVersion));
            props.put("new_version", String.valueOf(currentVersion));
            trackEvent("APP_UPDATED", null, props);
        }

        prefs.setLastAppVersion(currentVersion);
    }

    private void checkInstallReferrer() {
        SdkPreferences prefs = new SdkPreferences(context);
        if (!prefs.isReferrerChecked()) {
            new P5InstallReferrerClient(context).startConnection();
            prefs.setReferrerChecked(true);
        }
    }

    @NonNull
    public static Plumb5Monitor initialize(
            @NonNull Context context,
            @NonNull MonitoringConfig config) {

        Plumb5Monitor instance = INSTANCE;

        if (instance == null) {
            synchronized (Plumb5Monitor.class) {
                instance = INSTANCE;
                if (instance == null) {
                    instance = new Plumb5Monitor(context, config);
                    INSTANCE = instance;
                    new SdkPreferences(instance.context).saveConfig(instance.config);
                }
            }
        }

        Context applicationContext = context.getApplicationContext();
        if (applicationContext instanceof Application) {
            try {
                MonitoringInitializer.initialize(
                        (Application) applicationContext,
                        instance.config
                );
                SdkLogger.debug("Plumb5Monitor: lifecycle callbacks registered");
            } catch (Exception e) {
                SdkLogger.error(
                        "Plumb5Monitor: lifecycle registration failed",
                        e
                );
            }
        } else {
            SdkLogger.error(
                    "Plumb5Monitor: Application context unavailable; "
                            + "lifecycle callbacks were not registered"
            );
        }

        return instance;
    }

    @NonNull
    public static Plumb5Monitor init(
            @NonNull Context context,
            @NonNull String appId,
            @NonNull String apiKey,
            @NonNull String serverUrl) {
        return init(
                context,
                appId,
                apiKey,
                serverUrl,
                !serverUrl.contains("10.0.2.2")
                        && !serverUrl.contains("localhost")
                        && !serverUrl.contains("127.0.0.1")
        );
    }

    @NonNull
    public static Plumb5Monitor init(
            @NonNull Context context,
            @NonNull String appId,
            @NonNull String apiKey,
            @NonNull String serverUrl,
            boolean enforceHttps) {

        MonitoringConfig config = new MonitoringConfig.Builder(appId, serverUrl)
                .apiKey(apiKey)
                .enforceHttps(enforceHttps)
                .debugLogging(true)
                .build();

        return initialize(context, config);
    }

    public static void p5Init(
            @NonNull Context context,
            @NonNull String appKey,
            @NonNull String serviceUrl,
            @NonNull CommunicationMode mode) {

        boolean enforceHttps = !serviceUrl.contains("10.0.2.2")
                && !serviceUrl.contains("localhost")
                && !serviceUrl.contains("127.0.0.1");

        MonitoringConfig config = new MonitoringConfig.Builder(appKey, serviceUrl)
                .apiKey(appKey)
                .communicationMode(mode)
                .enforceHttps(enforceHttps)
                .build();

        Plumb5Monitor monitor = initialize(context, config);
        new SdkPreferences(monitor.context).saveConfig(monitor.config);
    }

    @NonNull
    public static Plumb5Monitor getInstance() {
        Plumb5Monitor instance = INSTANCE;
        if (instance == null) {
            throw new IllegalStateException(
                    "Plumb5Monitor is not initialized. Call initialize() first."
            );
        }
        if (!instance.initialized) {
            throw new IllegalStateException("Plumb5Monitor has been shut down.");
        }
        return instance;
    }

    public void identifyUser(String userId, Map<String, String> properties) {
        this.userId = userId;
        transactionManager.setUserId(userId);
        trackEvent("USER_IDENTIFIED", null, properties);
    }

    public void p5SetUserInfo(
            Context context,
            String name,
            String email,
            String phone,
            String extra) {

        userName = name;
        userEmail = email;
        userPhone = phone;

        new SdkPreferences(this.context).saveUserInfo(name, email, phone);

        final String reportName = name;
        final String reportEmail = email;
        final String reportPhone = phone;

        try {
            databaseExecutor.execute(() -> {
                try {
                    ApiClient apiClient = new ApiClient(
                            this.context,
                            config.getServerUrl(),
                            config.getApiKey(),
                            config.getLicenseServerUrl()
                    );

                    boolean reported = apiClient.reportUser(
                            config.getAppId(),
                            reportName,
                            reportEmail,
                            reportPhone
                    );

                    if (reported) {
                        SdkLogger.debug("User identity reported successfully");
                    } else {
                        SdkLogger.error("User identity report failed");
                    }
                } catch (Exception e) {
                    SdkLogger.error("User identity reporting exception", e);
                }
            });
        } catch (RejectedExecutionException e) {
            SdkLogger.error(
                    "User identity reporting rejected: SDK is shutting down",
                    e
            );
        }

        Map<String, String> props = new HashMap<>();
        props.put("name", name != null ? name : "");
        props.put("email", email != null ? email : "");
        props.put("phone", phone != null ? phone : "");
        props.put("extra", extra != null ? extra : "");
        trackEvent("SET_USER_INFO", null, props);
    }

    public void setSessionProperty(String key, String value) {
        if (key != null && value != null) {
            sessionProperties.put(key, value);
        }
    }

    public void trackError(Throwable throwable, P5ErrorSeverity severity) {
        if (throwable == null) {
            return;
        }

        Map<String, String> props = new HashMap<>();
        props.put("severity", severity == null ? "UNKNOWN" : severity.name());
        props.put(
                "message",
                throwable.getMessage() == null ? "" : throwable.getMessage()
        );

        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        props.put("stacktrace", sw.toString());

        trackEvent("HANDLED_ERROR", null, props);
    }

    public void startTimer(String label) {
        if (label != null) {
            timers.put(label, System.currentTimeMillis());
        }
    }

    public void stopTimer(String label) {
        if (label == null) {
            return;
        }

        Long startTime = timers.remove(label);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            Map<String, String> props = new HashMap<>();
            props.put("label", label);
            props.put("duration_ms", String.valueOf(duration));
            trackEvent("PERFORMANCE_TIMER", null, props);
        }
    }

    private void setupCrashReporting() {
        SdkLogger.debug("Setting up crash reporting...");

        previousCrashHandler = Thread.getDefaultUncaughtExceptionHandler();

        sdkCrashHandler = (thread, throwable) -> {
            try {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));

                Map<String, String> props = new HashMap<>();
                props.put("stacktrace", sw.toString());
                props.put("thread", thread.getName());

                synchronized (breadcrumbs) {
                    props.put("breadcrumbs", breadcrumbs.toString());
                }

                SdkLogger.error(
                        "CRITICAL: Application crashed. Logging event..."
                );
                trackEventSync("APP_CRASH", null, props);
            } catch (Exception e) {
                SdkLogger.error("Failed to log crash event", e);
            } finally {
                Thread.UncaughtExceptionHandler old = previousCrashHandler;
                if (old != null && old != sdkCrashHandler) {
                    old.uncaughtException(thread, throwable);
                }
            }
        };

        Thread.setDefaultUncaughtExceptionHandler(sdkCrashHandler);
    }

    public boolean isForegroundLocationTrackingAllowed() {
        if (!initialized || config == null
                || !config.isEnabled()
                || !config.isLocationTrackingEnabled()
                || databaseExecutor.isShutdown()) {
            return false;
        }

        SdkPreferences currentPrefs = new SdkPreferences(context);
        return currentPrefs.isLocationTrackingEnabled()
                && currentPrefs.isEnabled()
                && currentPrefs.isConsentGranted();
    }

    public void trackEvent(@NonNull String eventName) {
        trackEvent(eventName, null);
    }

    public void logEvent(@NonNull String eventName, String screenName) {
        trackEvent(eventName, screenName);
    }

    public void P5Eventdata(
            Context context,
            String eventName,
            String key,
            String value) {
        if (key == null) {
            return;
        }

        Map<String, String> props = new HashMap<>();
        props.put(key, value == null ? "" : value);
        trackEvent(eventName, null, props);
    }

    public void trackEvent(@NonNull String eventName, String screenName) {
        trackEvent(eventName, screenName, null);
    }

    public void trackEvent(
            @NonNull String eventName,
            String screenName,
            Map<String, String> properties) {
        trackEvent(eventName, screenName, properties, null);
    }

    public void trackEvent(
            @NonNull String eventName,
            String screenName,
            Map<String, String> properties,
            String payload) {

        if (!initialized || config == null || !config.isEnabled()
                || databaseExecutor.isShutdown()) {
            return;
        }

        SdkPreferences prefs = new SdkPreferences(context);
        if (!prefs.isConsentGranted() && !"CONSENT_UPDATE".equals(eventName)) {
            return;
        }

        final String finalEventName = eventName.trim();
        if (finalEventName.isEmpty()) {
            return;
        }

        final String finalScreenName =
                screenName == null ? null : screenName.trim();

        final Map<String, String> eventProperties =
                properties == null ? null : new HashMap<>(properties);

        final Map<String, String> eventSessionProperties =
                new HashMap<>(sessionProperties);

        final String eventUserId = userId;
        final String eventSessionId = sessionId;
        final MonitoringConfig eventConfig = config;

        final long sequence = sequenceCounter.incrementAndGet();
        final int battery = getBatteryLevel();
        final String network = getNetworkType();
        final String orientation = getOrientation();

        try {
            databaseExecutor.execute(() -> {
                addBreadcrumb(finalEventName);
                storeEvent(
                        finalEventName,
                        finalScreenName,
                        eventProperties,
                        payload,
                        sequence,
                        battery,
                        network,
                        orientation,
                        eventUserId,
                        eventSessionId,
                        eventSessionProperties,
                        eventConfig
                );
            });

            campaignManager.evaluateEvent(
                    finalEventName,
                    eventProperties == null
                            ? null : new HashMap<>(eventProperties)
            );
        } catch (RejectedExecutionException e) {
            SdkLogger.error(
                    "Plumb5Monitor executor rejected event: " + finalEventName,
                    e
            );
        }
    }

    private void trackEventSync(
            String eventName,
            String screenName,
            Map<String, String> properties) {

        storeEvent(
                eventName,
                screenName,
                properties == null ? null : new HashMap<>(properties),
                null,
                sequenceCounter.incrementAndGet(),
                getBatteryLevel(),
                getNetworkType(),
                getOrientation(),
                userId,
                sessionId,
                new HashMap<>(sessionProperties),
                config
        );
    }

    private void addBreadcrumb(String eventName) {
        synchronized (breadcrumbs) {
            if (breadcrumbs.size() >= MAX_BREADCRUMBS) {
                breadcrumbs.removeFirst();
            }
            breadcrumbs.add(eventName);
        }
    }

    private void storeEvent(
            String eventName,
            String screenName,
            Map<String, String> properties,
            String payload,
            long seq,
            int battery,
            String network,
            String orientation,
            String eventUserId,
            String eventSessionId,
            Map<String, String> eventSessionProperties,
            MonitoringConfig eventConfig) {

        try {
            MonitoringDatabase database = MonitoringDatabase.getInstance(context);

            int maxDatabaseSize = eventConfig.getMaxDatabaseSize();
            int eventCount = database.eventDao().getEventCount();

            if (maxDatabaseSize > 0 && eventCount >= maxDatabaseSize) {
                int recordsToDelete =
                        Math.min(100, eventCount - maxDatabaseSize + 1);

                if (recordsToDelete > 0) {
                    database.eventDao().deleteOldest(recordsToDelete);
                }
            }

            EventEntity event = new EventEntity();
            event.setEventId(UUID.randomUUID().toString());
            event.setAppId(eventConfig.getAppId());
            event.setSessionId(eventSessionId);
            event.setUserId(eventUserId);
            event.setSequenceNumber(seq);
            event.setEventName(eventName);
            event.setScreenName(screenName);
            event.setDeviceModel(Build.MODEL);
            event.setAndroidVersion(Build.VERSION.RELEASE);
            event.setSdkVersion(SDK_VERSION);
            event.setTimestamp(System.currentTimeMillis());
            event.setBatteryLevel(battery);
            event.setNetworkType(network);
            event.setOrientation(orientation);
            event.setPayload(payload);

            Map<String, String> contextProps = new HashMap<>();
            contextProps.put("ram_available", getAvailableMemory());
            contextProps.put("storage_available", getAvailableInternalStorage());
            contextProps.put(
                    "cpu_cores",
                    String.valueOf(Runtime.getRuntime().availableProcessors())
            );
            contextProps.putAll(P5DeviceInfo.getAdvancedInfo(context));
            contextProps.putAll(eventSessionProperties);

            if (properties != null) {
                contextProps.putAll(properties);
            }

            if (!contextProps.isEmpty()) {
                event.setProperties(
                        new com.google.gson.Gson().toJson(contextProps)
                );
            }

            database.eventDao().insertEvent(event);
            SdkLogger.debug("Event stored: " + eventName);

            int updatedCount = database.eventDao().getEventCount();
            boolean forceImmediate =
                    eventConfig.isDevelopmentMode() || updatedCount >= 100;

            if (forceImmediate
                    || connectionDetector.canPerformImmediateUpload()) {
                UploadScheduler.scheduleImmediateUpload(context, eventConfig);
            } else {
                UploadScheduler.scheduleUpload(context, eventConfig);
            }
        } catch (Exception e) {
            SdkLogger.error("Failed to store event: " + eventName, e);
        }
    }

    public void P5Transdata(Context context, JSONArray transData) {
        if (transData == null || !initialized
                || config == null || !config.isEnabled()) {
            return;
        }

        transactionManager.setSessionId(sessionId);
        transactionManager.setUserId(userId);
        transactionManager.trackTransaction(transData);
    }

    public void P5PostStaticFormData(
            Context context,
            String formId,
            JSONArray dataList) {
        if (dataList == null) {
            return;
        }

        Map<String, String> props = new HashMap<>();
        props.put("form_id", formId == null ? "" : formId);
        trackEvent("FORM_SUBMISSION", null, props, dataList.toString());
    }

    private int getBatteryLevel() {
        try {
            Intent intent = context.registerReceiver(
                    null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            );

            if (intent != null) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                if (level >= 0 && scale > 0) {
                    return (int) ((level / (float) scale) * 100);
                }
            }
        } catch (Exception ignored) {
            // Best-effort device metadata.
        }

        return -1;
    }

    @SuppressWarnings("deprecation")
    private String getNetworkType() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

                if (activeNetwork != null && activeNetwork.isConnected()) {
                    if (activeNetwork.getType()
                            == ConnectivityManager.TYPE_WIFI) {
                        return "WIFI";
                    }

                    if (activeNetwork.getType()
                            == ConnectivityManager.TYPE_MOBILE) {
                        return "CELLULAR";
                    }

                    return "OTHER";
                }
            }
        } catch (Exception ignored) {
            // Best-effort device metadata.
        }

        return "UNKNOWN";
    }

    private String getOrientation() {
        int orientation =
                context.getResources().getConfiguration().orientation;

        return orientation == Configuration.ORIENTATION_LANDSCAPE
                ? "LANDSCAPE" : "PORTRAIT";
    }

    private String getAvailableMemory() {
        try {
            ActivityManager.MemoryInfo mi =
                    new ActivityManager.MemoryInfo();

            ActivityManager activityManager = (ActivityManager)
                    context.getSystemService(Context.ACTIVITY_SERVICE);

            if (activityManager != null) {
                activityManager.getMemoryInfo(mi);
                return (mi.availMem / 1048576L) + "MB";
            }
        } catch (Exception ignored) {
            // Best-effort device metadata.
        }

        return "UNKNOWN";
    }

    private String getAvailableInternalStorage() {
        try {
            StatFs stat =
                    new StatFs(Environment.getDataDirectory().getPath());

            long bytesAvailable =
                    stat.getAvailableBlocksLong() * stat.getBlockSizeLong();

            return (bytesAvailable / 1048576L) + "MB";
        } catch (Exception ignored) {
            // Best-effort device metadata.
        }

        return "UNKNOWN";
    }

    public void trackAppStarted() {
        trackEvent("APP_STARTED");
    }

    public void trackAppForeground(String screenName) {
        trackEvent("APP_FOREGROUND", screenName);
    }

    public void trackAppBackground() {
        trackEvent("APP_BACKGROUND");
    }

    public void trackScreenOpened(String screenName) {
        trackEvent("SCREEN_OPENED", screenName);
    }

    public void trackScreenClosed(String screenName) {
        trackEvent("SCREEN_CLOSED", screenName);
    }

    public void trackScreenResumed(String screenName) {
        trackEvent("SCREEN_RESUMED", screenName);
    }

    public void trackNumberButtonClick(String number) {
        if (number == null || number.trim().isEmpty()) {
            return;
        }

        Map<String, String> props = new HashMap<>();
        props.put("button", number.trim());
        trackEvent("NUMBER_BUTTON_CLICK", null, props);
    }

    public void trackOperatorClick(String operator) {
        if (operator == null || operator.trim().isEmpty()) {
            return;
        }

        Map<String, String> props = new HashMap<>();
        props.put("operator", operator.trim());
        trackEvent("OPERATOR_CLICK", null, props);
    }

    public void trackCalculateResult() {
        trackEvent("CALCULATE_RESULT");
    }

    public void trackCalculationCompleted(
            double firstNumber,
            String operator,
            double secondNumber,
            double result) {

        Map<String, String> props = new HashMap<>();
        props.put("first_number", String.valueOf(firstNumber));
        props.put("second_number", String.valueOf(secondNumber));
        props.put("operator", operator == null ? "" : operator);
        props.put("result", String.valueOf(result));

        trackEvent("CALCULATION_COMPLETED", null, props);
    }

    public void trackClearCalculator() {
        trackEvent("CLEAR_CALCULATOR");
    }

    public void checkServerHealth(@NonNull HealthCheckCallback callback) {
        if (!initialized || config == null || !config.isEnabled()
                || databaseExecutor.isShutdown()) {
            deliverHealthResult(callback, false, 0, "SDK unavailable");
            return;
        }

        try {
            databaseExecutor.execute(() -> {
                try {
                    MonitoringConfig healthConfig = config;

                    ApiClient apiClient = new ApiClient(
                            context,
                            healthConfig.getServerUrl(),
                            healthConfig.getApiKey(),
                            healthConfig.getLicenseServerUrl()
                    );

                    ServerHealthResult result =
                            apiClient.checkServerHealth();

                    deliverHealthResult(
                            callback,
                            result.isHealthy(),
                            result.getStatusCode(),
                            result.getMessage()
                    );
                } catch (Exception e) {
                    deliverHealthResult(
                            callback,
                            false,
                            0,
                            e.getMessage() == null
                                    ? "Health check failed" : e.getMessage()
                    );
                }
            });
        } catch (RejectedExecutionException e) {
            deliverHealthResult(
                    callback,
                    false,
                    0,
                    "SDK is shutting down"
            );
        }
    }

    private void deliverHealthResult(
            @NonNull HealthCheckCallback callback,
            boolean reachable,
            int httpCode,
            String message) {

        new Handler(Looper.getMainLooper()).post(
                () -> callback.onResult(reachable, httpCode, message)
        );
    }

    public void uploadNow() {
        if (!initialized || config == null || !config.isEnabled()) {
            return;
        }

        UploadScheduler.scheduleImmediateUpload(context, config);
        UploadScheduler.scheduleImmediateTransactionUpload(context, config);
    }

    public void showChat(Context context) {
        if (context instanceof FragmentActivity) {
            FragmentActivity activity = (FragmentActivity) context;
            String chatUrl =
                    config.getServerUrl() + "/chat/" + config.getAppId();

            P5ChatEngine.newInstance(chatUrl).show(
                    activity.getSupportFragmentManager(),
                    "p5_chat"
            );
        }
    }

    public void showDebugScreen(Context context) {
        if (!initialized) {
            return;
        }

        Intent intent = new Intent(
                context,
                com.plumb5.monitor.ui.P5DebugActivity.class
        );

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public void setConsent(boolean granted) {
        new SdkPreferences(context).setConsentGranted(granted);
        trackEvent(
                "CONSENT_UPDATE",
                null,
                null,
                String.valueOf(granted)
        );
    }

    public void wipeData() {
        try {
            databaseExecutor.execute(() -> {
                try {
                    MonitoringDatabase database =
                            MonitoringDatabase.getInstance(context);

                    database.eventDao().deleteAll();
                    database.transactionDao().deleteAll();
                    new SdkPreferences(context).clear();

                    userId = null;
                    userName = null;
                    userEmail = null;
                    userPhone = null;

                    sessionProperties.clear();
                    timers.clear();

                    SdkLogger.debug("All local data wiped");
                } catch (Exception e) {
                    SdkLogger.error("Failed to wipe data", e);
                }
            });
        } catch (RejectedExecutionException e) {
            SdkLogger.error(
                    "Cannot wipe data: SDK is shutting down",
                    e
            );
        }
    }

    private void pruneOldEvents() {
        try {
            databaseExecutor.execute(() -> {
                try {
                    long threshold = System.currentTimeMillis()
                            - TimeUnit.DAYS.toMillis(30);

                    MonitoringDatabase database =
                            MonitoringDatabase.getInstance(context);

                    database.eventDao().pruneOldEvents(threshold);
                    database.transactionDao().pruneOldTransactions(threshold);

                    SdkLogger.debug(
                            "Old monitoring data pruned (TTL: 30 days)"
                    );
                } catch (Exception e) {
                    SdkLogger.error("Failed to prune old monitoring data", e);
                }
            });
        } catch (RejectedExecutionException e) {
            SdkLogger.error("Unable to schedule data pruning", e);
        }
    }

    private void syncRemoteConfig() {
        if (!initialized || databaseExecutor.isShutdown()) {
            return;
        }

        try {
            databaseExecutor.execute(() -> {
                try {
                    MonitoringConfig startingConfig = config;

                    ApiClient client = new ApiClient(
                            context,
                            startingConfig.getServerUrl(),
                            startingConfig.getApiKey(),
                            startingConfig.getLicenseServerUrl()
                    );

                    String json = client.fetchRemoteConfig();

                    if (json == null || json.trim().isEmpty()) {
                        SdkLogger.debug(
                                "Remote config: Empty response; keeping existing configuration"
                        );
                        return;
                    }

                    JSONObject remote = new JSONObject(json);
                    SdkPreferences prefs = new SdkPreferences(context);

                    String beaconKey = remote.has("beaconFilters")
                            ? "beaconFilters" : "beacons";
                    if (remote.has(beaconKey)) {
                        JSONArray beacons = remote.optJSONArray(beaconKey);

                        if (beacons != null) {
                            prefs.saveBeaconFilters(beacons.toString());

                            SdkLogger.debug(
                                    "Remote config: Saved "
                                            + beacons.length()
                                            + " beacon filters"
                            );
                        } else {
                            SdkLogger.error(
                                    "Remote config: 'beacons' exists but is not a JSON array"
                            );
                        }
                    }

                    MonitoringConfig.Builder builder = config.toBuilder();
                    boolean changed = false;

                    if (remote.has("enabled")) {
                        builder.enabled(remote.getBoolean("enabled"));
                        changed = true;
                    }

                    if (remote.has("maxDatabaseSize")) {
                        int maxDatabaseSize =
                                remote.getInt("maxDatabaseSize");

                        if (maxDatabaseSize > 0) {
                            builder.maxDatabaseSize(maxDatabaseSize);
                            changed = true;
                        }
                    }

                    if (remote.has("debug")) {
                        builder.debugLogging(remote.getBoolean("debug"));
                        changed = true;
                    }

                    if (remote.has("beaconEnabled")) {
                        builder.beaconEnabled(
                                remote.getBoolean("beaconEnabled")
                        );
                        changed = true;
                    }

                    if (remote.has("geofenceEnabled")) {
                        builder.geofenceEnabled(
                                remote.getBoolean("geofenceEnabled")
                        );
                        changed = true;
                    }

                    JSONObject settings = remote.optJSONObject("sdkSettings");

                    if (settings != null) {
                        if (settings.has("batchSize")) {
                            int batchSize = settings.getInt("batchSize");

                            if (batchSize > 0) {
                                builder.batchSize(batchSize);
                                changed = true;
                            }
                        }

                        if (settings.has("syncIntervalMinutes")) {
                            int interval =
                                    settings.getInt("syncIntervalMinutes");

                            interval = Math.max(15, Math.min(1440, interval));

                            builder.syncIntervalMinutes(interval);
                            changed = true;
                        }
                    }

                    if (changed) {
                        MonitoringConfig updatedConfig = builder.build();
                        config = updatedConfig;

                        prefs.saveConfig(updatedConfig);

                        SdkLogger.initialize(
                                updatedConfig.isDebugLoggingEnabled()
                        );

                        UploadScheduler.scheduleConfigSync(
                                context,
                                updatedConfig.getSyncIntervalMinutes()
                        );

                        SdkLogger.debug(
                                "Remote configuration applied successfully"
                        );
                    }

                    // Fixed: schedule or cancel scanning based on the latest
                    // effective configuration, even if only filters changed.
                    UploadScheduler.scheduleBeaconScan(context, config);

                } catch (Exception e) {
                    SdkLogger.error("Failed to sync remote config", e);
                }
            });
        } catch (RejectedExecutionException e) {
            SdkLogger.error(
                    "Remote config sync rejected: SDK is shutting down",
                    e
            );
        }
    }

    public synchronized void shutdown() {
        if (!initialized) {
            return;
        }

        initialized = false;
        transactionManager.shutdown();

        if (Thread.getDefaultUncaughtExceptionHandler() == sdkCrashHandler) {
            Thread.setDefaultUncaughtExceptionHandler(previousCrashHandler);
        }

        databaseExecutor.shutdown();
        INSTANCE = null;
    }

    private void printInitializationLog() {
        SdkLogger.debug("=================================");
        SdkLogger.debug("Plumb5 Monitoring SDK v" + SDK_VERSION);
        SdkLogger.debug("App ID: " + config.getAppId());
        SdkLogger.debug("Session ID: " + sessionId);
        SdkLogger.debug("Effective SDK enabled: " + config.isEnabled());
        SdkLogger.debug("Effective beacon enabled: " + config.isBeaconEnabled());
        SdkLogger.debug("Effective geofence enabled: " + config.isGeofenceEnabled());
        SdkLogger.debug("=================================");
    }
}
