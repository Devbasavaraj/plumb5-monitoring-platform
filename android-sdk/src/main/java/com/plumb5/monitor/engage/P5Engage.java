package com.plumb5.monitor.engage;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.plumb5.monitor.MonitoringConfig;
import com.plumb5.monitor.Plumb5Monitor;
import com.plumb5.monitor.core.SdkLogger;
import com.plumb5.monitor.core.SdkPreferences;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Shared helpers for the engagement features (push registration, in-app
 * messaging, reminders, geofence restore). Everything here is additive: it
 * reads the configuration the existing SDK already saved and never changes it.
 */
public final class P5Engage {

    private static final String TAG = "P5Engage";
    private static final String API_KEY_HEADER = "X-Monitoring-Api-Key";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

    private P5Engage() {
    }

    /** Single background thread for all engagement network and disk work. */
    public static ExecutorService io() {
        return IO;
    }

    public static void runOnMain(@NonNull Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            MAIN.post(r);
        }
    }

    public static OkHttpClient http() {
        return HTTP;
    }

    /**
     * Makes sure {@link Plumb5Monitor} exists. Receivers (geofence, boot) can run
     * in a fresh process where the host app never called p5Init, so the SDK is
     * rebuilt from the configuration saved by the last p5Init.
     *
     * @return true when the SDK is usable
     */
    public static boolean ensureInitialized(@Nullable Context context) {
        if (context == null) {
            return false;
        }
        try {
            Plumb5Monitor.getInstance();
            return true;
        } catch (IllegalStateException notReady) {
            // fall through and try to restore
        }
        try {
            Context app = context.getApplicationContext();
            SdkPreferences prefs = new SdkPreferences(app);
            if (!prefs.isConfigured()) {
                return false;
            }
            String serverUrl = prefs.getServerUrl();
            if (serverUrl == null || serverUrl.trim().isEmpty()) {
                return false;
            }
            serverUrl = serverUrl.trim();
            MonitoringConfig config = new MonitoringConfig.Builder(prefs.getAppId(), serverUrl)
                    .apiKey(prefs.getApiKey())
                    .debugLogging(prefs.isDebugLoggingEnabled())
                    .batchSize(prefs.getBatchSize())
                    .maxRetries(prefs.getMaxRetries())
                    .enforceHttps(!isLocalServer(serverUrl))
                    .locationTrackingEnabled(prefs.isLocationTrackingEnabled())
                    .build();
            Plumb5Monitor.initialize(app, config);
            Plumb5Monitor.getInstance();
            return true;
        } catch (Exception e) {
            SdkLogger.error(TAG + ": could not restore SDK from saved config", e);
            return false;
        }
    }

    static boolean isLocalServer(String url) {
        String u = url.toLowerCase();
        return u.startsWith("http://10.0.2.2")
                || u.startsWith("http://localhost")
                || u.startsWith("http://127.0.0.1");
    }

    @Nullable
    public static String baseUrl(Context context) {
        String url = new SdkPreferences(context.getApplicationContext()).getServerUrl();
        if (url == null) {
            return null;
        }
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url.isEmpty() ? null : url;
    }

    @Nullable
    public static String apiKey(Context context) {
        return new SdkPreferences(context.getApplicationContext()).getApiKey();
    }

    @Nullable
    public static String appId(Context context) {
        return new SdkPreferences(context.getApplicationContext()).getAppId();
    }

    /** GET {server}{path}. Returns the body, or null on any failure. Blocking. */
    @Nullable
    public static String get(Context context, String path) {
        String base = baseUrl(context);
        String key = apiKey(context);
        if (base == null || key == null) {
            return null;
        }
        Request request = new Request.Builder()
                .url(base + path)
                .header(API_KEY_HEADER, key)
                .get()
                .build();
        try (Response response = HTTP.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                SdkLogger.debug(TAG + ": GET " + path + " -> HTTP " + response.code());
                return null;
            }
            return response.body().string();
        } catch (IOException e) {
            SdkLogger.debug(TAG + ": GET " + path + " failed: " + e.getMessage());
            return null;
        }
    }

    /** POST JSON to {server}{path}. Returns true for a 2xx answer. Blocking. */
    public static boolean postJson(Context context, String path, String json) {
        String base = baseUrl(context);
        String key = apiKey(context);
        if (base == null || key == null) {
            return false;
        }
        Request request = new Request.Builder()
                .url(base + path)
                .header(API_KEY_HEADER, key)
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response response = HTTP.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                SdkLogger.debug(TAG + ": POST " + path + " -> HTTP " + response.code());
            }
            return response.isSuccessful();
        } catch (IOException e) {
            SdkLogger.debug(TAG + ": POST " + path + " failed: " + e.getMessage());
            return false;
        }
    }

    /** Records an event through the existing pipeline; never throws. */
    public static void track(String eventName, @Nullable Map<String, String> props) {
        track(eventName, props, null);
    }

    public static void track(String eventName, @Nullable Map<String, String> props, @Nullable String payload) {
        try {
            Plumb5Monitor.getInstance().trackEvent(eventName, null, props, payload);
        } catch (Exception ignored) {
            // SDK not initialised or shut down: engagement events are best-effort
        }
    }

    /** Parses "k=v,k2=v2" pairs into extras-like map entries. */
    public static java.util.Map<String, String> parsePairs(@Nullable String raw) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        for (String part : raw.split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                out.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
            }
        }
        return out;
    }
}
