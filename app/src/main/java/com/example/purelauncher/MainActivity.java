package com.example.purelauncher;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private final UserProfileStore profileStore = new UserProfileStore();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionPrefs.isOnboardingComplete(this)) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            profileStore.getRole(FirebaseAuth.getInstance().getCurrentUser())
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            SessionPrefs.Role serverRole = task.getResult();
                            SessionPrefs.Role localRole = SessionPrefs.getRole(this);
                            if (localRole != null && localRole != serverRole) {
                                Toast.makeText(this,
                                        "Role synced to " + serverRole.name().toLowerCase() + " account.",
                                        Toast.LENGTH_SHORT).show();
                            }
                            SessionPrefs.setRole(this, serverRole);
                            route(serverRole);
                            return;
                        }
                        FirebaseAuth.getInstance().signOut();
                        startActivity(new Intent(this, LoginActivity.class));
                        finish();
                    });
            return;
        }

        route(SessionPrefs.getRole(this));
    }

    private void route(SessionPrefs.Role role) {
        Intent nextIntent;
        if (role == SessionPrefs.Role.PARENT
                && !SessionPrefs.isParentTourComplete(this)) {
            nextIntent = new Intent(this, ParentFeatureTourActivity.class);
        } else if (role == SessionPrefs.Role.PARENT
                && FirebaseAuth.getInstance().getCurrentUser() == null) {
            nextIntent = new Intent(this, LoginActivity.class);
        } else if (role == SessionPrefs.Role.PARENT
                && !SessionPrefs.isParentLinkingComplete(this)) {
            nextIntent = new Intent(this, ParentLinkChildActivity.class);
        } else if (role == SessionPrefs.Role.PARENT) {
            nextIntent = new Intent(this, ActivityParentDashboardActivity.class);
        } else if (role == SessionPrefs.Role.CHILD
                && !SessionPrefs.isPersonalTourComplete(this)) {
            nextIntent = new Intent(this, PersonalFeatureTourActivity.class);
        } else if (role == SessionPrefs.Role.CHILD
                && !SessionPrefs.isPersonalPermissionsComplete(this)) {
            nextIntent = new Intent(this, PersonalPermissionsActivity.class);
        } else if (role == SessionPrefs.Role.CHILD
            && (!SessionPrefs.isChildAuthComplete(this)
            || FirebaseAuth.getInstance().getCurrentUser() == null)) {
            nextIntent = new Intent(this, AuthenticationActivity.class);
        } else if (role == SessionPrefs.Role.CHILD) {
            nextIntent = new Intent(this, LauncherActivity.class);
        } else {
            nextIntent = new Intent(this, LoginActivity.class);
        }
        startActivity(nextIntent);
        finish();
    }
}