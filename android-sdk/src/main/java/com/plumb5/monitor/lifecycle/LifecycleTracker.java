package com.plumb5.monitor.lifecycle;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.FrameMetricsAggregator;

import com.plumb5.monitor.Plumb5Monitor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LifecycleTracker
        implements Application.ActivityLifecycleCallbacks {

    private final Plumb5Monitor plumb5Monitor;

    private int startedActivityCount = 0;

    private final Set<Activity> startedActivities =
            new HashSet<>();

    private final FrameMetricsAggregator frameMetricsAggregator;

    public LifecycleTracker(
            @NonNull Plumb5Monitor plumb5Monitor) {

        this.plumb5Monitor = plumb5Monitor;
        this.frameMetricsAggregator = new FrameMetricsAggregator(FrameMetricsAggregator.TOTAL_DURATION);
    }

    @Override
    public synchronized void onActivityCreated(
            @NonNull Activity activity,
            @Nullable Bundle savedInstanceState) {
        // SCREEN_OPENED is handled in onActivityStarted().
    }

    @Override
    public synchronized void onActivityStarted(
            @NonNull Activity activity) {

        /*
         * Ignore duplicate START callbacks for the same
         * Activity instance.
         */
        if (!startedActivities.add(activity)) {

            return;
        }

        boolean wasInBackground =
                startedActivityCount == 0;

        startedActivityCount++;

        plumb5Monitor.trackScreenOpened(
                getScreenName(activity)
        );

        if (wasInBackground) {

            plumb5Monitor.trackAppForeground(
                    getScreenName(activity)
            );
        }

        try {
            frameMetricsAggregator.add(activity);
        } catch (Exception ignored) {}
    }

    @Override
    public synchronized void onActivityResumed(
            @NonNull Activity activity) {

        plumb5Monitor.trackScreenResumed(
                getScreenName(activity)
        );
    }

    @Override
    public synchronized void onActivityPaused(
            @NonNull Activity activity) {
        // Do nothing.
    }

    @Override
    public synchronized void onActivityStopped(
            @NonNull Activity activity) {

        /*
         * Ignore duplicate STOP callbacks.
         */
        if (!startedActivities.remove(activity)) {

            return;
        }

        if (startedActivityCount > 0) {

            startedActivityCount--;
        }

        plumb5Monitor.trackScreenClosed(
                getScreenName(activity)
        );

        if (startedActivityCount == 0) {

            plumb5Monitor.trackAppBackground();
        }

        trackJank(activity);
    }

    private void trackJank(Activity activity) {
        try {
            SparseIntArray[] metrics = frameMetricsAggregator.remove(activity);
            if (metrics != null && metrics[FrameMetricsAggregator.TOTAL_INDEX] != null) {
                SparseIntArray durations = metrics[FrameMetricsAggregator.TOTAL_INDEX];
                int totalFrames = 0;
                int jankyFrames = 0;

                for (int i = 0; i < durations.size(); i++) {
                    int durationMs = durations.keyAt(i);
                    int frameCount = durations.valueAt(i);

                    totalFrames += frameCount;
                    if (durationMs > 16) { // Frames taking longer than 16ms
                        jankyFrames += frameCount;
                    }
                }

                if (totalFrames > 0) {
                    Map<String, String> props = new HashMap<>();
                    props.put("screen", getScreenName(activity));
                    props.put("total_frames", String.valueOf(totalFrames));
                    props.put("janky_frames", String.valueOf(jankyFrames));
                    props.put("jank_percentage", String.format(Locale.US, "%.2f%%", (jankyFrames * 100.0 / totalFrames)));
                    plumb5Monitor.trackEvent("UI_PERFORMANCE", null, props);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public synchronized void onActivitySaveInstanceState(
            @NonNull Activity activity,
            @NonNull Bundle outState) {
        // Nothing to track.
    }

    @Override
    public synchronized void onActivityDestroyed(
            @NonNull Activity activity) {

        /*
         * Defensive cleanup only.
         */
        startedActivities.remove(activity);
    }

    private String getScreenName(
            @NonNull Activity activity) {

        return activity.getClass().getSimpleName();
    }
}
