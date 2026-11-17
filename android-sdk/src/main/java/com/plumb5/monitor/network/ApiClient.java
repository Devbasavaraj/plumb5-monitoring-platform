package com.plumb5.monitor.network;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.plumb5.monitor.core.SdkLogger;
import com.plumb5.monitor.core.SdkPreferences;
import com.plumb5.monitor.database.EventEntity;
import com.plumb5.monitor.database.TransactionEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Central network client for Plumb5 Monitoring SDK.
 *
 * Handles:
 *
 * 1. License validation
 * 2. Event upload
 * 3. Transaction upload
 * 4. Usage reporting
 * 5. Remote configuration
 * 6. Server health
 *
 * All network operations are synchronous because they are called
 * from WorkManager/background executors.
 */
public class ApiClient {

    // ============================================================
    // CONSTANTS
    // ============================================================

    private static final String TAG =
            "ApiClient";

    private static final String LICENSE_VALIDATE_PATH =
            "/v1/license/validate";

    private static final String EVENT_BATCH_PATH =
            "/api/monitoring/events/batch";

    private static final String EVENT_FALLBACK_PATH =
            "/api/usage/event";

    private static final String TRANSACTION_PATH =
            "/api/usage/transaction";

    private static final String REMOTE_CONFIG_PATH =
            "/api/config/sync";

    private static final String HEALTH_PATH =
            "/health";

    private static final String API_KEY_HEADER =
            "X-Monitoring-Api-Key";

    private static final String JSON_MEDIA_TYPE =
            "application/json; charset=utf-8";

    private static final String LICENSE_CACHE_NAME =
            "p5_monitor_cache";

    private static final String KEY_LAST_LICENSE_CHECK =
            "last_license_check";

    private static final String KEY_LICENSE_STATUS =
            "license_status";

    private static final String KEY_LICENSE_RESPONSE =
            "license_response";

    private static final String PRODUCTION_LICENSE_SERVER =
            "https://api.plumb5.com";

    private static final long CONNECT_TIMEOUT_SECONDS =
            15;

    private static final long READ_TIMEOUT_SECONDS =
            30;

    private static final long WRITE_TIMEOUT_SECONDS =
            30;

    // ============================================================
    // FIELDS
    // ============================================================

    private final Context context;

    private final String serverUrl;

    private final String apiKey;

    private final String licenseServerUrl;

    private final OkHttpClient httpClient;

    private final Gson gson;

    // ============================================================
    // CONSTRUCTORS
    // ============================================================

    public ApiClient(
            @NonNull Context context,
            @NonNull String serverUrl,
            @NonNull String apiKey,
            String licenseServerUrl) {

        this.context =
                context.getApplicationContext();

        this.serverUrl =
                normalizeBaseUrl(serverUrl);

        this.apiKey =
                apiKey.trim();

        if (licenseServerUrl == null
                || licenseServerUrl.trim().isEmpty()) {

            this.licenseServerUrl =
                    this.serverUrl;

        } else {

            this.licenseServerUrl =
                    normalizeBaseUrl(
                            licenseServerUrl
                    );
        }

        this.httpClient =
                new OkHttpClient.Builder()
                        .connectTimeout(
                                CONNECT_TIMEOUT_SECONDS,
                                TimeUnit.SECONDS
                        )
                        .readTimeout(
                                READ_TIMEOUT_SECONDS,
                                TimeUnit.SECONDS
                        )
                        .writeTimeout(
                                WRITE_TIMEOUT_SECONDS,
                                TimeUnit.SECONDS
                        )
                        .build();

        this.gson =
                new Gson();
    }

    /**
     * Backward-compatible constructor.
     */
    public ApiClient(
            @NonNull Context context,
            @NonNull String serverUrl,
            @NonNull String apiKey) {

        this(
                context,
                serverUrl,
                apiKey,
                serverUrl
        );
    }

    // ============================================================
    // LICENSE VALIDATION
    // ============================================================

    /**
     * Validates the API key against the license server.
     *
     * Actual backend contract:
     *
     * POST /v1/license/validate
     *
     * Header:
     * X-Monitoring-Api-Key: <apiKey>
     *
     * Response:
     * {"status":"valid"}
     *
     * or:
     *
     * {"status":"invalid"}
     *
     * Grace period is used only when the SDK previously received
     * a valid license and the license server is temporarily
     * unreachable.
     */
    public boolean validateLicense(
            int gracePeriodMinutes) {

        if (apiKey.isEmpty()) {

            SdkLogger.error(
                    TAG + ": API key is empty"
            );

            return false;
        }

        String url =
                buildUrl(
                        licenseServerUrl,
                        LICENSE_VALIDATE_PATH
                );

        SdkLogger.debug(
                TAG + ": Validating license at "
                        + url
        );

        Request request =
                new Request.Builder()
                        .url(url)
                        .post(
                                RequestBody.create(
                                        "",
                                        MediaType.parse(
                                                JSON_MEDIA_TYPE
                                        )
                                )
                        )
                        .header(
                                API_KEY_HEADER,
                                apiKey
                        )
                        .header(
                                "Accept",
                                "application/json"
                        )
                        .build();

        try {

            Response response =
                    httpClient.newCall(
                            request
                    ).execute();

            String responseBody =
                    response.body() != null
                            ? response.body().string()
                            : "";

            int statusCode =
                    response.code();

            boolean successful =
                    response.isSuccessful();

            response.close();

            SdkLogger.debug(
                    TAG
                            + ": License response HTTP "
                            + statusCode
                            + " body="
                            + responseBody
            );

            if (!successful) {

                SdkLogger.error(
                        TAG
                                + ": License server returned HTTP "
                                + statusCode
                );

                return handleLicenseFailure(
                        gracePeriodMinutes
                );
            }

            boolean valid =
                    parseLicenseResponse(
                            responseBody
                    );

            if (valid) {

                saveLicenseSuccess(
                        responseBody
                );

                return true;
            }

            saveLicenseInvalid(
                    responseBody
            );

            return false;

        } catch (Exception e) {

            SdkLogger.error(
                    TAG
                            + ": License validation request failed",
                    e
            );

            return handleLicenseFailure(
                    gracePeriodMinutes
            );
        }
    }

    /**
     * Parses the exact backend response:
     *
     * {"status":"valid"}
     */
    private boolean parseLicenseResponse(
            String responseBody) {

        if (responseBody == null
                || responseBody.trim().isEmpty()) {

            return false;
        }

        try {

            Map<?, ?> response =
                    gson.fromJson(
                            responseBody,
                            Map.class
                    );

            if (response == null) {
                return false;
            }

            Object status =
                    response.get("status");

            if (status == null) {
                return false;
            }

            return "valid".equalsIgnoreCase(
                    String.valueOf(status).trim()
            );

        } catch (Exception e) {

            SdkLogger.error(
                    TAG
                            + ": Failed to parse license response",
                    e
            );

            return false;
        }
    }

    /**
     * Handles temporary license-server failure.
     *
     * If a previous successful license validation exists,
     * the SDK can continue during the configured grace period.
     */
    private boolean handleLicenseFailure(
            int gracePeriodMinutes) {

        if (gracePeriodMinutes <= 0) {
            return false;
        }

        SharedPreferences preferences =
                getLicensePreferences();

        boolean previousValid =
                preferences.getBoolean(
                        KEY_LICENSE_STATUS,
                        false
                );

        if (!previousValid) {

            SdkLogger.error(
                    TAG
                            + ": No previous valid license found"
            );

            return false;
        }

        long lastCheck =
                preferences.getLong(
                        KEY_LAST_LICENSE_CHECK,
                        0L
                );

        if (lastCheck <= 0L) {

            return false;
        }

        long elapsed =
                System.currentTimeMillis()
                        - lastCheck;

        long gracePeriodMillis =
                TimeUnit.MINUTES.toMillis(
                        gracePeriodMinutes
                );

        if (elapsed <= gracePeriodMillis) {

            long remaining =
                    gracePeriodMillis
                            - elapsed;

            long remainingMinutes =
                    TimeUnit.MILLISECONDS.toMinutes(
                            remaining
                    );

            SdkLogger.debug(
                    TAG
                            + ": Using license grace period. "
                            + "Remaining minutes="
                            + remainingMinutes
            );

            return true;
        }

        SdkLogger.error(
                TAG
                        + ": License grace period expired"
        );

        return false;
    }

    private void saveLicenseSuccess(
            String responseBody) {

        getLicensePreferences()
                .edit()
                .putBoolean(
                        KEY_LICENSE_STATUS,
                        true
                )
                .putLong(
                        KEY_LAST_LICENSE_CHECK,
                        System.currentTimeMillis()
                )
                .putString(
                        KEY_LICENSE_RESPONSE,
                        responseBody
                )
                .apply();

        SdkLogger.debug(
                TAG + ": License marked valid"
        );
    }

    private void saveLicenseInvalid(
            String responseBody) {

        getLicensePreferences()
                .edit()
                .putBoolean(
                        KEY_LICENSE_STATUS,
                        false
                )
                .putString(
                        KEY_LICENSE_RESPONSE,
                        responseBody
                )
                .apply();

        SdkLogger.error(
                TAG + ": License marked invalid"
        );
    }

    private SharedPreferences getLicensePreferences() {

        return context.getSharedPreferences(
                LICENSE_CACHE_NAME,
                Context.MODE_PRIVATE
        );
    }

    // ============================================================
    // EVENT UPLOAD
    // ============================================================

    /**
     * Uploads a batch of events.
     *
     * Primary endpoint:
     *
     * POST /api/monitoring/events/batch
     *
     * Body:
     * JSON array containing EventEntity objects.
     */
    public boolean uploadEvents(
            List<EventEntity> events) {

        if (events == null
                || events.isEmpty()) {

            return true;
        }

        String url =
                buildUrl(
                        serverUrl,
                        EVENT_BATCH_PATH
                );

        String json =
                gson.toJson(events);

        SdkLogger.debug(
                TAG
                        + ": Uploading "
                        + events.size()
                        + " events"
        );

        try {

            Response response =
                    executeJsonPost(
                            url,
                            json,
                            true
                    );

            int statusCode =
                    response.code();

            String responseBody =
                    readResponseBody(
                            response
                    );

            boolean success =
                    response.isSuccessful();

            response.close();

            SdkLogger.debug(
                    TAG
                            + ": Event batch response HTTP "
                            + statusCode
                            + " body="
                            + responseBody
            );

            if (success) {

                return true;
            }

            SdkLogger.error(
                    TAG
                            + ": Batch event upload failed. "
                            + "Trying fallback endpoint."
            );

        } catch (Exception e) {

            SdkLogger.error(
                    TAG
                            + ": Batch event upload exception",
                    e
            );
        }

        /*
         * Fallback to individual usage events.
         *
         * This preserves compatibility with older Plumb5
         * monitoring servers.
         */
        return uploadEventsFallback(
                events
        );
    }

    /**
     * Fallback event upload.
     *
     * POST /api/usage/event
     *
     * Each EventEntity is sent as JSON.
     */
    private boolean uploadEventsFallback(
            List<EventEntity> events) {

        String url =
                buildUrl(
                        serverUrl,
                        EVENT_FALLBACK_PATH
                );

        for (EventEntity event : events) {

            try {

                String json =
                        gson.toJson(event);

                Response response =
                        executeJsonPost(
                                url,
                                json,
                                true
                        );

                int statusCode =
                        response.code();

                String responseBody =
                        readResponseBody(
                                response
                        );

                boolean success =
                        response.isSuccessful();

                response.close();

                if (!success) {

                    SdkLogger.error(
                            TAG
                                    + ": Fallback event upload failed. "
                                    + "HTTP="
                                    + statusCode
                                    + " body="
                                    + responseBody
                    );

                    return false;
                }

            } catch (Exception e) {

                SdkLogger.error(
                        TAG
                                + ": Fallback event upload exception",
                        e
                );

                return false;
            }
        }

        SdkLogger.debug(
                TAG
                        + ": Fallback event upload successful. "
                        + "Count="
                        + events.size()
        );

        return true;
    }

    // ============================================================
    // TRANSACTION UPLOAD
    // ============================================================

    /**
     * Uploads transaction records.
     *
     * Endpoint:
     *
     * POST /api/usage/transaction
     *
     * Body:
     * JSON array of TransactionEntity objects.
     */
    public boolean uploadTransactions(
            List<TransactionEntity> transactions) {

        if (transactions == null || transactions.isEmpty()) {
            return true;
        }

        String url =
                buildUrl(
                        licenseServerUrl,
                        TRANSACTION_PATH
                );
        /*
         * IMPORTANT: the Spring Boot backend expects the request body
         * to be a raw JSON array: List<Map<String,Object>>.
         * Do not send a wrapper object and do not send Room's internal
         * database id/retryCount fields.
         */
        List<Map<String, Object>> payload =
                new ArrayList<>();

        for (TransactionEntity transaction : transactions) {

            Map<String, Object> item =
                    new HashMap<>();

            item.put(
                    "transactionId",
                    transaction.getTransactionId()
            );

            item.put(
                    "appId",
                    transaction.getAppId()
            );

            item.put(
                    "sessionId",
                    transaction.getSessionId()
            );

            if (transaction.getUserId() != null) {
                item.put(
                        "userId",
                        transaction.getUserId()
                );
            }

            item.put(
                    "timestamp",
                    transaction.getTimestamp()
            );

            String transactionData =
                    transaction.getTransactionData();

            if (transactionData == null
                    || transactionData.trim().isEmpty()) {

                item.put("transactionData", null);

            } else {

                try {
                    Object parsed =
                            gson.fromJson(
                                    transactionData,
                                    Object.class
                            );

                    item.put(
                            "transactionData",
                            parsed
                    );

                } catch (Exception e) {

                    // Preserve malformed/original data rather than
                    // dropping the transaction locally.
                    item.put(
                            "transactionData",
                            transactionData
                    );
                }
            }

            payload.add(item);
        }

        String json =
                gson.toJson(payload);

        SdkLogger.debug(
                TAG
                        + ": Uploading "
                        + transactions.size()
                        + " transactions to "
                        + url
        );

        try {

            Response response =
                    executeJsonPost(
                            url,
                            json,
                            true
                    );

            int statusCode =
                    response.code();

            boolean success =
                    response.isSuccessful();

            String responseBody =
                    readResponseBody(response);

            response.close();

            SdkLogger.debug(
                    TAG
                            + ": Transaction response HTTP "
                            + statusCode
                            + " body="
                            + responseBody
            );

            return success;

        } catch (Exception e) {

            SdkLogger.error(
                    TAG
                            + ": Transaction upload failed",
                    e
            );

            return false;
        }
    }

    // ============================================================
// USAGE REPORTING
// ============================================================

    public boolean reportUsage(int eventCount) {

        String url =
                buildUrl(
                        licenseServerUrl,
                        "/api/usage/heartbeat"
                );

        Map<String, Object> payload =
                new HashMap<>();

        payload.put(
                "apiKey",
                apiKey
        );

        payload.put(
                "eventCount",
                eventCount
        );

        String json =
                gson.toJson(payload);

        SdkLogger.debug(
                TAG
                        + ": Reporting usage heartbeat to "
                        + url
                        + " count="
                        + eventCount
        );

        try {

            Response response =
                    executeJsonPost(
                            url,
                            json,
                            true
                    );

            int statusCode =
                    response.code();

            String responseBody =
                    readResponseBody(
                            response
                    );

            boolean success =
                    response.isSuccessful();

            response.close();

            SdkLogger.debug(
                    TAG
                            + ": Usage heartbeat HTTP "
                            + statusCode
                            + " body="
                            + responseBody
            );

            return success;

        } catch (Exception e) {

            SdkLogger.error(
                    TAG
                            + ": Usage heartbeat failed",
                    e
            );

            return false;
        }
    }
// ============================================================
// USER IDENTITY REPORTING
// ============================================================

    /**
     * Reports user identity information to the developer backend.
     *
     * POST /api/users/report
     *
     * Payload:
     *
     * {
     *     "appId": "...",
     *     "apiKey": "...",
     *     "name": "...",
     *     "email": "...",
     *     "phone": "..."
     * }
     */
    public boolean reportUser(
            String appId,
            String name,
            String email,
            String phone) {

        String url =
                buildUrl(
                        licenseServerUrl,
                        "/api/users/report"
                );

        Map<String, Object> payload =
                new HashMap<>();

        payload.put(
                "appId",
                appId
        );

        payload.put(
                "apiKey",
                apiKey
        );

        payload.put(
                "name",
                name
        );

        payload.put(
                "email",
                email
        );

        payload.put(
                "phone",
                phone
        );

        String json =
                gson.toJson(payload);

        SdkLogger.debug(
                TAG
                        + ": Reporting user identity to "
                        + url
        );

        SdkLogger.debug(
                TAG
                        + ": Identity payload = "
                        + json
        );

        try {

            Response response =
                    executeJsonPost(
                            url,
                            json,
                            true
                    );

            int statusCode =
                    response.code();

            String responseBody =
                    readResponseBody(
                            response
                    );

            boolean success =
                    response.isSuccessful();

            response.close();

            SdkLogger.debug(
                    TAG
                            + ": User identity response HTTP "
                            + statusCode
                            + " body="
                            + responseBody
            );

            return success;

        } catch (Exception e) {

            SdkLogger.error(
                    TAG
                            + ": User identity reporting failed",
                    e
            );

            return false;
        }
    }

    // ============================================================
    // REMOTE CONFIG
    // ============================================================

    /**
     * Retrieves remote configuration.
     *
     * GET /api/config/sync
     */
    public String fetchRemoteConfig()
            throws IOException {

        String url =
                buildUrl(
                        licenseServerUrl,
                        REMOTE_CONFIG_PATH
                );

        SdkLogger.debug(
                TAG
                        + ": Fetching remote config: "
                        + url
        );

        Request request =
                new Request.Builder()
                        .url(url)
                        .get()
                        .header(
                                API_KEY_HEADER,
                                apiKey
                        )
                        .header(
                                "Accept",
                                "application/json"
                        )
                        .build();

        Response response =
                httpClient.newCall(
                        request
                ).execute();

        String body =
                readResponseBody(
                        response
                );

        int statusCode =
                response.code();

        boolean success =
                response.isSuccessful();

        response.close();

        SdkLogger.debug(
                TAG
                        + ": Remote config HTTP "
                        + statusCode
        );

        if (!success) {

            throw new IOException(
                    "Remote config request failed. HTTP "
                            + statusCode
                            + " body="
                            + body
            );
        }

        return body;
    }

    // ============================================================
    // SERVER HEALTH
    // ============================================================

    /**
     * Checks monitoring server health.
     *
     * GET /health
     */
    public ServerHealthResult checkServerHealth() {

        String url =
                buildUrl(
                        serverUrl,
                        HEALTH_PATH
                );

        Request request =
                new Request.Builder()
                        .url(url)
                        .get()
                        .header(
                                "Accept",
                                "application/json"
                        )
                        .build();

        try {

            Response response =
                    httpClient.newCall(
                            request
                    ).execute();

            int statusCode =
                    response.code();

            String body =
                    readResponseBody(
                            response
                    );

            boolean healthy =
                    response.isSuccessful();

            response.close();

            String message =
                    healthy
                            ? "Server reachable"
                            : "Server returned HTTP "
                              + statusCode;

            return new ServerHealthResult(
                    healthy,
                    statusCode,
                    body,
                    message
            );

        } catch (Exception e) {

            SdkLogger.error(
                    TAG
                            + ": Server health check failed",
                    e
            );

            return new ServerHealthResult(
                    false,
                    0,
                    "",
                    e.getMessage() != null
                            ? e.getMessage()
                            : "Server unreachable"
            );
        }
    }

    // ============================================================
    // HTTP HELPERS
    // ============================================================

    private Response executeJsonPost(
            String url,
            String json,
            boolean includeApiKey)
            throws IOException {

        RequestBody body =
                RequestBody.create(
                        json,
                        MediaType.parse(
                                JSON_MEDIA_TYPE
                        )
                );

        Request.Builder builder =
                new Request.Builder()
                        .url(url)
                        .post(body)
                        .header(
                                "Accept",
                                "application/json"
                        );

        if (includeApiKey
                && !apiKey.isEmpty()) {

            builder.header(
                    API_KEY_HEADER,
                    apiKey
            );
        }

        return httpClient.newCall(
                builder.build()
        ).execute();
    }

    private String readResponseBody(
            Response response)
            throws IOException {

        if (response == null
                || response.body() == null) {

            return "";
        }

        return response.body().string();
    }

    // ============================================================
    // URL HELPERS
    // ============================================================

    private static String normalizeBaseUrl(
            String url) {

        if (url == null) {
            return "";
        }

        String result =
                url.trim();

        while (result.endsWith("/")) {

            result =
                    result.substring(
                            0,
                            result.length() - 1
                    );
        }

        return result;
    }

    private static String buildUrl(
            String baseUrl,
            String path) {

        String base =
                normalizeBaseUrl(
                        baseUrl
                );

        if (path == null
                || path.isEmpty()) {

            return base;
        }

        if (!path.startsWith("/")) {

            return base
                    + "/"
                    + path;
        }

        return base + path;
    }
}