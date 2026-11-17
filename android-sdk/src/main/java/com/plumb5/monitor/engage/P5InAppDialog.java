package com.plumb5.monitor.engage;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Native renderer for server-defined in-app messages. Supports the old SDK's
 * field types: text, image, input, checkbox, radio, dropdown, rating,
 * countdown and button. No WebView and no JavaScript.
 *
 * content = { "background", "cornerRadius", "cancelable", "formName",
 *             "fields": [ { "type": "...", ... } ] }
 */
public class P5InAppDialog extends DialogFragment {

    private static final String ARG_MESSAGE = "message";

    private interface Reader {
        /** @return the value, or null when the field is empty */
        Object read();
    }

    private static final class FieldBinding {
        final String key;
        final boolean required;
        final Reader reader;
        final View view;

        FieldBinding(String key, boolean required, Reader reader, View view) {
            this.key = key;
            this.required = required;
            this.reader = reader;
            this.view = view;
        }
    }

    private JSONObject message = new JSONObject();
    private JSONObject content = new JSONObject();
    private final List<FieldBinding> bindings = new ArrayList<>();
    private final List<CountDownTimer> timers = new ArrayList<>();
    private boolean acted = false;

    static P5InAppDialog newInstance(String messageJson) {
        P5InAppDialog fragment = new P5InAppDialog();
        Bundle args = new Bundle();
        args.putString(ARG_MESSAGE, messageJson);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String json = getArguments() == null ? null : getArguments().getString(ARG_MESSAGE);
        try {
            message = new JSONObject(json == null ? "{}" : json);
        } catch (JSONException e) {
            message = new JSONObject();
        }
        content = P5InAppManager.content(message);
        setCancelable(content.optBoolean("cancelable", true));
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = inflater.getContext();
        bindings.clear();

        LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(20), dp(20), dp(20));

        JSONArray fields = content.optJSONArray("fields");
        if (fields != null) {
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.optJSONObject(i);
                if (field != null) {
                    View v = buildField(ctx, field);
                    if (v != null) {
                        column.addView(v);
                    }
                }
            }
        }

        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(column);

        GradientDrawable background = new GradientDrawable();
        background.setColor(parseColor(content.optString("background"), Color.WHITE));
        background.setCornerRadius(dp(content.optInt("cornerRadius", 16)));

        FrameLayout root = new FrameLayout(ctx);
        root.setBackground(background);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (content.optBoolean("cancelable", true)) {
            TextView close = new TextView(ctx);
            close.setText("✕");
            close.setTextSize(18);
            close.setTextColor(Color.GRAY);
            close.setPadding(dp(12), dp(8), dp(12), dp(8));
            close.setContentDescription("Close");
            close.setOnClickListener(v -> dismissAllowingStateLoss());
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            root.addView(close, lp);
        }
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!acted && getArguments() != null && !getArguments().getBoolean("viewed", false)) {
            getArguments().putBoolean("viewed", true);
            P5Engage.track("INAPP_VIEW", baseProps());
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        for (CountDownTimer t : timers) {
            t.cancel();
        }
        timers.clear();
        if (!acted) {
            P5Engage.track("INAPP_CLOSE", baseProps());
        }
        P5InAppManager.onDialogClosed();
    }

    // -------------------------------------------------------------- fields

    @Nullable
    private View buildField(Context ctx, JSONObject f) {
        String type = f.optString("type").toLowerCase(Locale.ROOT);
        switch (type) {
            case "text":
                return buildText(ctx, f);
            case "image":
                return buildImage(ctx, f);
            case "input":
                return buildInput(ctx, f);
            case "checkbox":
                return buildCheckbox(ctx, f);
            case "radio":
                return buildRadio(ctx, f);
            case "dropdown":
                return buildDropdown(ctx, f);
            case "rating":
                return buildRating(ctx, f);
            case "countdown":
                return buildCountdown(ctx, f);
            case "button":
                return buildButton(ctx, f);
            default:
                return null;
        }
    }

    private View buildText(Context ctx, JSONObject f) {
        TextView tv = new TextView(ctx);
        tv.setText(f.optString("text"));
        tv.setTextSize(f.optInt("size", 16));
        tv.setTextColor(parseColor(f.optString("color"), Color.BLACK));
        if (f.optBoolean("bold", false)) {
            tv.setTypeface(Typeface.DEFAULT_BOLD);
        }
        tv.setGravity(gravityOf(f.optString("align")));
        tv.setLayoutParams(rowParams());
        return tv;
    }

    private View buildImage(Context ctx, JSONObject f) {
        ImageView iv = new ImageView(ctx);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setLayoutParams(rowParams());
        iv.setVisibility(View.GONE);
        P5ImageLoader.load(f.optString("url"), iv, () -> iv.setVisibility(View.VISIBLE), null);
        final JSONObject action = f.optJSONObject("action");
        if (action != null) {
            iv.setOnClickListener(v -> {
                Map<String, String> props = baseProps();
                props.put("target", "image");
                P5Engage.track("INAPP_CLICK", props);
                runAction(action);
            });
        }
        return iv;
    }

    private View buildInput(Context ctx, JSONObject f) {
        final EditText et = new EditText(ctx);
        et.setHint(f.optString("hint", f.optString("text")));
        et.setTextColor(parseColor(f.optString("color"), Color.BLACK));
        et.setSingleLine(!"multiline".equals(f.optString("inputType")));
        switch (f.optString("inputType", "text").toLowerCase(Locale.ROOT)) {
            case "email":
                et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                break;
            case "phone":
                et.setInputType(InputType.TYPE_CLASS_PHONE);
                break;
            case "number":
                et.setInputType(InputType.TYPE_CLASS_NUMBER);
                break;
            case "multiline":
                et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                break;
            default:
                et.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        et.setLayoutParams(rowParams());
        register(f, et, () -> {
            String s = et.getText().toString().trim();
            return s.isEmpty() ? null : s;
        });
        return et;
    }

    private View buildCheckbox(Context ctx, JSONObject f) {
        final CheckBox cb = new CheckBox(ctx);
        cb.setText(f.optString("text"));
        cb.setTextColor(parseColor(f.optString("color"), Color.BLACK));
        cb.setLayoutParams(rowParams());
        register(f, cb, () -> cb.isChecked() ? Boolean.TRUE : null);
        return cb;
    }

    private View buildRadio(Context ctx, JSONObject f) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutParams(rowParams());
        if (!f.optString("text").isEmpty()) {
            TextView label = new TextView(ctx);
            label.setText(f.optString("text"));
            label.setTextColor(parseColor(f.optString("color"), Color.BLACK));
            box.addView(label);
        }
        final RadioGroup group = new RadioGroup(ctx);
        group.setOrientation("horizontal".equals(f.optString("orientation"))
                ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        JSONArray options = f.optJSONArray("options");
        for (int i = 0; options != null && i < options.length(); i++) {
            RadioButton rb = new RadioButton(ctx);
            rb.setId(View.generateViewId());
            rb.setText(options.optString(i));
            rb.setTextColor(parseColor(f.optString("color"), Color.BLACK));
            group.addView(rb);
        }
        box.addView(group);
        register(f, box, () -> {
            int id = group.getCheckedRadioButtonId();
            if (id == -1) {
                return null;
            }
            RadioButton rb = group.findViewById(id);
            return rb == null ? null : rb.getText().toString();
        });
        return box;
    }

    private View buildDropdown(Context ctx, JSONObject f) {
        final Spinner spinner = new Spinner(ctx);
        List<String> items = new ArrayList<>();
        JSONArray options = f.optJSONArray("options");
        for (int i = 0; options != null && i < options.length(); i++) {
            items.add(options.optString(i));
        }
        spinner.setAdapter(new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_dropdown_item, items));
        spinner.setLayoutParams(rowParams());
        register(f, spinner, () -> spinner.getSelectedItem() == null ? null : spinner.getSelectedItem().toString());
        return spinner;
    }

    private View buildRating(Context ctx, JSONObject f) {
        final RatingBar bar = new RatingBar(ctx);
        bar.setNumStars(f.optInt("max", 5));
        bar.setStepSize(1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(8);
        bar.setLayoutParams(lp);
        register(f, bar, () -> bar.getRating() > 0 ? (Object) Integer.valueOf((int) bar.getRating()) : null);
        return bar;
    }

    private View buildCountdown(Context ctx, JSONObject f) {
        final TextView tv = new TextView(ctx);
        final String label = f.optString("text");
        tv.setTextSize(f.optInt("size", 18));
        tv.setTextColor(parseColor(f.optString("color"), Color.BLACK));
        tv.setGravity(Gravity.CENTER);
        tv.setLayoutParams(rowParams());
        long seconds = Math.max(1, f.optLong("seconds", 60));
        CountDownTimer timer = new CountDownTimer(seconds * 1000, 1000) {
            @Override
            public void onTick(long ms) {
                long s = ms / 1000;
                tv.setText(String.format(Locale.US, "%s %02d:%02d", label, s / 60, s % 60).trim());
            }

            @Override
            public void onFinish() {
                tv.setText(String.format(Locale.US, "%s 00:00", label).trim());
            }
        };
        timers.add(timer);
        timer.start();
        return tv;
    }

    private View buildButton(Context ctx, final JSONObject f) {
        Button b = new Button(ctx);
        b.setText(f.optString("text", "OK"));
        b.setAllCaps(false);
        b.setTextColor(parseColor(f.optString("color"), Color.WHITE));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(parseColor(f.optString("bg"), Color.parseColor("#1976D2")));
        bg.setCornerRadius(dp(8));
        b.setBackground(bg);
        b.setLayoutParams(rowParams());
        b.setOnClickListener(v -> onButton(f));
        return b;
    }

    // --------------------------------------------------------------- logic

    private void onButton(JSONObject f) {
        JSONObject responses = null;
        if (f.optBoolean("submit", false)) {
            responses = collectResponses();
            if (responses == null) {
                return; // validation failed; the field shows its error
            }
        }
        acted = true;
        Map<String, String> props = baseProps();
        props.put("button", f.optString("text"));
        JSONObject action = f.optJSONObject("action");
        if (action != null) {
            props.put("action", action.optString("type"));
        }
        P5Engage.track("INAPP_CLICK", props);
        if (responses != null) {
            Map<String, String> formProps = baseProps();
            formProps.put("form_name", content.optString("formName", message.optString("name")));
            P5Engage.track("INAPP_FORM_SUBMIT", formProps, responses.toString());
        }
        if (action != null) {
            runAction(action);
        }
        dismissAllowingStateLoss();
    }

    /** @return the responses, or null when a required field is empty */
    @Nullable
    private JSONObject collectResponses() {
        JSONObject out = new JSONObject();
        boolean ok = true;
        for (FieldBinding b : bindings) {
            Object value = b.reader.read();
            if (value == null && b.required) {
                ok = false;
                if (b.view instanceof EditText) {
                    ((EditText) b.view).setError("Required");
                } else {
                    b.view.requestFocus();
                }
            }
            try {
                if (value != null) {
                    out.put(b.key, value);
                }
            } catch (JSONException ignored) {
                // key is never null; nothing to do
            }
        }
        return ok ? out : null;
    }

    private void register(JSONObject f, View view, Reader reader) {
        String key = f.optString("key");
        if (key.isEmpty()) {
            key = "field_" + bindings.size();
        }
        bindings.add(new FieldBinding(key, f.optBoolean("required", false), reader, view));
    }

    private void runAction(JSONObject action) {
        String type = action.optString("type");
        if ("Close".equals(type)) {
            return;
        }
        P5ActionRunner.run(requireContext(), type, action.optString("value"), action.optString("param"));
    }

    private Map<String, String> baseProps() {
        Map<String, String> props = new HashMap<>();
        props.put("message_id", P5InAppManager.idOf(message));
        props.put("message_name", message.optString("name"));
        return props;
    }

    // ------------------------------------------------------------- helpers

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        return lp;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int gravityOf(String align) {
        if ("center".equals(align)) {
            return Gravity.CENTER_HORIZONTAL;
        }
        if ("right".equals(align)) {
            return Gravity.END;
        }
        return Gravity.START;
    }

    private static int parseColor(@Nullable String value, int fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Color.parseColor(value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
