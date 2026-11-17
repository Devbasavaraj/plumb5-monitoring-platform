package com.plumb5.monitor.engage;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.Request;
import okhttp3.Response;

/** Minimal image fetcher with an in-memory cache, for in-app messages and banners. */
final class P5ImageLoader {

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 1024 / 16)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };

    private P5ImageLoader() {
    }

    /** Loads into the view on the main thread. Safe to call from any thread. */
    static void load(@Nullable final String url, @NonNull final ImageView target,
                     @Nullable final Runnable onSuccess, @Nullable final Runnable onFailure) {
        if (url == null || !url.startsWith("http")) {
            if (onFailure != null) {
                P5Engage.runOnMain(onFailure);
            }
            return;
        }
        final Bitmap cached = CACHE.get(url);
        if (cached != null) {
            P5Engage.runOnMain(() -> {
                target.setImageBitmap(cached);
                if (onSuccess != null) {
                    onSuccess.run();
                }
            });
            return;
        }
        P5Engage.io().execute(() -> {
            final Bitmap bitmap = fetchBlocking(url);
            P5Engage.runOnMain(() -> {
                if (bitmap != null) {
                    target.setImageBitmap(bitmap);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                } else if (onFailure != null) {
                    onFailure.run();
                }
            });
        });
    }

    /** Blocking download (decoded and cached). Call off the main thread. */
    @Nullable
    static Bitmap fetchBlocking(@Nullable String url) {
        if (url == null || !url.startsWith("http")) {
            return null;
        }
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            return cached;
        }
        Request request = new Request.Builder().url(url).build();
        try (Response response = P5Engage.http().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            try (InputStream in = response.body().byteStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                if (bitmap != null) {
                    CACHE.put(url, bitmap);
                }
                return bitmap;
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
