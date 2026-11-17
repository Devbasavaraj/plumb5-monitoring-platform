package com.plumb5.monitor.engage;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.plumb5.monitor.core.SdkLogger;

import java.util.Map;

/**
 * Shows a notification built locally (reminders, geofence/beacon offers when
 * the app is in the background). Uses the same channel as the push service.
 */
public final class P5LocalNotifier {

    static final String CHANNEL_ID = "p5_notification_channel";
    static final String CHANNEL_NAME = "Plumb5 Campaigns";

    private P5LocalNotifier() {
    }

    /** Call off the main thread when imageUrl is set (it is downloaded). */
    public static void show(Context context, @Nullable String title, @Nullable String body,
                            @Nullable String deepLink, @Nullable String imageUrl,
                            @Nullable Map<String, String> extras) {
        final Context app = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            SdkLogger.debug("P5LocalNotifier: POST_NOTIFICATIONS not granted; skipped");
            return;
        }
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                    new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent intent;
        if (deepLink != null && !deepLink.isEmpty() && P5ActionRunner.isSafeLink(deepLink)) {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink));
        } else {
            intent = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        }
        if (intent == null) {
            intent = new Intent();
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("p5_push_click", true);
        if (extras != null) {
            for (Map.Entry<String, String> e : extras.entrySet()) {
                intent.putExtra(e.getKey(), e.getValue());
            }
        }
        final int id = (int) (System.currentTimeMillis() & 0x7FFFFFFF);
        PendingIntent pi = PendingIntent.getActivity(app, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        int icon = app.getApplicationInfo().icon;
        NotificationCompat.Builder b = new NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(icon != 0 ? icon : android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pi);
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Bitmap bmp = P5ImageLoader.fetchBlocking(imageUrl);
            if (bmp != null) {
                b.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bmp).setSummaryText(body));
            }
        } else if (body != null && body.length() > 40) {
            b.setStyle(new NotificationCompat.BigTextStyle().bigText(body));
        }
        nm.notify(id, b.build());
    }
}
