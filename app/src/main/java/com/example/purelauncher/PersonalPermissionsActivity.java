package com.example.purelauncher;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class PersonalPermissionsActivity extends AppCompatActivity {

    private enum PermissionStep {
        OVERLAY,
        DEFAULT_HOME,
        USAGE_ACCESS,
        NOTIFICATION_ACCESS,
        /** Shown only on API 33+ where POST_NOTIFICATIONS must be granted at runtime. */
        POST_NOTIFICATIONS
    }

    private PermissionStep[] steps;
    private int currentStepIndex = 0;

    private TextView stepCount;
    private TextView title;
    private TextView body;
    private TextView status;
    private Button grantButton;
    private Button backButton;
    private Button nextButton;

    private ActivityResultLauncher<Intent> settingsLauncher;
    private ActivityResultLauncher<Intent> roleRequestLauncher;
    /** Handles the POST_NOTIFICATIONS runtime dialog on API 33+. */
    private ActivityResultLauncher<String> notificationPermLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_personal_permissions);

        View main = findViewById(R.id.main);
        final int basePaddingLeft = main.getPaddingLeft();
        final int basePaddingTop = main.getPaddingTop();
        final int basePaddingRight = main.getPaddingRight();
        final int basePaddingBottom = main.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    basePaddingLeft + systemBars.left,
                    basePaddingTop + systemBars.top,
                    basePaddingRight + systemBars.right,
                    basePaddingBottom + systemBars.bottom
            );
            return insets;
        });

        // POST_NOTIFICATIONS only exists on API 33+; skip the step on earlier versions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            steps = new PermissionStep[]{
                    PermissionStep.DEFAULT_HOME,
                    PermissionStep.OVERLAY,
                    PermissionStep.USAGE_ACCESS,
                    PermissionStep.NOTIFICATION_ACCESS,
                    PermissionStep.POST_NOTIFICATIONS
            };
        } else {
            steps = new PermissionStep[]{
                    PermissionStep.DEFAULT_HOME,
                    PermissionStep.OVERLAY,
                    PermissionStep.USAGE_ACCESS,
                    PermissionStep.NOTIFICATION_ACCESS
            };
        }

        stepCount = findViewById(R.id.tvStepCount);
        title = findViewById(R.id.tvPermissionTitle);
        body = findViewById(R.id.tvPermissionBody);
        status = findViewById(R.id.tvPermissionStatus);
        grantButton = findViewById(R.id.btnGrant);
        backButton = findViewById(R.id.btnBack);
        nextButton = findViewById(R.id.btnNext);

        settingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> renderCurrentStep()
        );
        roleRequestLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> renderCurrentStep()
        );
        notificationPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> renderCurrentStep()
        );

        grantButton.setOnClickListener(v -> openPermissionSettings(steps[currentStepIndex]));
        backButton.setOnClickListener(v -> {
            if (currentStepIndex == 0) {
                finish();
                return;
            }
            currentStepIndex -= 1;
            renderCurrentStep();
        });
        nextButton.setOnClickListener(v -> {
            PermissionStep step = steps[currentStepIndex];
            if (!isGranted(step)) {
                Toast.makeText(this, "Grant this permission to continue.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentStepIndex == steps.length - 1) {
                SessionPrefs.setPersonalPermissionsComplete(this, true);
                Intent intent = new Intent(this, AuthenticationActivity.class);
                startActivity(intent);
                finish();
                return;
            }
            currentStepIndex += 1;
            renderCurrentStep();
        });

        renderCurrentStep();
        // Auto-trigger first permission request if not granted
        if (!isGranted(steps[0])) {
            // Delay to ensure UI is rendered first
            findViewById(R.id.main).postDelayed(() -> {
                openPermissionSettings(steps[0]);
            }, 500);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderCurrentStep();
    }

    private void renderCurrentStep() {
        PermissionStep step = steps[currentStepIndex];
        int stepNumber = currentStepIndex + 1;
        stepCount.setText("Step " + stepNumber + " of " + steps.length);

        if (step == PermissionStep.DEFAULT_HOME) {
            title.setText("Set default home screen");
            body.setText("Set PureLauncher as your default launcher so all focus features work consistently.");
        } else if (step == PermissionStep.OVERLAY) {
            title.setText("Allow overlay access");
            body.setText("PureLauncher needs overlay access to show intentional friction prompts over distracting apps.");
        } else if (step == PermissionStep.USAGE_ACCESS) {
            title.setText("Allow usage access");
            body.setText("Usage access powers app-time tracking and lock rules, so we can reduce screen-time distractions.");
        } else if (step == PermissionStep.NOTIFICATION_ACCESS) {
            title.setText("Allow notification access");
            body.setText("Notification access lets PureLauncher count alerts and sync notification activity for the parent dashboard.");
        } else {
            // POST_NOTIFICATIONS (API 33+ only)
            title.setText("Allow notification permission");
            body.setText("Android 13+ requires explicit permission to show notifications. Tap \"Grant\" to allow.");
        }

        boolean granted = isGranted(step);
        status.setText(granted ? "Status: Granted" : "Status: Required");
        grantButton.setText(granted ? "Granted" : "Grant Permission");
        grantButton.setEnabled(!granted);

        backButton.setEnabled(currentStepIndex > 0);
        backButton.setAlpha(currentStepIndex > 0 ? 1f : 0.55f);
        nextButton.setEnabled(granted);
        nextButton.setAlpha(granted ? 1f : 0.55f);
        nextButton.setText(currentStepIndex == steps.length - 1 ? "Continue" : "Next");
    }

    private void openPermissionSettings(PermissionStep step) {
        if (step == PermissionStep.OVERLAY) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            settingsLauncher.launch(intent);
            return;
        }

        if (step == PermissionStep.DEFAULT_HOME) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                roleRequestLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME));
                return;
            }
            Intent fallback = new Intent(Settings.ACTION_HOME_SETTINGS);
            settingsLauncher.launch(fallback);
            return;
        }

        if (step == PermissionStep.USAGE_ACCESS) {
            Intent usageIntent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            settingsLauncher.launch(usageIntent);
            return;
        }

        if (step == PermissionStep.POST_NOTIFICATIONS) {
            // Runtime permission dialog — only reachable on API 33+ (guarded by step list).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
            return;
        }

        // NOTIFICATION_ACCESS — system notification listener settings
        Intent notificationIntent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        settingsLauncher.launch(notificationIntent);
    }

    private boolean isGranted(PermissionStep step) {
        if (step == PermissionStep.OVERLAY) {
            return Settings.canDrawOverlays(this);
        }
        if (step == PermissionStep.DEFAULT_HOME) {
            return isDefaultLauncher();
        }
        if (step == PermissionStep.USAGE_ACCESS) {
            return hasUsageAccess();
        }
        if (step == PermissionStep.POST_NOTIFICATIONS) {
            // Only reachable on API 33+; always true on older versions (step is excluded).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return ContextCompat.checkSelfPermission(this,
                        Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            }
            return true;
        }
        return hasNotificationAccess();
    }

    private boolean isDefaultLauncher() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        PackageManager packageManager = getPackageManager();
        android.content.pm.ResolveInfo resolveInfo = packageManager.resolveActivity(
                homeIntent,
                PackageManager.MATCH_DEFAULT_ONLY
        );
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return false;
        }
        return getPackageName().equals(resolveInfo.activityInfo.packageName);
    }

    private boolean hasUsageAccess() {
        AppOpsManager appOpsManager = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        if (appOpsManager == null) {
            return false;
        }
        int mode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    getPackageName()
            );
        } else {
            mode = appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    getPackageName()
            );
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean hasNotificationAccess() {
        String enabledListeners = Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners"
        );
        if (enabledListeners == null || enabledListeners.trim().isEmpty()) {
            return false;
        }
        return enabledListeners.contains(getPackageName());
    }
}
