package com.plumb5.monitor.network;

public class ServerHealthResult {

    private final boolean healthy;
    private final int statusCode;
    private final String responseBody;
    private final String message;

    public ServerHealthResult(
            boolean healthy,
            int statusCode,
            String responseBody,
            String message) {

        this.healthy = healthy;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.message = message;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getMessage() {
        return message;
    }
}
