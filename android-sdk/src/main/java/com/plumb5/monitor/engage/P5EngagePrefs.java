package com.plumb5.monitor.engage;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * Small plain-text store for engagement caches. Kept separate from the
 * existing encrypted SdkPreferences and the Room database so adding these
 * features needs no schema migration and cannot disturb existing data.
 * Holds nothing secret: cached campaign JSON, geofence list, push token hash.
 */
final class P5EngagePrefs {

    private static final String NAME = "plumb5_engage";

    private final SharedPreferences prefs;

    P5EngagePrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    @Nullable
    String getString(String key) {
        return prefs.getString(key, null);
    }

    void putString(String key, @Nullable String value) {
        prefs.edit().putString(key, value).apply();
    }

    long getLong(String key) {
        return prefs.getLong(key, 0L);
    }

    void putLong(String key, long value) {
        prefs.edit().putLong(key, value).apply();
    }

    void remove(String key) {
        prefs.edit().remove(key).apply();
    }

    static final String KEY_INAPP_JSON = "inapp_json";
    static final String KEY_INAPP_FETCHED_AT = "inapp_fetched_at";
    static final String KEY_GEOFENCES_JSON = "geofences_json";
    static final String KEY_PUSH_SIGNATURE = "push_signature";
    static final String KEY_PUSH_UPLOADED_AT = "push_uploaded_at";
    static final String KEY_REMINDER_SEQ = "reminder_seq";
}
