package com.plumb5.monitor;

import androidx.annotation.NonNull;

import com.plumb5.monitor.core.CommunicationMode;

public final class MonitoringConfig {

    private final String appId;
    private final String serverUrl;
    private final String licenseServerUrl;
    private final String apiKey;

    private final boolean enabled;
    private final int batchSize;
    private final int maxRetries;

    private final boolean debugLoggingEnabled;

    private final String communicationMode;

    private final boolean beaconEnabled;
    private final boolean geofenceEnabled;
    private final boolean locationTrackingEnabled;

    private final int maxDatabaseSize;

    private final boolean enforceHttps;

    private final int licenseGracePeriodMinutes;

    private final boolean developmentMode;
    private final int syncIntervalMinutes;

    private MonitoringConfig(Builder builder) {

        this.appId = builder.appId;
        this.serverUrl = builder.serverUrl;
        this.licenseServerUrl = builder.licenseServerUrl;
        this.apiKey = builder.apiKey;

        this.enabled = builder.enabled;
        this.batchSize = builder.batchSize;
        this.maxRetries = builder.maxRetries;

        this.debugLoggingEnabled =
                builder.debugLoggingEnabled;

        this.communicationMode =
                builder.communicationMode;

        this.beaconEnabled =
                builder.beaconEnabled;

        this.geofenceEnabled =
                builder.geofenceEnabled;

        this.locationTrackingEnabled =
                builder.locationTrackingEnabled;

        this.maxDatabaseSize =
                builder.maxDatabaseSize;

        this.enforceHttps =
                builder.enforceHttps;

        this.licenseGracePeriodMinutes =
                builder.licenseGracePeriodMinutes;

        this.developmentMode =
                builder.developmentMode;

        this.syncIntervalMinutes =
                builder.syncIntervalMinutes;
    }

    // ============================================================
    // GETTERS
    // ============================================================

    @NonNull
    public String getAppId() {
        return appId;
    }

    @NonNull
    public String getServerUrl() {
        return serverUrl;
    }

    @NonNull
    public String getLicenseServerUrl() {
        return licenseServerUrl;
    }

    @NonNull
    public String getApiKey() {
        return apiKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public boolean isDebugLogging() {
        return debugLoggingEnabled;
    }

    /**
     * Backward-compatible getter used by existing SDK classes.
     */
    public boolean isDebugLoggingEnabled() {
        return debugLoggingEnabled;
    }

    @NonNull
    public String getCommunicationMode() {
        return communicationMode;
    }

    public boolean isBeaconEnabled() {
        return beaconEnabled;
    }

    public boolean isGeofenceEnabled() {
        return geofenceEnabled;
    }

    public boolean isLocationTrackingEnabled() {
        return locationTrackingEnabled;
    }

    public int getMaxDatabaseSize() {
        return maxDatabaseSize;
    }

    public boolean isEnforceHttps() {
        return enforceHttps;
    }

    public int getLicenseGracePeriodMinutes() {
        return licenseGracePeriodMinutes;
    }

    public boolean isDevelopmentMode() {
        return developmentMode;
    }

    public int getSyncIntervalMinutes() {
        return syncIntervalMinutes;
    }

    // ============================================================
    // BUILDER COPY
    // ============================================================

    public Builder toBuilder() {

        return new Builder(
                appId,
                serverUrl
        )
                .licenseServerUrl(licenseServerUrl)
                .apiKey(apiKey)
                .enabled(enabled)
                .batchSize(batchSize)
                .maxRetries(maxRetries)
                .debugLogging(debugLoggingEnabled)
                .communicationMode(communicationMode)
                .beaconEnabled(beaconEnabled)
                .geofenceEnabled(geofenceEnabled)
                .locationTrackingEnabled(locationTrackingEnabled)
                .maxDatabaseSize(maxDatabaseSize)
                .enforceHttps(enforceHttps)
                .licenseGracePeriod(
                        licenseGracePeriodMinutes
                )
                .developmentMode(developmentMode)
                .syncIntervalMinutes(syncIntervalMinutes);
    }

    // ============================================================
    // BUILDER
    // ============================================================

    public static final class Builder {

        private final String appId;
        private final String serverUrl;

        private String licenseServerUrl;

        private String apiKey = "";

        private boolean enabled = true;

        private int batchSize = 10;

        private int maxRetries = 3;

        private boolean debugLoggingEnabled = false;

        private String communicationMode =
                "AUTO_ON_CREATE";

        private boolean beaconEnabled = false;

        private boolean geofenceEnabled = false;

        private boolean locationTrackingEnabled = false;

        private int maxDatabaseSize = 1000;

        private boolean enforceHttps = true;

        private int licenseGracePeriodMinutes = 1440;

        private boolean developmentMode = false;

        private int syncIntervalMinutes = 15;

        public Builder(
                @NonNull String appId,
                @NonNull String serverUrl) {

            if (appId.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "appId cannot be empty"
                );
            }

            if (serverUrl.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "serverUrl cannot be empty"
                );
            }

            this.appId = appId.trim();

            this.serverUrl =
                    removeTrailingSlash(
                            serverUrl.trim()
                    );

            /*
             * Backward-compatible default:
             * if a separate license server is not supplied,
             * use the monitoring server.
             */
            this.licenseServerUrl =
                    this.serverUrl;
        }

        // ========================================================
        // API KEY
        // ========================================================

        public Builder apiKey(String apiKey) {

            this.apiKey =
                    apiKey == null
                            ? ""
                            : apiKey.trim();

            return this;
        }

        // ========================================================
        // LICENSE SERVER
        // ========================================================

        public Builder licenseServerUrl(
                String licenseServerUrl) {

            if (licenseServerUrl == null
                    || licenseServerUrl.trim().isEmpty()) {

                this.licenseServerUrl =
                        this.serverUrl;

            } else {

                this.licenseServerUrl =
                        removeTrailingSlash(
                                licenseServerUrl.trim()
                        );
            }

            return this;
        }

        // ========================================================
        // ENABLED
        // ========================================================

        public Builder enabled(boolean enabled) {

            this.enabled = enabled;

            return this;
        }

        // ========================================================
        // BATCH SIZE
        // ========================================================

        public Builder batchSize(int batchSize) {

            if (batchSize <= 0) {
                throw new IllegalArgumentException(
                        "batchSize must be greater than 0"
                );
            }

            this.batchSize = batchSize;

            return this;
        }

        // ========================================================
        // MAX RETRIES
        // ========================================================

        public Builder maxRetries(int maxRetries) {

            if (maxRetries < 0) {
                throw new IllegalArgumentException(
                        "maxRetries cannot be negative"
                );
            }

            this.maxRetries = maxRetries;

            return this;
        }

        // ========================================================
        // DEBUG LOGGING
        // ========================================================

        public Builder debugLogging(
                boolean debugLogging) {

            this.debugLoggingEnabled =
                    debugLogging;

            return this;
        }

        // ========================================================
        // COMMUNICATION MODE - STRING
        // ========================================================

        public Builder communicationMode(
                @NonNull String communicationMode) {

            if (communicationMode.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "communicationMode cannot be empty"
                );
            }

            this.communicationMode =
                    communicationMode;

            return this;
        }

        // ========================================================
        // COMMUNICATION MODE - ENUM
        // ========================================================

        public Builder communicationMode(
                @NonNull CommunicationMode mode) {

            this.communicationMode =
                    mode.name();

            return this;
        }

        // ========================================================
        // BEACON
        // ========================================================

        public Builder beaconEnabled(
                boolean beaconEnabled) {

            this.beaconEnabled =
                    beaconEnabled;

            return this;
        }

        // ========================================================
        // GEOFENCE
        // ========================================================

        public Builder geofenceEnabled(
                boolean geofenceEnabled) {

            this.geofenceEnabled =
                    geofenceEnabled;

            return this;
        }

        public Builder locationTrackingEnabled(
                boolean locationTrackingEnabled) {

            this.locationTrackingEnabled =
                    locationTrackingEnabled;

            return this;
        }

        // ========================================================
        // DATABASE SIZE
        // ========================================================

        public Builder maxDatabaseSize(
                int maxDatabaseSize) {

            if (maxDatabaseSize <= 0) {
                throw new IllegalArgumentException(
                        "maxDatabaseSize must be greater than 0"
                );
            }

            this.maxDatabaseSize =
                    maxDatabaseSize;

            return this;
        }

        // ========================================================
        // HTTPS
        // ========================================================

        public Builder enforceHttps(
                boolean enforceHttps) {

            this.enforceHttps =
                    enforceHttps;

            return this;
        }

        // ========================================================
        // LICENSE GRACE PERIOD
        // ========================================================

        public Builder licenseGracePeriod(
                int minutes) {

            if (minutes < 0) {
                throw new IllegalArgumentException(
                        "licenseGracePeriod cannot be negative"
                );
            }

            this.licenseGracePeriodMinutes =
                    minutes;

            return this;
        }

        // ========================================================
        // DEVELOPMENT MODE
        // ========================================================

        public Builder developmentMode(
                boolean developmentMode) {

            this.developmentMode =
                    developmentMode;

            return this;
        }

        // ========================================================
        // REMOTE CONFIG SYNC INTERVAL
        // ========================================================

        public Builder syncIntervalMinutes(
                int minutes) {

            if (minutes < 15) {
                throw new IllegalArgumentException(
                        "syncIntervalMinutes must be at least 15 minutes"
                );
            }

            if (minutes > 1440) {
                throw new IllegalArgumentException(
                        "syncIntervalMinutes cannot exceed 1440 minutes"
                );
            }

            this.syncIntervalMinutes = minutes;

            return this;
        }

        // ========================================================
        // BUILD
        // ========================================================

        public MonitoringConfig build() {

            String finalLicenseServerUrl =
                    licenseServerUrl;

            if (finalLicenseServerUrl == null
                    || finalLicenseServerUrl
                    .trim()
                    .isEmpty()) {

                finalLicenseServerUrl =
                        serverUrl;
            }

            /*
             * HTTPS validation.
             *
             * Local development URLs such as:
             * 10.0.2.2
             * localhost
             * 127.0.0.1
             *
             * can be used when enforceHttps(false)
             * is explicitly configured.
             */
            if (enforceHttps) {

                if (!serverUrl
                        .toLowerCase()
                        .startsWith("https://")) {

                    throw new IllegalArgumentException(
                            "HTTPS is required for serverUrl"
                    );
                }

                if (!finalLicenseServerUrl
                        .toLowerCase()
                        .startsWith("https://")) {

                    throw new IllegalArgumentException(
                            "HTTPS is required for licenseServerUrl"
                    );
                }
            }

            this.licenseServerUrl =
                    removeTrailingSlash(
                            finalLicenseServerUrl
                    );

            return new MonitoringConfig(this);
        }

        private static String removeTrailingSlash(
                String url) {

            String result = url;

            while (result.endsWith("/")) {

                result =
                        result.substring(
                                0,
                                result.length() - 1
                        );
            }

            return result;
        }
    }

    // ============================================================
    // UTILITY
    // ============================================================

    private static String removeTrailingSlash(
            String url) {

        String result = url;

        while (result.endsWith("/")) {

            result =
                    result.substring(
                            0,
                            result.length() - 1
                    );
        }

        return result;
    }
}