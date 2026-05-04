package com.example.purelauncher;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class PersonalPermissionsActivity extends AppCompatActivity {

    private enum PermissionStep {
        OVERLAY,
        DEFAULT_HOME,
        USAGE_ACCESS
    }

    private PermissionStep[] steps;
    private int currentStepIndex = 0;

    private TextView stepCount;
    private TextView title;
    private TextView body;
    private TextView status;
    private TextView note;
    private Button grantButton;
    private Button backButton;
    private Button nextButton;

    private ActivityResultLauncher<Intent> settingsLauncher;
    private ActivityResultLauncher<Intent> roleRequestLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_personal_permissions);

        android.view.View main = findViewById(R.id.main);
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

        steps = new PermissionStep[]{
                PermissionStep.DEFAULT_HOME,
                PermissionStep.OVERLAY,
                PermissionStep.USAGE_ACCESS
        };

        stepCount = findViewById(R.id.tvStepCount);
        title = findViewById(R.id.tvPermissionTitle);
        body = findViewById(R.id.tvPermissionBody);
        status = findViewById(R.id.tvPermissionStatus);
        note = findViewById(R.id.tvPermissionNote);
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

        grantButton.setOnClickListener(v -> openPermissionSettings(steps[currentStepIndex]));
        backButton.setOnClickListener(v -> {
            PermissionStep step = steps[currentStepIndex];
            if (isGranted(step)) {
                askToRemovePermissionBeforeBack(step);
                return;
            }
            if (currentStepIndex == 0) {
                Intent intent = new Intent(this, PersonalFeatureTourActivity.class);
                intent.putExtra("openLastPage", true);
                startActivity(intent);
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
                if (!RequiredPermissions.allGranted(this)) {
                    SessionPrefs.setPersonalPermissionsComplete(this, false);
                    Toast.makeText(this, "All required permissions must be granted.", Toast.LENGTH_SHORT).show();
                    renderCurrentStep();
                    return;
                }
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
        }

        boolean granted = isGranted(step);
        boolean permissionRevoked = getIntent().getBooleanExtra("permissionRevoked", false);
        status.setText(granted ? "Status: Granted" : "Status: Required");
        grantButton.setText(granted ? "Granted" : "Grant Permission");
        grantButton.setEnabled(!granted);
        note.setText(permissionRevoked && !RequiredPermissions.allGranted(this)
                ? "Permission not granted. To use the app, please grant the required permission. Until then, the app will not work."
                : "Required items: default home, overlay, and usage access.");

        backButton.setEnabled(true);
        backButton.setAlpha(1f);
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
            roleRequestLauncher.launch(RequiredPermissions.defaultHomeIntent(this));
            return;
        }

        if (step == PermissionStep.USAGE_ACCESS) {
            Intent usageIntent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            settingsLauncher.launch(usageIntent);
        }
    }

    private boolean isGranted(PermissionStep step) {
        if (step == PermissionStep.OVERLAY) {
            return Settings.canDrawOverlays(this);
        }
        if (step == PermissionStep.DEFAULT_HOME) {
            return RequiredPermissions.isDefaultLauncher(this);
        }
        if (step == PermissionStep.USAGE_ACCESS) {
            return RequiredPermissions.hasUsageAccess(this);
        }
        return false;
    }

    private void askToRemovePermissionBeforeBack(PermissionStep step) {
        new AlertDialog.Builder(this, R.style.DarkDialog)
                .setTitle("Remove current permission?")
                .setMessage("To go back, remove this permission in system settings first. PureLauncher will stay on this step until it is removed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open Settings", (dialog, which) -> openPermissionSettings(step))
                .show();
    }
}
