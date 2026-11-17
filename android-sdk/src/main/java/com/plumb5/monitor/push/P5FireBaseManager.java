package com.plumb5.monitor.push;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.plumb5.monitor.Plumb5Monitor;

import java.util.HashMap;
import java.util.Map;

public class P5FireBaseManager extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);

        Map<String, String> props = new HashMap<>();
        props.put("fcm_token", token);

        try {
            Plumb5Monitor.getInstance().trackEvent("FCM_TOKEN_UPDATE", null, props);
        } catch (Exception ignored) {}
    }
}
