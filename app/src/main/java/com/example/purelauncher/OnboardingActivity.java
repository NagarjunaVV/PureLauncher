package com.example.purelauncher;

import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class OnboardingActivity extends AppCompatActivity {

    private RadioButton rbParent;
    private RadioButton rbChild;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_onboarding);
        final android.view.View main = findViewById(R.id.main);
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

        rbParent = findViewById(R.id.rbParent);
        rbChild = findViewById(R.id.rbChild);

        // Check if role is already selected; if so, route to next screen
        if (SessionPrefs.getRole(this) != null) {
            openNextStep();
            return;
        }

        findViewById(R.id.cardParentMode).setOnClickListener(v -> selectRole(SessionPrefs.Role.PARENT));
        findViewById(R.id.cardChildMode).setOnClickListener(v -> selectRole(SessionPrefs.Role.CHILD));
        rbParent.setOnClickListener(v -> selectRole(SessionPrefs.Role.PARENT));
        rbChild.setOnClickListener(v -> selectRole(SessionPrefs.Role.CHILD));

        findViewById(R.id.btnGetStarted).setOnClickListener(v -> openNextStep());
    }

    private void selectRole(SessionPrefs.Role role) {
        boolean isParent = role == SessionPrefs.Role.PARENT;
        rbParent.setChecked(isParent);
        rbChild.setChecked(!isParent);
    }

    private void openNextStep() {
        SessionPrefs.Role selectedRole;
        if (rbParent.isChecked()) {
            selectedRole = SessionPrefs.Role.PARENT;
        } else if (rbChild.isChecked()) {
            selectedRole = SessionPrefs.Role.CHILD;
        } else {
            selectedRole = SessionPrefs.getRole(this);
            if (selectedRole == null) {
                selectedRole = SessionPrefs.Role.CHILD;
            }
        }

        SessionPrefs.setRole(this, selectedRole);
        SessionPrefs.setOnboardingComplete(this, true);
        if (selectedRole == SessionPrefs.Role.CHILD) {
            SessionPrefs.setChildAuthComplete(this, false);
        } else {
            SessionPrefs.setParentLinkingComplete(this, false);
        }

        Intent intent;
        if (selectedRole == SessionPrefs.Role.PARENT) {
            if (!SessionPrefs.isParentTourComplete(this)) {
                intent = new Intent(this, ParentFeatureTourActivity.class);
            } else if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) {
                intent = new Intent(this, LoginActivity.class);
            } else if (!SessionPrefs.isParentLinkingComplete(this)) {
                intent = new Intent(this, ParentLinkChildActivity.class);
            } else {
                intent = new Intent(this, ActivityParentDashboardActivity.class);
            }
        } else if (!SessionPrefs.isPersonalTourComplete(this)) {
            intent = new Intent(this, PersonalFeatureTourActivity.class);
        } else if (!SessionPrefs.isPersonalPermissionsComplete(this)) {
            intent = new Intent(this, PersonalPermissionsActivity.class);
        } else {
            intent = new Intent(this, AuthenticationActivity.class);
        }
        startActivity(intent);
        finish();
    }
}