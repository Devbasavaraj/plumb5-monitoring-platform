package com.plumb5.monitor.core;

import java.util.UUID;

public final class SessionManager {

    private String sessionId;
    private long sessionStartTime;
    private long sessionEndTime;
    private static final long SESSION_RESUME_THRESHOLD_MS = 30000; // 30 seconds

    public synchronized void startSession() {
        if (sessionId != null && (System.currentTimeMillis() - sessionEndTime) < SESSION_RESUME_THRESHOLD_MS) {
            SdkLogger.debug("Session resumed: " + sessionId);
            return;
        }

        sessionId = UUID
                .randomUUID()
                .toString();

        sessionStartTime =
                System.currentTimeMillis();

        SdkLogger.debug(
                "Session started: " +
                        sessionId
        );
    }

    public synchronized String getSessionId() {

        if (sessionId == null) {
            startSession();
        }

        return sessionId;
    }

    public synchronized long getSessionStartTime() {
        return sessionStartTime;
    }

    public synchronized void endSession() {

        SdkLogger.debug(
                "Session suspended: " +
                        sessionId
        );

        sessionEndTime = System.currentTimeMillis();
    }
}
