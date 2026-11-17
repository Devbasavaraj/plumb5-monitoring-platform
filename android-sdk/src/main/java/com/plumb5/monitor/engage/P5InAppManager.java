package com.plumb5.monitor.engage;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.plumb5.monitor.core.SdkLogger;
import com.plumb5.monitor.core.SdkPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server-driven in-app messaging: dialogs/forms, banners and local
 * notifications. Messages are fetched from {@code GET /api/inapp/active},
 * cached on disk, and matched against events the SDK already records
 * (APP_FOREGROUND, GEOFENCE_ENTER, BEACON_DETECTED, custom events, ...).
 *
 * Message shape:
 * { "id", "name", "type": "dialog|banner|notification", "trigger": "EVENT_NAME" or "MANUAL:key",
 *   "triggerProp", "triggerValue", "maxImpressions", "minIntervalHours", "content": {...} }
 */
public final class P5InAppManager {

    private static final String TAG = "P5InAppManager";
    private static final long REFRESH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(15);
    private static final AtomicBoolean DIALOG_SHOWING = new AtomicBoolean(false);
    private static final AtomicBoolean REFRESHING = new AtomicBoolean(false);

    private P5InAppManager() {
    }

    // ---------------------------------------------------------------- fetch

    /** Downloads the active messages (throttled to once per 15 minutes unless forced). */
    public static void refresh(@NonNull Context context, boolean force) {
        final Context app = context.getApplicationContext();
        P5EngagePrefs store = new P5EngagePrefs(app);
        if (!force && System.currentTimeMillis() - store.getLong(P5EngagePrefs.KEY_INAPP_FETCHED_AT)
                < REFRESH_INTERVAL_MS) {
            return;
        }
        if (!REFRESHING.compareAndSet(false, true)) {
            return;
        }
        P5Engage.io().execute(() -> {
            try {
                refreshBlocking(app);
            } finally {
                REFRESHING.set(false);
            }
        });
    }

    private static void refreshBlocking(Context app) {
        SdkPreferences sdk = new SdkPreferences(app);
        if (!sdk.isConfigured() || !sdk.isEnabled() || !sdk.isConsentGranted()) {
            return;
        }
        String body = P5Engage.get(app, "/api/inapp/active?appId=" + android.net.Uri.encode(
                String.valueOf(sdk.getAppId())));
        if (body == null) {
            return;
        }
        try {
            JSONObject root = new JSONObject(body);
            JSONArray messages = root.optJSONArray("messages");
            if (messages == null) {
                return;
            }
            P5EngagePrefs store = new P5EngagePrefs(app);
            store.putString(P5EngagePrefs.KEY_INAPP_JSON, messages.toString());
            store.putLong(P5EngagePrefs.KEY_INAPP_FETCHED_AT, System.currentTimeMillis());
            SdkLogger.debug(TAG + ": cached " + messages.length() + " in-app messages");
        } catch (JSONException e) {
            SdkLogger.error(TAG + ": bad in-app response", e);
        }
    }

    private static JSONArray cached(Context app) {
        String json = new P5EngagePrefs(app).getString(P5EngagePrefs.KEY_INAPP_JSON);
        if (json == null) {
            return new JSONArray();
        }
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    // ------------------------------------------------------------- triggers

    /**
     * Called for every event the SDK records. Cheap: reads the cached list on
     * the engagement thread and returns immediately.
     */
    public static void onEvent(@NonNull Context context, @Nullable final String eventName,
                               @Nullable final Map<String, String> props) {
        if (eventName == null || eventName.startsWith("INAPP_") || eventName.startsWith("PUSH_")) {
            return; // never trigger on our own tracking events
        }
        final Context app = context.getApplicationContext();
        String cachedJson = new P5EngagePrefs(app).getString(P5EngagePrefs.KEY_INAPP_JSON);
        if (cachedJson == null || cachedJson.length() <= 2) {
            return; // nothing cached: skip scheduling work for every event
        }
        final Map<String, String> copy = props == null ? null : new HashMap<>(props);
        P5Engage.io().execute(() -> {
            try {
                JSONArray messages = cached(app);
                for (int i = 0; i < messages.length(); i++) {
                    JSONObject m = messages.optJSONObject(i);
                    if (m == null || "banner".equals(m.optString("type"))) {
                        continue;
                    }
                    if (!eventName.equalsIgnoreCase(m.optString("trigger"))) {
                        continue;
                    }
                    if (!propMatches(m, copy) || !withinCaps(app, m)) {
                        continue;
                    }
                    if (present(app, m)) {
                        return; // one message per event
                    }
                }
            } catch (Exception e) {
                SdkLogger.error(TAG + ": trigger evaluation failed", e);
            }
        });
    }

    /** Shows the message registered for MANUAL:{key} (the old "static form" use case). */
    public static boolean showManual(@NonNull final Activity activity, @NonNull String key) {
        final Context app = activity.getApplicationContext();
        JSONArray messages = cached(app);
        for (int i = 0; i < messages.length(); i++) {
            JSONObject m = messages.optJSONObject(i);
            if (m != null && ("MANUAL:" + key).equalsIgnoreCase(m.optString("trigger"))
                    && !"banner".equals(m.optString("type"))) {
                final JSONObject message = m;
                P5Engage.io().execute(() -> present(app, message));
                return true;
            }
        }
        refresh(app, true); // maybe not downloaded yet
        return false;
    }

    // ------------------------------------------------------------ rendering

    private static boolean present(Context app, JSONObject message) {
        String type = message.optString("type", "dialog");
        JSONObject content = content(message);
        if ("notification".equals(type)) {
            showNotification(app, message, content);
            return true;
        }
        Activity activity = P5ActivityTracker.current();
        if (activity instanceof FragmentActivity && !activity.isFinishing() && !activity.isDestroyed()) {
            final FragmentActivity fa = (FragmentActivity) activity;
            final String json = message.toString();
            final String id = idOf(message);
            P5Engage.runOnMain(() -> {
                FragmentManager fm = fa.getSupportFragmentManager();
                if (fm.isStateSaved() || !DIALOG_SHOWING.compareAndSet(false, true)) {
                    return;
                }
                try {
                    P5InAppDialog.newInstance(json).show(fm, "p5_inapp");
                    new SdkPreferences(app).incrementCampaignImpressions("inapp_" + id);
                } catch (RuntimeException e) {
                    DIALOG_SHOWING.set(false);
                    SdkLogger.error(TAG + ": could not show dialog", e);
                }
            });
            return true;
        }
        // App is in the background (e.g. a geofence woke it): fall back to a
        // notification when the message carries notification text.
        if (!content.optString("notificationBody").isEmpty()) {
            showNotification(app, message, content);
            return true;
        }
        return false;
    }

    private static void showNotification(Context app, JSONObject message, JSONObject content) {
        String title = content.optString("notificationTitle", content.optString("title"));
        String body = content.optString("notificationBody", content.optString("body"));
        String link = content.optString("deepLink");
        String image = content.optString("imageUrl");
        Map<String, String> extras = new HashMap<>();
        extras.put("p5_inapp_id", idOf(message));
        P5LocalNotifier.show(app, title, body, link.isEmpty() ? null : link, image.isEmpty() ? null : image, extras);
        new SdkPreferences(app).incrementCampaignImpressions("inapp_" + idOf(message));
        Map<String, String> props = new HashMap<>();
        props.put("message_id", idOf(message));
        P5Engage.track("INAPP_VIEW", props);
    }

    static void onDialogClosed() {
        DIALOG_SHOWING.set(false);
    }

    // --------------------------------------------------------------- banner

    /**
     * Fills {@code banner} with the active banner message, if any. The views
     * stay GONE when there is nothing to show, so it is safe to leave them in
     * a layout permanently.
     */
    public static void loadBanner(@NonNull final Activity activity, @NonNull final ImageView banner,
                                  @NonNull final ImageView close) {
        banner.setVisibility(View.GONE);
        close.setVisibility(View.GONE);
        final Context app = activity.getApplicationContext();
        P5Engage.io().execute(() -> {
            JSONObject m = firstBanner(app);
            if (m == null) {
                refreshBlocking(app);
                m = firstBanner(app);
            }
            if (m == null) {
                return;
            }
            final JSONObject message = m;
            final JSONObject content = content(message);
            final String id = idOf(message);
            P5Engage.runOnMain(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                P5ImageLoader.load(content.optString("imageUrl"), banner, () -> {
                    banner.setVisibility(View.VISIBLE);
                    close.setVisibility(content.optBoolean("closable", true) ? View.VISIBLE : View.GONE);
                    new SdkPreferences(app).incrementCampaignImpressions("inapp_" + id);
                    Map<String, String> props = new HashMap<>();
                    props.put("message_id", id);
                    P5Engage.track("INAPP_VIEW", props);
                }, null);
                banner.setOnClickListener(v -> {
                    Map<String, String> props = new HashMap<>();
                    props.put("message_id", id);
                    props.put("target", "banner");
                    P5Engage.track("INAPP_CLICK", props);
                    JSONObject action = content.optJSONObject("action");
                    if (action != null) {
                        P5ActionRunner.run(activity, action.optString("type"),
                                action.optString("value"), action.optString("param"));
                    }
                });
                close.setOnClickListener(v -> {
                    banner.setVisibility(View.GONE);
                    close.setVisibility(View.GONE);
                    Map<String, String> props = new HashMap<>();
                    props.put("message_id", id);
                    P5Engage.track("INAPP_CLOSE", props);
                });
            });
        });
    }

    @Nullable
    private static JSONObject firstBanner(Context app) {
        JSONArray messages = cached(app);
        for (int i = 0; i < messages.length(); i++) {
            JSONObject m = messages.optJSONObject(i);
            if (m != null && "banner".equals(m.optString("type")) && withinCaps(app, m)) {
                return m;
            }
        }
        return null;
    }

    // -------------------------------------------------------------- helpers

    private static boolean propMatches(JSONObject message, @Nullable Map<String, String> props) {
        String key = message.optString("triggerProp");
        if (key.isEmpty()) {
            return true;
        }
        String expected = message.optString("triggerValue");
        String actual = props == null ? null : props.get(key);
        return actual != null && actual.equalsIgnoreCase(expected);
    }

    /** maxImpressions or minIntervalHours of 0 (or absent) means "no limit". */
    private static boolean withinCaps(Context app, JSONObject message) {
        SdkPreferences prefs = new SdkPreferences(app);
        String key = "inapp_" + idOf(message);
        int max = message.optInt("maxImpressions", 0);
        if (max > 0 && prefs.getCampaignImpressions(key) >= max) {
            return false;
        }
        int hours = message.optInt("minIntervalHours", 0);
        if (hours > 0) {
            long since = System.currentTimeMillis() - prefs.getCampaignLastShownTime(key);
            return since >= TimeUnit.HOURS.toMillis(hours);
        }
        return true;
    }

    static String idOf(JSONObject message) {
        return message.optString("id", "0");
    }

    /** "content" may arrive as an object or as a JSON string. */
    static JSONObject content(JSONObject message) {
        JSONObject obj = message.optJSONObject("content");
        if (obj != null) {
            return obj;
        }
        String raw = message.optString("content", "");
        try {
            return raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
}
