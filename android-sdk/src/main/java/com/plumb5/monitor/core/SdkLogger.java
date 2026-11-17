package com.plumb5.monitor.core;

import android.util.Log;

import java.util.LinkedList;
import java.util.List;

public final class SdkLogger {

    private static final String TAG = "Plumb5Monitor";
    private static volatile boolean debugEnabled = false;
    private static final LinkedList<String> logBuffer = new LinkedList<>();
    private static final int MAX_BUFFER_SIZE = 100;

    private SdkLogger() {
    }

    public static void initialize(
            boolean enabled) {

        debugEnabled = enabled;
    }

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static void debug(
            String message) {

        if (!debugEnabled) {
            return;
        }

        Log.d(
                TAG,
                safeMessage(message)
        );
        addToBuffer("DEBUG: " + message);
    }

    public static void error(
            String message) {

        Log.e(
                TAG,
                safeMessage(message)
        );
        addToBuffer("ERROR: " + message);
    }

    public static void error(
            String message,
            Throwable throwable) {

        Log.e(
                TAG,
                safeMessage(message),
                throwable
        );
        addToBuffer("ERROR: " + message + " - " + throwable.getMessage());
    }

    private static void addToBuffer(String log) {
        synchronized (logBuffer) {
            if (logBuffer.size() >= MAX_BUFFER_SIZE) {
                logBuffer.removeFirst();
            }
            logBuffer.add(System.currentTimeMillis() + ": " + log);
        }
    }

    public static String getExportableLogs() {
        StringBuilder sb = new StringBuilder();
        synchronized (logBuffer) {
            for (String log : logBuffer) {
                sb.append(log).append("\n");
            }
        }
        return sb.toString();
    }

    private static String safeMessage(
            String message) {

        if (message == null) {
            return "";
        }

        /*
         * Never allow accidental API-key logging.
         */
        return message
                .replaceAll(
                        "(?i)(X-Monitoring-Api-Key\\s*[:=]\\s*)[^\\s,]+",
                        "$1[REDACTED]"
                )
                .replaceAll(
                        "(?i)(apiKey\\s*[:=]\\s*)[^\\s,]+",
                        "$1[REDACTED]"
                );
    }
}
