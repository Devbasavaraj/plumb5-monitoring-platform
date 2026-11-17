package com.example.license.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Sends real FCM push messages with the Firebase Admin SDK.
 *
 * Disabled until firebase.credentials.path points at a Firebase service
 * account JSON file; nothing is initialised at startup, so the application
 * runs exactly as before without it.
 */
@Service
public class FcmSender {

    public enum Result { SENT, INVALID_TOKEN, FAILED }

    @Value("${firebase.credentials.path:}")
    private String credentialsPath;

    private boolean initialised = false;

    public boolean isConfigured() {
        return credentialsPath != null && !credentialsPath.isBlank();
    }

    private synchronized boolean ensureInitialised() {
        if (initialised) {
            return true;
        }
        if (!isConfigured()) {
            return false;
        }
        try (InputStream in = new FileInputStream(credentialsPath)) {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(in))
                        .build();
                FirebaseApp.initializeApp(options);
            }
            initialised = true;
            return true;
        } catch (IOException e) {
            System.err.println("[FCM] Could not read Firebase credentials: " + e.getMessage());
            return false;
        }
    }

    /** Sends a data-only message (the SDK builds the notification itself). */
    public Result send(String token, Map<String, String> data) {
        if (!ensureInitialised()) {
            return Result.FAILED;
        }
        try {
            Message message = Message.builder()
                    .setToken(token)
                    .putAllData(data)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .build();
            FirebaseMessaging.getInstance().send(message);
            return Result.SENT;
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            // Only UNREGISTERED means the token is dead; INVALID_ARGUMENT can also be a bad payload.
            if (code == MessagingErrorCode.UNREGISTERED) {
                return Result.INVALID_TOKEN;
            }
            System.err.println("[FCM] Send failed: " + e.getMessage());
            return Result.FAILED;
        }
    }
}
