package com.plumb5.monitor.network;

import androidx.annotation.NonNull;

import com.plumb5.monitor.Plumb5Monitor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Interceptor to automatically track network performance.
 */
public class P5NetworkInterceptor implements Interceptor {

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        long startTime = System.currentTimeMillis();

        Response response;
        try {
            response = chain.proceed(request);
        } catch (IOException e) {
            trackNetworkError(request, startTime, e);
            throw e;
        }

        long duration = System.currentTimeMillis() - startTime;
        trackNetworkSuccess(request, response, duration);

        return response;
    }

    private void trackNetworkSuccess(Request request, Response response, long duration) {
        Map<String, String> props = new HashMap<>();
        props.put("url", sanitizeUrl(request.url().toString()));
        props.put("method", request.method());
        props.put("status_code", String.valueOf(response.code()));
        props.put("duration_ms", String.valueOf(duration));
        props.put("success", "true");

        Plumb5Monitor.getInstance().trackEvent("NETWORK_PERFORMANCE", null, props);
    }

    private void trackNetworkError(Request request, long startTime, Exception e) {
        long duration = System.currentTimeMillis() - startTime;
        Map<String, String> props = new HashMap<>();
        props.put("url", sanitizeUrl(request.url().toString()));
        props.put("method", request.method());
        props.put("duration_ms", String.valueOf(duration));
        props.put("success", "false");
        props.put("error", e.getMessage());

        Plumb5Monitor.getInstance().trackEvent("NETWORK_PERFORMANCE", null, props);
    }

    private String sanitizeUrl(String url) {
        // Basic sanitization: remove query parameters if needed, or just return as is for internal use.
        if (url.contains("?")) {
            return url.substring(0, url.indexOf("?"));
        }
        return url;
    }
}
