package com.plumb5.monitor.engage;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tracks the resumed activity (needed to show in-app dialogs, which the
 * application context cannot do) and picks up notification taps. Registered
 * once by MonitoringInitializer next to the existing lifecycle callbacks.
 */
public final class P5ActivityTracker implements Application.ActivityLifecycleCallbacks {

    private static volatile P5ActivityTracker INSTANCE;
    private static volatile WeakReference<Activity> current = new WeakReference<>(null);

    private P5ActivityTracker() {
    }

    public static synchronized void register(@NonNull Application app) {
        if (INSTANCE == null) {
            INSTANCE = new P5ActivityTracker();
            app.registerActivityLifecycleCallbacks(INSTANCE);
        }
    }

    /** The activity currently in the foreground, or null when the app is in the background. */
    @Nullable
    public static Activity current() {
        return current.get();
    }

    /**
     * Records PUSH_CAMPAIGN_CLICKED if the intent came from a Plumb5
     * notification. Safe to call repeatedly: the marker is removed after use.
     * Call it from onNewIntent as well if your launcher activity is singleTop.
     */
    public static void trackPushClick(@Nullable Intent intent) {
        if (intent == null || !intent.getBooleanExtra("p5_push_click", false)) {
            return;
        }
        intent.removeExtra("p5_push_click");
        Map<String, String> props = new HashMap<>();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            Set<String> keys = extras.keySet();
            for (String key : new String[]{"campaignId", "pushId", "PushId", "title", "p5_inapp_id"}) {
                if (keys.contains(key) && extras.get(key) != null) {
                    props.put(key, String.valueOf(extras.get(key)));
                }
            }
        }
        P5Engage.track("PUSH_CAMPAIGN_CLICKED", props);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        current = new WeakReference<>(activity);
        trackPushClick(activity.getIntent());
        Application app = activity.getApplication();
        P5InAppManager.refresh(app, false);
        P5PushRegistrar.syncIfNeeded(app);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (current.get() == activity) {
            current = new WeakReference<>(null);
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }
}
