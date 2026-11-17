package com.plumb5.monitor.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.plumb5.monitor.Plumb5Monitor;
import com.plumb5.monitor.R;

import java.util.HashMap;
import java.util.Map;

/**
 * In-App message dialog UI (v1.4 Feature).
 */
public class P5DialogBox extends DialogFragment {

    private String htmlContent;

    public static P5DialogBox newInstance(String html) {
        P5DialogBox fragment = new P5DialogBox();
        Bundle args = new Bundle();
        args.putString("html", html);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            htmlContent = getArguments().getString("html");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        WebView webView = new WebView(getContext());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new P5JavaScriptInterface(), "Plumb5");
        webView.setWebViewClient(new WebViewClient());
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null);
        
        return webView;
    }

    private class P5JavaScriptInterface {
        @JavascriptInterface
        public void dismiss() {
            // Fix: the old body called dismiss() on itself (infinite recursion).
            // JavascriptInterface calls arrive on a background thread.
            dismissOnUiThread();
        }

        @JavascriptInterface
        public void trackAction(String action) {
            Map<String, String> props = new HashMap<>();
            props.put("action", action);
            Plumb5Monitor.getInstance().trackEvent("CAMPAIGN_ACTION", null, props);
            dismissOnUiThread();
        }
    }

    private void dismissOnUiThread() {
        final android.app.Activity activity = getActivity();
        if (activity == null || !isAdded()) {
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                P5DialogBox.this.dismissAllowingStateLoss();
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.MATCH_PARENT;
            dialog.getWindow().setLayout(width, height);
        }
    }
}
