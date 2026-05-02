package com.example.purelauncher;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.purelauncher.ui.views.BarChartView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ActivityParentDashboardActivity extends AppCompatActivity {

    private final TelemetryRepository telemetryRepository = new TelemetryRepository();
    private final UserProfileStore userProfileStore = new UserProfileStore();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_parent_dashboard);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.ivSettings).setOnClickListener(v -> open(GlobalSettingsActivity.class));
        findViewById(R.id.btnPolicies).setOnClickListener(v -> open(PolicyManagerActivity.class));
        findViewById(R.id.btnAddTime).setOnClickListener(v -> open(UsageRestrictActivity.class));

        bindLinkedChildTelemetry();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindLinkedChildTelemetry();
    }

    private void open(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }

    private void bindLinkedChildTelemetry() {
        FirebaseUser parentUser = FirebaseAuth.getInstance().getCurrentUser();
        TextView monitoring = findViewById(R.id.tvMonitoring);
        TextView totalTime = findViewById(R.id.tvTotalTime);
        TextView frictionGates = findViewById(R.id.tvFrictionGates);
        BarChartView chart = findViewById(R.id.lineChart);

        if (parentUser == null) {
            monitoring.setText("Monitoring: no parent session");
            chart.setSamples(flatSamples());
            return;
        }

        userProfileStore.getLinkedChildUid(parentUser).addOnCompleteListener(linkTask -> {
            if (!linkTask.isSuccessful() || linkTask.getResult() == null || linkTask.getResult().trim().isEmpty()) {
                monitoring.setText("Monitoring: no child linked");
                totalTime.setText("0h 00m");
                frictionGates.setText("0");
                chart.setSamples(flatSamples());
                return;
            }

            String childUid = linkTask.getResult().trim();
            telemetryRepository.getLatestSnapshotForChild(childUid).addOnCompleteListener(metricsTask -> {
                if (!metricsTask.isSuccessful() || metricsTask.getResult() == null) {
                    monitoring.setText("Monitoring: child linked, waiting for sync");
                    chart.setSamples(flatSamples());
                    return;
                }

                TelemetrySnapshot snapshot = metricsTask.getResult();
                totalTime.setText(formatMinutes(snapshot.weeklyScreenTimeMinutes));
                frictionGates.setText(String.valueOf(snapshot.frictionCount));
                chart.setSamples(normalize(snapshot.dailyUsageMinutes));
                monitoring.setText(
                        "Monitoring child: "
                                + childUid.substring(0, Math.min(6, childUid.length()))
                                + "  |  Notifications " + snapshot.notificationCount
                                + "  |  Unlocks " + snapshot.unlockCount
                                + "  |  Vaulted " + snapshot.vaultedCount
                );
            });
        });
    }

    private String formatMinutes(long minutes) {
        long hours = minutes / 60;
        long remainder = minutes % 60;
        return hours + "h " + remainder + "m";
    }

    private float[] normalize(long[] values) {
        if (values == null || values.length == 0) {
            return flatSamples();
        }
        long max = 1;
        for (long value : values) {
            if (value > max) {
                max = value;
            }
        }

        float[] normalized = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            float scaled = (float) values[i] / (float) max;
            normalized[i] = Math.max(0.1f, Math.min(1f, scaled));
        }
        return normalized;
    }

    private float[] flatSamples() {
        return new float[]{0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f};
    }
}