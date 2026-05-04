package com.example.purelauncher;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.purelauncher.ui.views.CircularCounterView;

import java.time.LocalDate;
import java.util.List;

public class DialogFrictionGateActivity extends AppCompatActivity {

    private String packageName;
    private String appName;
    private int requiredClicks;
    private int currentClicks = 0;

    private TextView tvClickCount;
    private CircularCounterView circularCounter;
    private Button btnUnlock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.dialog_friction_gate);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        packageName = getIntent().getStringExtra("packageName");
        appName = getIntent().getStringExtra("appName");

        if (packageName == null) {
            finish();
            return;
        }

        checkLimitAndUsage();

        // Setup UI
        ImageView ivAppIcon = findViewById(R.id.ivAppIcon);
        TextView tvAppName = findViewById(R.id.tvAppName);
        tvClickCount = findViewById(R.id.tvClickCount);
        circularCounter = findViewById(R.id.circularCounter);
        btnUnlock = findViewById(R.id.btnUnlock);
        TextView tvLimit = findViewById(R.id.tvLimit);

        tvAppName.setText(appName);
        try {
            Drawable icon = getPackageManager().getApplicationIcon(packageName);
            ivAppIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            ivAppIcon.setImageResource(R.drawable.ic_placeholder_app);
        }

        int limitMinutes = VaultPrefs.getAppLimitMinutes(this, packageName);
        if (limitMinutes > 0) {
            int hours = limitMinutes / 60;
            int mins = limitMinutes % 60;
            tvLimit.setText("Limit: " + (hours > 0 ? hours + "h " : "") + mins + "m");
            tvLimit.setVisibility(View.VISIBLE);
        } else {
            tvLimit.setVisibility(View.GONE);
        }

        requiredClicks = VaultPrefs.getRequiredClicks(this, packageName);
        updateUI();

        findViewById(R.id.ivClose).setOnClickListener(v -> finish());
        findViewById(R.id.tvChangedMind).setOnClickListener(v -> finish());

        btnUnlock.setOnClickListener(v -> {
            currentClicks++;
            if (currentClicks >= requiredClicks) {
                launchApp();
            } else {
                updateUI();
                if (SessionPrefs.getRole(this) == SessionPrefs.Role.CHILD) {
                    TelemetryLocalStore.incrementFriction(this);
                    new TelemetryRepository().syncCurrentChild(this);
                }
            }
        });
    }

    private void checkLimitAndUsage() {
        int limitMinutes = VaultPrefs.getAppLimitMinutes(this, packageName);
        if (limitMinutes <= 0) return;

        new Thread(() -> {
            TelemetryRepository repo = new TelemetryRepository();
            List<TelemetryRepository.AppUsageEntry> usage = repo.getAppUsageForDate(this, LocalDate.now());
            long currentMins = 0;
            for (TelemetryRepository.AppUsageEntry entry : usage) {
                if (entry.packageName.equals(packageName)) {
                    currentMins = entry.minutes;
                    break;
                }
            }

            if (currentMins >= limitMinutes) {
                runOnUiThread(() -> {
                    Intent intent = new Intent(this, LimitReachedActivity.class);
                    intent.putExtra("packageName", packageName);
                    intent.putExtra("appName", appName);
                    startActivity(intent);
                    finish();
                });
            }
        }).start();
    }

    private void updateUI() {
        int remaining = requiredClicks - currentClicks;
        tvClickCount.setText(String.valueOf(remaining));
        float progress = (float) currentClicks / requiredClicks;
        circularCounter.setProgress(progress);
        
        if (currentClicks > 0) {
            btnUnlock.setText("KEEP TAPPING...");
        } else {
            btnUnlock.setText("TAP TO UNLOCK (" + requiredClicks + ")");
        }
    }

    private void launchApp() {
        VaultPrefs.incrementDailyClicks(this, packageName);
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(launchIntent);
        } else {
            Toast.makeText(this, "Could not launch app", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
