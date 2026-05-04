package com.example.purelauncher;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

public class AuthenticationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionPrefs.Role role = SessionPrefs.getRole(this);
        if (role == SessionPrefs.Role.PARENT) {
            openParentAuthenticationStep();
            return;
        }
        if (role == SessionPrefs.Role.CHILD) {
            openChildAuthenticationStep();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_authentication);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.tvBiometric).setOnClickListener(v -> openPostLoginDestination());
        findViewById(R.id.key0).setOnClickListener(v -> openPostLoginDestination());
    }

    @Override
    public void onBackPressed() {
        // Allow back navigation through the setup flow
        super.onBackPressed();
    }

    private void openChildAuthenticationStep() {
        Intent intent;
        if (!SessionPrefs.isPersonalPermissionsComplete(this)
                || !RequiredPermissions.allGranted(this)) {
            SessionPrefs.setPersonalPermissionsComplete(this, false);
            intent = new Intent(this, PersonalPermissionsActivity.class);
            intent.putExtra("permissionRevoked", true);
        } else if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            SessionPrefs.setChildAuthComplete(this, true);
            intent = new Intent(this, LauncherActivity.class);
        } else {
            intent = new Intent(this, LoginActivity.class);
        }
        startActivity(intent);
        finish();
    }

    private void openParentAuthenticationStep() {
        Intent intent;
        if (!SessionPrefs.isParentTourComplete(this)) {
            intent = new Intent(this, ParentFeatureTourActivity.class);
        } else if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            intent = new Intent(this, LoginActivity.class);
        } else if (!SessionPrefs.isParentLinkingComplete(this)) {
            intent = new Intent(this, ParentLinkChildActivity.class);
        } else {
            intent = new Intent(this, ActivityParentDashboardActivity.class);
        }
        startActivity(intent);
        finish();
    }

    private void openPostLoginDestination() {
        SessionPrefs.Role role = SessionPrefs.getRole(this);
        Intent intent;
        if (role == SessionPrefs.Role.PARENT) {
            intent = new Intent(this, ActivityParentDashboardActivity.class);
        } else if (role == SessionPrefs.Role.CHILD) {
            intent = new Intent(this, LauncherActivity.class);
        } else {
            SessionPrefs.setOnboardingComplete(this, false);
            intent = new Intent(this, OnboardingActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
