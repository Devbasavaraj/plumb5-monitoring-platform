package com.plumb5.monitor;

import android.content.Context;

import com.plumb5.monitor.core.CommunicationMode;
import com.plumb5.monitor.core.P5ErrorSeverity;
import com.plumb5.monitor.core.SdkLogger;

import org.json.JSONArray;

/**
 * Public static API wrapper for Plumb5 compatibility.
 * This class provides standard integration methods as documented in the Plumb5 Integration Guide.
 */
public final class P5Engine {

    /**
     * Initializes the Plumb5 SDK.
     *
     * @param context    The application context.
     * @param appKey     The application key provided by Plumb5.
     * @param serviceUrl The service URL for the Plumb5 backend.
     * @param mode       The communication mode (FCM, GCM, or DEFAULT).
     */
    public static void p5Init(Context context, String appKey, String serviceUrl, CommunicationMode mode) {
        Plumb5Monitor.p5Init(context, appKey, serviceUrl, mode);
    }

    /**
     * Sets user information for tracking and personalization.
     *
     * @param context The application context.
     * @param name    The name of the user.
     * @param email   The email address of the user.
     * @param phone   The phone number of the user.
     * @param extra   Additional information or metadata about the user.
     */
    public static void p5SetUserInfo(Context context, String name, String email, String phone, String extra) {
        if (context == null) return;
        try {
            Plumb5Monitor.getInstance().p5SetUserInfo(context, name, email, phone, extra);
        } catch (IllegalStateException e) {
            SdkLogger.error("P5Engine: SDK not initialized. Call p5Init first.");
        }
    }

    public static void P5Eventdata(Context context, String eventName, String key, String value) {
        if (context == null || eventName == null) return;
        try {
            Plumb5Monitor.getInstance().P5Eventdata(context, eventName, key, value);
        } catch (IllegalStateException e) {
            SdkLogger.error("P5Engine: SDK not initialized.");
        }
    }

    public static void P5Transdata(Context context, JSONArray transData) {
        if (transData == null) return;
        try {
            Plumb5Monitor.getInstance().P5Transdata(context, transData);
        } catch (IllegalStateException e) {
            SdkLogger.error("P5Engine: SDK not initialized.");
        }
    }

    public static void P5PostStaticFormData(Context context, String formId, JSONArray dataList) {
        if (formId == null || dataList == null) return;
        try {
            Plumb5Monitor.getInstance().P5PostStaticFormData(context, formId, dataList);
        } catch (IllegalStateException e) {
            SdkLogger.error("P5Engine: SDK not initialized.");
        }
    }

    public static void showChat(Context context) {
        if (context == null) return;
        try {
            Plumb5Monitor.getInstance().showChat(context);
        } catch (IllegalStateException e) {
            SdkLogger.error("P5Engine: SDK not initialized.");
        }
    }

    public static void setSessionProperty(String key, String value) {
        if (key == null || value == null) return;
        try {
            Plumb5Monitor.getInstance().setSessionProperty(key, value);
        } catch (IllegalStateException e) {
            SdkLogger.error("P5Engine: SDK not initialized.");
        }
    }

    public static void trackError(Throwable throwable, P5ErrorSeverity severity) {
        if (throwable == null) return;
        try {
            Plumb5Monitor.getInstance().trackError(throwable, severity);
        } catch (IllegalStateException e) {
            SdkLogger.error("P5Engine: SDK not initialized.");
        }
    }

    public static void exportLogs(Context context) {
        if (context == null) return;
        String logs = SdkLogger.getExportableLogs();
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Plumb5 SDK Logs");
        intent.putExtra(android.content.Intent.EXTRA_TEXT, logs);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(android.content.Intent.createChooser(intent, "Export Plumb5 Logs"));
        } catch (Exception ignored) {}
    }

    public static void setConsent(Context context, boolean granted) {
        try {
            Plumb5Monitor.getInstance().setConsent(granted);
        } catch (IllegalStateException e) {
            SdkLogger.error("P5Engine: SDK not initialized.");
        }
    }

    public static void wipeData(Context context) {
        try {
            Plumb5Monitor.getInstance().wipeData();
        } catch (IllegalStateException e) {
            SdkLogger.error("P5Engine: SDK not initialized.");
        }
    }

    public static void showDebugConsole(Context context) {
        if (context == null) return;
        android.content.Intent intent = new android.content.Intent(context, com.plumb5.monitor.ui.P5DebugActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------
    // Engagement features (added; everything above is unchanged)
    // ------------------------------------------------------------------

    /**
     * Turns on push for this install: asks for the Android 13+ notification
     * permission if needed and registers the FCM token with the Plumb5 backend.
     * Call after p5Init (typically in your launcher activity, or after login so
     * the token is linked to the identified user).
     */
    public static void p5EnablePush(android.app.Activity activity) {
        if (activity == null) return;
        p5RequestNotificationPermission(activity);
        com.plumb5.monitor.engage.P5PushRegistrar.enable(activity);
    }

    /** Asks for POST_NOTIFICATIONS on Android 13+; does nothing on older versions. */
    public static void p5RequestNotificationPermission(android.app.Activity activity) {
        if (activity == null || android.os.Build.VERSION.SDK_INT < 33) return;
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                activity, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(activity,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 5051);
        }
    }

    /** Call from onNewIntent() of a singleTop launcher activity so notification taps are counted. */
    public static void p5TrackPushClick(android.content.Intent intent) {
        com.plumb5.monitor.engage.P5ActivityTracker.trackPushClick(intent);
    }

    /** Downloads in-app messages now (otherwise refreshed at most every 15 minutes). */
    public static void p5RefreshCampaigns(Context context) {
        if (context == null) return;
        com.plumb5.monitor.engage.P5InAppManager.refresh(context, true);
    }

    /**
     * Shows the in-app message / form the dashboard registered as MANUAL:{key}.
     * @return true if the message is already downloaded and will be shown
     */
    public static boolean p5ShowInApp(android.app.Activity activity, String key) {
        if (activity == null || key == null) return false;
        return com.plumb5.monitor.engage.P5InAppManager.showManual(activity, key);
    }

    /**
     * Loads the server-defined banner into your own ImageView. Both views stay
     * GONE when there is no banner, so they can stay in your layout.
     */
    public static void p5LoadBanner(android.app.Activity activity,
                                    android.widget.ImageView banner,
                                    android.widget.ImageView closeButton) {
        if (activity == null || banner == null || closeButton == null) return;
        com.plumb5.monitor.engage.P5InAppManager.loadBanner(activity, banner, closeButton);
    }

    /** Shows a local reminder notification after the given number of minutes. */
    public static boolean p5ScheduleReminder(Context context, int minutes, String text) {
        if (context == null) return false;
        return com.plumb5.monitor.engage.P5ReminderReceiver.schedule(context, minutes, "Reminder", text);
    }
}
