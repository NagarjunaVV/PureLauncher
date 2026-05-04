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

        bindStaticTelemetry();
    }

    private void open(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }

    private void bindStaticTelemetry() {
        // Firebase logic for child monitoring removed as per request.
        // This will be re-implemented later.
        TextView monitoring = findViewById(R.id.tvMonitoring);
        TextView totalTime = findViewById(R.id.tvTotalTime);
        TextView frictionGates = findViewById(R.id.tvFrictionGates);
        BarChartView chart = findViewById(R.id.lineChart);

        monitoring.setText("Monitoring: Offline Mode");
        totalTime.setText("0h 00m");
        frictionGates.setText("0");
        chart.setSamples(new float[]{0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f});
    }
}