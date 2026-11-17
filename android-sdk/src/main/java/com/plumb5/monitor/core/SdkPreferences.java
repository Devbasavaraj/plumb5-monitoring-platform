
    package com.plumb5.monitor.core;

    import android.content.Context;
    import android.content.SharedPreferences;

    import androidx.annotation.NonNull;
    import androidx.security.crypto.EncryptedSharedPreferences;
    import androidx.security.crypto.MasterKeys;

    import com.plumb5.monitor.MonitoringConfig;

    import java.io.IOException;
    import java.security.GeneralSecurityException;

    public final class SdkPreferences {

        private static final String PREF_NAME =
                "monitoring_sdk_preferences";

        private static final String KEY_APP_ID =
                "app_id";

        private static final String KEY_SERVER_URL =
                "server_url";

        private static final String KEY_LICENSE_SERVER_URL =
                "license_server_url";

        private static final String KEY_API_KEY = "api_key";

        private static final String KEY_BATCH_SIZE =
                "batch_size";

        private static final String KEY_MAX_RETRIES =
                "max_retries";

        private static final String KEY_ENABLED =
                "enabled";

        private static final String KEY_DEBUG_LOGGING =
                "debug_logging";

        private static final String KEY_MAX_DATABASE_SIZE =
                "max_database_size";

        private static final String KEY_DEVELOPMENT_MODE =
                "development_mode";

        private static final String KEY_BEACON_ENABLED =
                "beacon_enabled";

        private static final String KEY_GEOFENCE_ENABLED =
                "geofence_enabled";

        private static final String KEY_LOCATION_TRACKING_ENABLED =
                "location_tracking_enabled";

        private static final String KEY_SYNC_INTERVAL_MINUTES =
                "sync_interval_minutes";

        private static final String KEY_USER_NAME = "user_name";
        private static final String KEY_USER_EMAIL = "user_email";
        private static final String KEY_USER_PHONE = "user_phone";
        private static final String KEY_CONSENT_GRANTED = "consent_granted";
        private static final String KEY_LAST_APP_VERSION = "last_app_version";
        private static final String KEY_REFERRER_CHECKED = "referrer_checked";

        private static final String PREFIX_CAMPAIGN_IMPRESSIONS =
                "campaign_imp_";

        private static final String PREFIX_CAMPAIGN_LAST_SHOWN =
                "campaign_last_";

        private static final String KEY_BEACON_FILTERS =
                "beacon_filters";

        private SharedPreferences preferences;

        public SdkPreferences(@NonNull Context context) {
            try {
                String masterKeyAlias =
                        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

                preferences = EncryptedSharedPreferences.create(
                        PREF_NAME,
                        masterKeyAlias,
                        context.getApplicationContext(),
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );

            } catch (GeneralSecurityException | IOException e) {
                SdkLogger.error(
                        "Failed to initialize EncryptedSharedPreferences",
                        e
                );

                preferences = context.getApplicationContext()
                        .getSharedPreferences(
                                PREF_NAME,
                                Context.MODE_PRIVATE
                        );
            }
        }

        public void saveConfig(@NonNull MonitoringConfig config) {

            preferences.edit()
                    .putString(
                            KEY_APP_ID,
                            config.getAppId()
                    )
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
                    .putBoolean(
                            KEY_ENABLED,
                            config.isEnabled()
                    )
                    .putBoolean(
                            KEY_DEBUG_LOGGING,
                            config.isDebugLoggingEnabled()
                    )
                    .putInt(
                            KEY_MAX_DATABASE_SIZE,
                            config.getMaxDatabaseSize()
                    )
                    .putBoolean(
                            KEY_DEVELOPMENT_MODE,
                            config.isDevelopmentMode()
                    )
                    .putBoolean(
                            KEY_BEACON_ENABLED,
                            config.isBeaconEnabled()
                    )
                    .putBoolean(
                            KEY_GEOFENCE_ENABLED,
                            config.isGeofenceEnabled()
                    )
                    .putBoolean(
                            KEY_LOCATION_TRACKING_ENABLED,
                            config.isLocationTrackingEnabled()
                    )
                    .putInt(
                            KEY_SYNC_INTERVAL_MINUTES,
                            config.getSyncIntervalMinutes()
                    )
                    .apply();
        }

        public String getAppId() {
            return preferences.getString(KEY_APP_ID, null);
        }

        public String getServerUrl() {
            return preferences.getString(KEY_SERVER_URL, null);
        }

        /**
         * Returns the configured license/config server URL.
         * Older SDK configurations fall back to the monitoring server URL.
         */
        public String getLicenseServerUrl() {
            String licenseServerUrl = preferences.getString(
                    KEY_LICENSE_SERVER_URL,
                    null
            );

            if (licenseServerUrl == null
                    || licenseServerUrl.trim().isEmpty()) {
                return getServerUrl();
            }

            return licenseServerUrl;
        }

        public String getApiKey() {
            return preferences.getString(KEY_API_KEY, null);
        }

        public int getBatchSize() {
            return preferences.getInt(KEY_BATCH_SIZE, 50);
        }

        public int getMaxRetries() {
            return preferences.getInt(KEY_MAX_RETRIES, 3);
        }

        public boolean isEnabled() {
            return preferences.getBoolean(KEY_ENABLED, true);
        }

        public boolean isDebugLoggingEnabled() {
            return preferences.getBoolean(KEY_DEBUG_LOGGING, false);
        }

        public int getMaxDatabaseSize() {
            return preferences.getInt(KEY_MAX_DATABASE_SIZE, 1000);
        }

        public boolean isDevelopmentMode() {
            return preferences.getBoolean(KEY_DEVELOPMENT_MODE, false);
        }

        public boolean isBeaconEnabled() {
            return preferences.getBoolean(KEY_BEACON_ENABLED, false);
        }

        public boolean isGeofenceEnabled() {
            return preferences.getBoolean(KEY_GEOFENCE_ENABLED, false);
        }

        public boolean isLocationTrackingEnabled() {
            return preferences.getBoolean(KEY_LOCATION_TRACKING_ENABLED, false);
        }

        public int getSyncIntervalMinutes() {
            return preferences.getInt(KEY_SYNC_INTERVAL_MINUTES, 15);
        }

        public void saveRemoteSdkSettings(
                int batchSize,
                int syncIntervalMinutes
        ) {
            int safeBatchSize =
                    batchSize > 0 ? batchSize : getBatchSize();

            int safeInterval = syncIntervalMinutes < 15
                    ? 15
                    : Math.min(syncIntervalMinutes, 1440);

            preferences.edit()
                    .putInt(KEY_BATCH_SIZE, safeBatchSize)
                    .putInt(KEY_SYNC_INTERVAL_MINUTES, safeInterval)
                    .apply();
        }

        public void saveUserInfo(
                String name,
                String email,
                String phone
        ) {
            preferences.edit()
                    .putString(KEY_USER_NAME, name)
                    .putString(KEY_USER_EMAIL, email)
                    .putString(KEY_USER_PHONE, phone)
                    .apply();
        }

        public String getUserName() {
            return preferences.getString(KEY_USER_NAME, null);
        }

        public String getUserEmail() {
            return preferences.getString(KEY_USER_EMAIL, null);
        }

        public String getUserPhone() {
            return preferences.getString(KEY_USER_PHONE, null);
        }

        public void setConsentGranted(boolean granted) {
            preferences.edit()
                    .putBoolean(KEY_CONSENT_GRANTED, granted)
                    .apply();
        }

        public boolean isConsentGranted() {
            return preferences.getBoolean(KEY_CONSENT_GRANTED, true);
        }

        public void incrementCampaignImpressions(String campaignId) {
            int count = getCampaignImpressions(campaignId);

            preferences.edit()
                    .putInt(
                            PREFIX_CAMPAIGN_IMPRESSIONS + campaignId,
                            count + 1
                    )
                    .putLong(
                            PREFIX_CAMPAIGN_LAST_SHOWN + campaignId,
                            System.currentTimeMillis()
                    )
                    .apply();
        }

        public int getCampaignImpressions(String campaignId) {
            return preferences.getInt(
                    PREFIX_CAMPAIGN_IMPRESSIONS + campaignId,
                    0
            );
        }

        public long getCampaignLastShownTime(String campaignId) {
            return preferences.getLong(
                    PREFIX_CAMPAIGN_LAST_SHOWN + campaignId,
                    0
            );
        }

        public void saveBeaconFilters(String json) {
            preferences.edit()
                    .putString(KEY_BEACON_FILTERS, json)
                    .apply();
        }

        public String getBeaconFilters() {
            return preferences.getString(KEY_BEACON_FILTERS, null);
        }

        public void setLastAppVersion(int version) {
            preferences.edit()
                    .putInt(KEY_LAST_APP_VERSION, version)
                    .apply();
        }

        public int getLastAppVersion() {
            return preferences.getInt(KEY_LAST_APP_VERSION, -1);
        }

        public void setReferrerChecked(boolean checked) {
            preferences.edit()
                    .putBoolean(KEY_REFERRER_CHECKED, checked)
                    .apply();
        }

        public boolean isReferrerChecked() {
            return preferences.getBoolean(KEY_REFERRER_CHECKED, false);
        }

        public boolean isConfigured() {
            String appId = getAppId();
            String serverUrl = getServerUrl();

            return appId != null
                    && !appId.trim().isEmpty()
                    && serverUrl != null
                    && !serverUrl.trim().isEmpty();
        }

        public void clear() {
            preferences.edit()
                    .clear()
                    .apply();
        }
    }
