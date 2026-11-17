package com.plumb5.monitor.push;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.plumb5.monitor.Plumb5Monitor;
import com.plumb5.monitor.core.SdkLogger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class P5FireBasePushNotificationService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "p5_notification_channel";
    private static final String CHANNEL_NAME = "Plumb5 Campaigns";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        // The service declared in the manifest receives token refreshes; send
        // the new token to the backend so campaigns keep reaching this device.
        com.plumb5.monitor.engage.P5PushRegistrar.onNewToken(getApplicationContext(), token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        Map<String, String> data = message.getData();
        if (data.isEmpty()) return;

        SdkLogger.debug("Push message received: " + data.toString());

        // Track the campaign receipt
        try {
            Plumb5Monitor.getInstance().trackEvent("PUSH_CAMPAIGN_RECEIVED", null, data);
        } catch (Exception ignored) {}

        String title = data.get("title");
        String body = data.get("message");
        String imageUrl = data.get("imageUrl");

        sendNotification(title, body, imageUrl, data);
    }

    private void sendNotification(String title, String body, String imageUrl, Map<String, String> data) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent;
        String deepLink = data.get("deepLink");

        if (deepLink != null && !deepLink.isEmpty()) {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink));
        } else {
            intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        }

        if (intent == null) {
            intent = new Intent(); // no launcher activity found: avoid a null PendingIntent target
        }
        for (Map.Entry<String, String> entry : data.entrySet()) {
            intent.putExtra(entry.getKey(), entry.getValue());
        }
        // Lets P5ActivityTracker report PUSH_CAMPAIGN_CLICKED when the user taps.
        intent.putExtra("p5_push_click", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Fix: a constant request code made every notification share one
        // PendingIntent, so a later notification could open with the extras of
        // an earlier one. Each notification now gets its own code.
        final int notificationId = (int) (System.currentTimeMillis() & 0x7FFFFFFF);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, notificationId, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // Should ideally be customizable
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (imageUrl != null && !imageUrl.isEmpty()) {
            Bitmap bitmap = getBitmapFromUrl(imageUrl);
            if (bitmap != null) {
                notificationBuilder.setStyle(new NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .setSummaryText(body));
            }
        }

        applyStyle(notificationBuilder, data.get("style"), title, body, data.get("lines"));

        // Action Buttons
        String actionsJson = data.get("actions");
        if (actionsJson != null && !actionsJson.isEmpty()) {
            try {
                JSONArray actions = new JSONArray(actionsJson);
                for (int i = 0; i < Math.min(actions.length(), 3); i++) {
                    JSONObject action = actions.getJSONObject(i);
                    String label = action.getString("label");
                    String link = action.getString("link");

                    Intent actionIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                    PendingIntent actionPendingIntent = PendingIntent.getActivity(this, notificationId + i + 1, actionIntent,
                            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

                    notificationBuilder.addAction(android.R.drawable.ic_menu_view, label, actionPendingIntent);
                }
            } catch (Exception e) {
                SdkLogger.error("Failed to parse notification actions", e);
            }
        }

        notificationManager.notify(notificationId, notificationBuilder.build());
    }

    /**
     * Optional "style" push field: bigtext, inbox or multiline. Anything else
     * (or no style) keeps the original behaviour (BigPicture when imageUrl is set).
     * "lines" holds inbox/multiline rows separated by "|".
     */
    private void applyStyle(NotificationCompat.Builder builder, String style, String title,
                            String body, String lines) {
        if (style == null) {
            return;
        }
        switch (style.toLowerCase(java.util.Locale.ROOT)) {
            case "bigtext":
                builder.setStyle(new NotificationCompat.BigTextStyle().bigText(body).setBigContentTitle(title));
                break;
            case "inbox":
            case "multiline": {
                NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle();
                inbox.setBigContentTitle(title);
                if (lines != null && !lines.isEmpty()) {
                    for (String line : lines.split("\\|")) {
                        inbox.addLine(line.trim());
                    }
                } else if (body != null) {
                    inbox.addLine(body);
                }
                builder.setStyle(inbox);
                break;
            }
            default:
                break;
        }
    }

    private Bitmap getBitmapFromUrl(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            SdkLogger.error("Error downloading push image", e);
            return null;
        }
    }
}
