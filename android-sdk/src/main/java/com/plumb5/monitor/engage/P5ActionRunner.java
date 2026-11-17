package com.plumb5.monitor.engage;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.plumb5.monitor.core.SdkLogger;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Runs the button actions an in-app message can define (same set as the old
 * Plumb5 SDK): Screen, Deeplink, Browser, Copy, Call, Share, Sms, Reminder,
 * Event, Permission, Close.
 *
 * Call opens the dialer (ACTION_DIAL) instead of placing the call, so the
 * SDK does not need the CALL_PHONE permission.
 */
public final class P5ActionRunner {

    private static final String TAG = "P5ActionRunner";
    static final int PERMISSION_REQUEST_CODE = 5052;

    private P5ActionRunner() {
    }

    /** Rejects schemes that must never be opened from server-supplied content. */
    static boolean isSafeLink(@Nullable String link) {
        if (link == null) {
            return false;
        }
        String scheme = Uri.parse(link.trim()).getScheme();
        if (scheme == null) {
            return false;
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        return !(scheme.equals("javascript") || scheme.equals("file")
                || scheme.equals("content") || scheme.equals("intent")
                || scheme.equals("data") || scheme.equals("android-app"));
    }

    public static void run(Context context, @Nullable String type, @Nullable String value, @Nullable String param) {
        if (type == null || type.isEmpty()) {
            return;
        }
        final String v = value == null ? "" : value;
        final String p = param == null ? "" : param;
        try {
            switch (type) {
                case "Screen": {
                    if (v.isEmpty()) {
                        return;
                    }
                    Intent intent = new Intent();
                    // Only activities of the host app may be opened.
                    intent.setClassName(context.getPackageName(), v);
                    for (Map.Entry<String, String> e : P5Engage.parsePairs(p).entrySet()) {
                        intent.putExtra(e.getKey(), e.getValue());
                    }
                    start(context, intent);
                    break;
                }
                case "Deeplink": {
                    if (!isSafeLink(v)) {
                        return;
                    }
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(v));
                    for (Map.Entry<String, String> e : P5Engage.parsePairs(p).entrySet()) {
                        intent.putExtra(e.getKey(), e.getValue());
                    }
                    start(context, intent);
                    break;
                }
                case "Browser": {
                    String lower = v.toLowerCase(Locale.ROOT);
                    if (lower.startsWith("http://") || lower.startsWith("https://")) {
                        start(context, new Intent(Intent.ACTION_VIEW, Uri.parse(v)));
                    }
                    break;
                }
                case "Copy": {
                    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null && !v.isEmpty()) {
                        cm.setPrimaryClip(ClipData.newPlainText("text label", v));
                        Toast.makeText(context, "Copied successfully.", Toast.LENGTH_SHORT).show();
                    }
                    break;
                }
                case "Call": {
                    if (!v.isEmpty()) {
                        start(context, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(v))));
                    }
                    break;
                }
                case "Share": {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_SUBJECT, v);
                    send.putExtra(Intent.EXTRA_TEXT, p);
                    start(context, Intent.createChooser(send, "Share via"));
                    break;
                }
                case "Sms": {
                    Intent sms = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(v)));
                    sms.putExtra("sms_body", p);
                    start(context, sms);
                    break;
                }
                case "Reminder": {
                    int minutes;
                    try {
                        minutes = Integer.parseInt(v.trim());
                    } catch (NumberFormatException e) {
                        return;
                    }
                    P5ReminderReceiver.schedule(context, minutes, "Reminder", p);
                    break;
                }
                case "Event": {
                    if (!v.isEmpty()) {
                        Map<String, String> props = new HashMap<>();
                        props.put(v, p);
                        P5Engage.track("Custom-InApp-Event", props);
                    }
                    break;
                }
                case "Permission": {
                    if (context instanceof Activity && v.startsWith("android.permission.")) {
                        ActivityCompat.requestPermissions((Activity) context, new String[]{v},
                                PERMISSION_REQUEST_CODE);
                    }
                    break;
                }
                default:
                    break; // "Close" and unknown types: nothing to run
            }
        } catch (ActivityNotFoundException e) {
            SdkLogger.debug(TAG + ": no app can handle action " + type);
        } catch (RuntimeException e) {
            SdkLogger.error(TAG + ": action " + type + " failed", e);
        }
    }

    private static void start(Context context, Intent intent) {
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }
}
