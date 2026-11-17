package com.plumb5.monitor;

import androidx.annotation.NonNull;

public final class MonitoringStatus {

    private final boolean initialized;
    private final boolean enabled;
    private final String sdkVersion;
    private final String appId;
    private final String serverUrl;
    private final String sessionId;

    public MonitoringStatus(
            boolean initialized,
            boolean enabled,
            @NonNull String sdkVersion,
            @NonNull String appId,
            @NonNull String serverUrl,
            @NonNull String sessionId) {

        this.initialized = initialized;
        this.enabled = enabled;
        this.sdkVersion = sdkVersion;
        this.appId = appId;
        this.serverUrl = serverUrl;
        this.sessionId = sessionId;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @NonNull
    public String getSdkVersion() {
        return sdkVersion;
    }

    @NonNull
    public String getAppId() {
        return appId;
    }

    @NonNull
    public String getServerUrl() {
        return serverUrl;
    }

    @NonNull
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String toString() {

        return "MonitoringStatus{" +
                "initialized=" + initialized +
                ", enabled=" + enabled +
                ", sdkVersion='" + sdkVersion + '\'' +
                ", appId='" + appId + '\'' +
                ", serverUrl='" + serverUrl + '\'' +
                ", sessionId='" + sessionId + '\'' +
                '}';
    }
}
