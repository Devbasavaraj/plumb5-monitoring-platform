package com.plumb5.monitor.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

import androidx.room.Index;

@Entity(
    tableName = "monitoring_events",
    indices = {
        @Index("timestamp"),
        @Index("eventName"),
        @Index("sessionId")
    }
)
public class EventEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    @SerializedName("event_id")
    private String eventId = "";

    @SerializedName("app_id")
    private String appId;

    @SerializedName("session_id")
    private String sessionId;

    @SerializedName("event_name")
    private String eventName;

    @SerializedName("screen_name")
    private String screenName;

    @SerializedName("device_model")
    private String deviceModel;

    @SerializedName("android_version")
    private String androidVersion;

    @SerializedName("sdk_version")
    private String sdkVersion;

    @SerializedName("timestamp")
    private long timestamp;

    @SerializedName("retry_count")
    private int retryCount;

    @SerializedName("properties")
    private String properties;

    @SerializedName("payload")
    private String payload;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("sequence_number")
    private long sequenceNumber;

    @SerializedName("battery_level")
    private int batteryLevel;

    @SerializedName("network_type")
    private String networkType;

    @SerializedName("orientation")
    private String orientation;

    public EventEntity() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public String getNetworkType() {
        return networkType;
    }

    public void setNetworkType(String networkType) {
        this.networkType = networkType;
    }

    public String getOrientation() {
        return orientation;
    }

    public void setOrientation(String orientation) {
        this.orientation = orientation;
    }

    public String getProperties() {
        return properties;
    }

    public void setProperties(String properties) {
        this.properties = properties;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    @NonNull
    public String getEventId() {
        return eventId;
    }

    public void setEventId(@NonNull String eventId) {
        this.eventId = eventId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getScreenName() {
        return screenName;
    }

    public void setScreenName(String screenName) {
        this.screenName = screenName;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getAndroidVersion() {
        return androidVersion;
    }

    public void setAndroidVersion(String androidVersion) {
        this.androidVersion = androidVersion;
    }

    public String getSdkVersion() {
        return sdkVersion;
    }

    public void setSdkVersion(String sdkVersion) {
        this.sdkVersion = sdkVersion;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
}
