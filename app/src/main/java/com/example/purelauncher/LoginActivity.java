package com.example.purelauncher;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

public class LoginActivity extends AppCompatActivity {

    private EditText emailField;
    private EditText passwordField;
    private TextView errorText;
    private TextView subtitleText;
    private Button loginButton;
    private View pbLoading;
    private FirebaseAuth auth;
    private UserProfileStore profileStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

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

        auth = FirebaseAuth.getInstance();
        profileStore = new UserProfileStore();

        emailField = findViewById(R.id.etEmail);
        passwordField = findViewById(R.id.etPassword);
        errorText = findViewById(R.id.tvError);
        subtitleText = findViewById(R.id.tvSubtitle);
        loginButton = findViewById(R.id.btnLogin);
        pbLoading = findViewById(R.id.pbLoading);

        SessionPrefs.Role role = SessionPrefs.getRole(this);
        if (role == SessionPrefs.Role.PARENT) {
            subtitleText.setText("Sign in to monitor your child and manage limits.");
        } else {
            subtitleText.setText("Sign in to continue your intentional digital habits.");
        }

        loginButton.setOnClickListener(v -> login());
        findViewById(R.id.tvGoSignup).setOnClickListener(v -> {
            startActivity(new Intent(this, SignupActivity.class));
        });
    }

    private void login() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Enter a valid email address.");
            return;
        }
        if (password.isEmpty()) {
            showError("Enter your password.");
            return;
        }

        setLoading(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        resolveRoleAndRoute();
                        return;
                    }
                    setLoading(false);
                    Exception exception = task.getException();
                    showError(mapFirebaseError(exception));
                });
    }

    private void resolveRoleAndRoute() {
        profileStore.getRole(auth.getCurrentUser()).addOnCompleteListener(roleTask -> {
            if (!roleTask.isSuccessful()) {
                setLoading(false);
                showError("Login succeeded, but role check failed. Try again.");
                auth.signOut();
                return;
            }

            SessionPrefs.Role serverRole = roleTask.getResult();
            if (serverRole == null) {
                setLoading(false);
                showError("Account role profile is missing. Please contact support.");
                auth.signOut();
                return;
            }

            SessionPrefs.Role localRole = SessionPrefs.getRole(this);
            if (localRole != null && localRole != serverRole) {
                setLoading(false);
                auth.signOut();
                showError("Login failed: You are on the " + localRole + " setup page, but this account is registered as a " + serverRole + ".");
                return;
            }

            // Session check for ALL accounts (Parent and Child)
            String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            profileStore.claimSession(auth.getUid(), deviceId).addOnCompleteListener(sessionTask -> {
                setLoading(false);
                if (sessionTask.isSuccessful() && Boolean.TRUE.equals(sessionTask.getResult())) {
                    proceedToRoute(serverRole);
                } else {
                    auth.signOut();
                    showError("This account is already active on another device.");
                }
            });
        });
    }

    private void proceedToRoute(SessionPrefs.Role serverRole) {
        SessionPrefs.setRole(this, serverRole);
        if (serverRole == SessionPrefs.Role.PARENT) {
            startActivity(new Intent(this, ParentLinkChildActivity.class));
        } else {
            SessionPrefs.setChildAuthComplete(this, true);
            Intent intent = new Intent(this, SetupActivity.class);
            intent.putExtra("next_activity", LauncherActivity.class.getName());
            startActivity(intent);
        }
        finish();
    }

    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        loginButton.setText(loading ? "" : "Log In");
        if (pbLoading != null) {
            pbLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void showError(String message) {
        errorText.setVisibility(View.VISIBLE);
        errorText.setText(message);
    }

    private String mapFirebaseError(Exception exception) {
        if (exception instanceof FirebaseAuthInvalidUserException) {
            return "No account found for this email.";
        }
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            return "Incorrect email or password.";
        }
        if (exception instanceof FirebaseNetworkException) {
            return "Network error. Check connection and try again.";
        }
        if (exception instanceof FirebaseTooManyRequestsException) {
            return "Too many attempts. Try again later.";
        }
        return "Login failed. Please try again.";
    }
}
