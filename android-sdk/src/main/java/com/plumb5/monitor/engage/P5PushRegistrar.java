package com.plumb5.monitor.engage;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.messaging.FirebaseMessaging;
import com.plumb5.monitor.core.P5DeviceInfo;
import com.plumb5.monitor.core.SdkConstants;
import com.plumb5.monitor.core.SdkLogger;
import com.plumb5.monitor.core.SdkPreferences;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

/**
 * Sends the FCM token to the Plumb5 backend so campaigns can target this
 * device. Uploads only when the token or the identified user changed, or
 * after 7 days, so calling it often is cheap.
 */
public final class P5PushRegistrar {

    private static final String TAG = "P5PushRegistrar";
    private static final long REFRESH_MS = TimeUnit.DAYS.toMillis(7);
    private static final String REGISTER_PATH = "/api/push/register";

    private P5PushRegistrar() {
    }

    /** Fetches the current token from Firebase and uploads it if needed. */
    public static void register(@NonNull Context context) {
        final Context app = context.getApplicationContext();
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    upload(app, task.getResult());
                } else {
                    SdkLogger.debug(TAG + ": FCM token unavailable (is Firebase configured?)");
                }
            });
        } catch (Exception e) {
            // FirebaseApp missing (no google-services.json) etc.
            SdkLogger.debug(TAG + ": Firebase not available: " + e.getMessage());
        }
    }

    /** Called from the messaging service when Firebase rotates the token. */
    public static void onNewToken(@NonNull Context context, @NonNull String token) {
        upload(context.getApplicationContext(), token);
    }

    /** Cheap periodic check used on app resume. */
    public static void syncIfNeeded(@NonNull Context context) {
        final P5EngagePrefs store = new P5EngagePrefs(context);
        if (store.getString(P5EngagePrefs.KEY_PUSH_SIGNATURE) == null) {
            return; // push was never enabled by the app
        }
        register(context);
    }

    private static void upload(final Context app, final String token) {
        P5Engage.io().execute(() -> {
            try {
                SdkPreferences sdk = new SdkPreferences(app);
                if (!sdk.isConfigured() || !sdk.isEnabled() || !sdk.isConsentGranted()) {
                    return;
                }
                String email = nz(sdk.getUserEmail());
                String signature = token.hashCode() + "|" + email;
                P5EngagePrefs store = new P5EngagePrefs(app);
                long age = System.currentTimeMillis() - store.getLong(P5EngagePrefs.KEY_PUSH_UPLOADED_AT);
                if (signature.equals(store.getString(P5EngagePrefs.KEY_PUSH_SIGNATURE)) && age < REFRESH_MS) {
                    return;
                }
                JSONObject body = new JSONObject();
                body.put("appId", nz(sdk.getAppId()));
                body.put("token", token);
                body.put("platform", "android");
                body.put("name", nz(sdk.getUserName()));
                body.put("email", email);
                body.put("phone", nz(sdk.getUserPhone()));
                body.put("deviceModel", Build.MODEL);
                body.put("osVersion", Build.VERSION.RELEASE);
                body.put("appVersion", String.valueOf(P5DeviceInfo.getAppVersionCode(app)));
                body.put("sdkVersion", SdkConstants.SDK_VERSION);
                if (P5Engage.postJson(app, REGISTER_PATH, body.toString())) {
                    store.putString(P5EngagePrefs.KEY_PUSH_SIGNATURE, signature);
                    store.putLong(P5EngagePrefs.KEY_PUSH_UPLOADED_AT, System.currentTimeMillis());
                    SdkLogger.debug(TAG + ": push token registered");
                }
            } catch (Exception e) {
                SdkLogger.error(TAG + ": token upload failed", e);
            }
        });
    }

    /**
     * Opts this install in to push: remembers the choice so the token is kept
     * fresh on every app resume, and registers the current token now.
     */
    public static void enable(@NonNull Context context) {
        P5EngagePrefs store = new P5EngagePrefs(context);
        if (store.getString(P5EngagePrefs.KEY_PUSH_SIGNATURE) == null) {
            store.putString(P5EngagePrefs.KEY_PUSH_SIGNATURE, "pending");
        }
        register(context);
    }

    private static String nz(@Nullable String s) {
        return s == null ? "" : s;
    }
}
