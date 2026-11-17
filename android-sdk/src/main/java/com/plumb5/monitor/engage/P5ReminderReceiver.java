package com.plumb5.monitor.engage;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.plumb5.monitor.core.SdkLogger;

/**
 * Local reminder (the old SDK's "Reminder" button action). Uses an inexact
 * alarm, so no exact-alarm permission is needed. Alarms do not survive a
 * device reboot.
 */
public class P5ReminderReceiver extends BroadcastReceiver {

    static final String EXTRA_TEXT = "p5_reminder_text";
    static final String EXTRA_TITLE = "p5_reminder_title";

    /** @return true if scheduled */
    public static boolean schedule(Context context, int minutes, String title, String text) {
        if (minutes <= 0) {
            return false;
        }
        Context app = context.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return false;
        }
        P5EngagePrefs store = new P5EngagePrefs(app);
        int seq = (int) (store.getLong(P5EngagePrefs.KEY_REMINDER_SEQ) + 1) & 0x7FFFFFFF;
        store.putLong(P5EngagePrefs.KEY_REMINDER_SEQ, seq);
        Intent intent = new Intent(app, P5ReminderReceiver.class);
        intent.putExtra(EXTRA_TEXT, text);
        intent.putExtra(EXTRA_TITLE, title);
        PendingIntent pi = PendingIntent.getBroadcast(app, seq, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + minutes * 60_000L, pi);
        SdkLogger.debug("P5Reminder: scheduled in " + minutes + " min");
        return true;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent == null) {
            return;
        }
        final String title = intent.getStringExtra(EXTRA_TITLE);
        final String text = intent.getStringExtra(EXTRA_TEXT);
        final PendingResult pending = goAsync();
        P5Engage.io().execute(() -> {
            try {
                P5LocalNotifier.show(context, title == null || title.isEmpty() ? "Reminder" : title,
                        text, null, null, null);
            } finally {
                pending.finish();
            }
        });
    }
}
