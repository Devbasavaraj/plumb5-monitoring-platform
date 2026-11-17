package com.plumb5.monitor;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.plumb5.monitor.core.SdkLogger;
import com.plumb5.monitor.location.P5ForegroundLocationTracker;

import java.util.HashSet;
import java.util.Set;

public final class MonitoringInitializer
        implements Application.ActivityLifecycleCallbacks {

    private static volatile MonitoringInitializer INSTANCE;

    private final Application application;
    private final P5ForegroundLocationTracker locationTracker;

    private final Set<Activity> startedActivities =
            new HashSet<>();

    private int startedActivityCount = 0;

    private MonitoringInitializer(
            @NonNull Application application) {

        this.application = application;
        this.locationTracker = new P5ForegroundLocationTracker(application);
    }

    public static synchronized MonitoringInitializer initialize(
            @NonNull Application application,
            @NonNull MonitoringConfig config) {

        if (INSTANCE == null) {

            INSTANCE =
                    new MonitoringInitializer(
                            application
                    );

            application.registerActivityLifecycleCallbacks(
                    INSTANCE
            );
            com.plumb5.monitor.engage.P5ActivityTracker.register(application);

            Plumb5Monitor sdk =
                    Plumb5Monitor.getInstance();

            sdk.trackAppStarted();

            SdkLogger.debug(
                    "MonitoringInitializer: lifecycle callbacks registered"
            );
        }

        return INSTANCE;
    }

    @Override
    public synchronized void onActivityCreated(
            @NonNull Activity activity,
            Bundle savedInstanceState) {

        if (activity instanceof FragmentActivity) {
            ((FragmentActivity) activity).getSupportFragmentManager()
                    .registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, true);
        }

        try {

            Plumb5Monitor.getInstance()
                    .trackEvent(
                            "AUTO_ON_CREATE",
                            activity.getClass()
                                    .getSimpleName()
                    );

        } catch (Exception e) {

            SdkLogger.error(
                    "Lifecycle onActivityCreated failed",
                    e
            );
        }
    }

    @Override
    public synchronized void onActivityStarted(
            @NonNull Activity activity) {

        if (!startedActivities.add(activity)) {
            return;
        }

        boolean wasInBackground =
                startedActivityCount == 0;

        startedActivityCount++;

        SdkLogger.debug(
                "LifecycleTracker: onActivityStarted " +
                        activity.getClass().getSimpleName() +
                        " count=" +
                        startedActivityCount
        );

        try {

            Plumb5Monitor sdk =
                    Plumb5Monitor.getInstance();

            if (wasInBackground) {

                locationTracker.start();


                sdk.trackAppForeground(
                        activity.getClass()
                                .getSimpleName()
                );
            }

        } catch (Exception e) {

            SdkLogger.error(
                    "Lifecycle foreground tracking failed",
                    e
            );
        }
    }

    @Override
    public synchronized void onActivityResumed(
            @NonNull Activity activity) {

        try {

            Plumb5Monitor.getInstance()
                    .trackScreenOpened(
                            activity.getClass()
                                    .getSimpleName()
                    );

            Plumb5Monitor.getInstance()
                    .trackScreenResumed(
                            activity.getClass()
                                    .getSimpleName()
                    );

        } catch (Exception e) {

            SdkLogger.error(
                    "Lifecycle resume tracking failed",
                    e
            );
        }
    }

    @Override
    public synchronized void onActivityPaused(
            @NonNull Activity activity) {
        // No tracking here.
    }

    @Override
    public synchronized void onActivityStopped(
            @NonNull Activity activity) {

        if (!startedActivities.remove(activity)) {
            return;
        }

        if (startedActivityCount > 0) {
            startedActivityCount--;
        }

        try {

            Plumb5Monitor.getInstance()
                    .trackScreenClosed(
                            activity.getClass()
                                    .getSimpleName()
                    );

            if (startedActivityCount == 0) {

                locationTracker.stop();

                Plumb5Monitor.getInstance()
                        .trackAppBackground();
            }

        } catch (Exception e) {

            SdkLogger.error(
                    "Lifecycle stop tracking failed",
                    e
            );
        }

        SdkLogger.debug(
                "LifecycleTracker: onActivityStopped " +
                        activity.getClass().getSimpleName() +
                        " count=" +
                        startedActivityCount
        );
    }

    @Override
    public void onActivitySaveInstanceState(
            @NonNull Activity activity,
            @NonNull Bundle outState) {
    }

    @Override
    public synchronized void onActivityDestroyed(
            @NonNull Activity activity) {

        startedActivities.remove(activity);
    }

    private final FragmentManager.FragmentLifecycleCallbacks fragmentLifecycleCallbacks =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                    super.onFragmentResumed(fm, f);
                    try {
                        Plumb5Monitor.getInstance().trackEvent("SCREEN_OPENED", f.getClass().getSimpleName());
                    } catch (Exception ignored) {}
                }
            };
}




