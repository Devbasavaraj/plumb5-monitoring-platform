package com.plumb5.monitor.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.plumb5.monitor.Plumb5Monitor;
import com.plumb5.monitor.R;
import com.plumb5.monitor.database.EventEntity;
import com.plumb5.monitor.database.MonitoringDatabase;

import java.util.List;
import java.util.concurrent.Executors;

public class P5DebugActivity extends AppCompatActivity {

    private TextView tvSessionInfo;
    private TextView tvEventsList;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_p5_debug);

        tvSessionInfo = findViewById(R.id.tvSessionInfo);
        tvEventsList = findViewById(R.id.tvEventsList);
        Button btnRefresh = findViewById(R.id.btnRefresh);

        btnRefresh.setOnClickListener(v -> refreshEvents());

        refreshEvents();
    }

    private void refreshEvents() {
        // Since Plumb5Monitor uses a private sessionId, we might need a getter if we want to show it.
        // For now just show "Active"
        tvSessionInfo.setText("SDK Initialized: " + Plumb5Monitor.getInstance().toString());

        Executors.newSingleThreadExecutor().execute(() -> {
            List<EventEntity> events = MonitoringDatabase.getInstance(this).eventDao().getPendingEvents(50);
            StringBuilder sb = new StringBuilder();
            for (EventEntity event : events) {
                sb.append("[").append(event.getEventName()).append("] ")
                        .append(event.getScreenName() != null ? event.getScreenName() : "")
                        .append("\n");
            }
            final String text = sb.length() > 0 ? sb.toString() : "No pending events in database.";
            runOnUiThread(() -> tvEventsList.setText(text));
        });
    }
}
