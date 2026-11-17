package com.plumb5.monitor.network;

import com.plumb5.monitor.database.EventEntity;

import java.util.ArrayList;
import java.util.List;

public final class EventRequest {

    private final List<EventData> events;

    public EventRequest(List<EventEntity> entities) {

        this.events = new ArrayList<>();

        if (entities == null) {
            return;
        }

        for (EventEntity entity : entities) {
            this.events.add(
                    new EventData(entity)
            );
        }
    }

    public List<EventData> getEvents() {
        return events;
    }

    public static final class EventData {

        private final String eventId;
        private final String appId;
        private final String sessionId;
        private final String eventName;
        private final String screenName;
        private final String deviceModel;
        private final String androidVersion;
        private final String sdkVersion;
        private final long timestamp;

        public EventData(EventEntity entity) {

            this.eventId = entity.getEventId();
            this.appId = entity.getAppId();
            this.sessionId = entity.getSessionId();
            this.eventName = entity.getEventName();
            this.screenName = entity.getScreenName();
            this.deviceModel = entity.getDeviceModel();
            this.androidVersion = entity.getAndroidVersion();
            this.sdkVersion = entity.getSdkVersion();
            this.timestamp = entity.getTimestamp();
        }

        public String getEventId() {
            return eventId;
        }

        public String getAppId() {
            return appId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getEventName() {
            return eventName;
        }

        public String getScreenName() {
            return screenName;
        }

        public String getDeviceModel() {
            return deviceModel;
        }

        public String getAndroidVersion() {
            return androidVersion;
        }

        public String getSdkVersion() {
            return sdkVersion;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
