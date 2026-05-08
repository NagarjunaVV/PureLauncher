package com.example.purelauncher;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.UserProfileChangeRequest;

public class SignupActivity extends AppCompatActivity {

    private EditText nameField;
    private EditText emailField;
    private EditText passwordField;
    private EditText confirmPasswordField;
    private TextView errorText;
    private TextView reqLen;
    private TextView reqLetter;
    private TextView reqNumber;
    private TextView reqSymbol;
    private TextView subtitleText;
    private Button signupButton;
    private View pbLoading;
    private FirebaseAuth auth;
    private UserProfileStore profileStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signup);

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

        nameField = findViewById(R.id.etName);
        emailField = findViewById(R.id.etEmail);
        passwordField = findViewById(R.id.etPassword);
        confirmPasswordField = findViewById(R.id.etConfirmPassword);
        reqLen = findViewById(R.id.tvReqLen);
        reqLetter = findViewById(R.id.tvReqLetter);
        reqNumber = findViewById(R.id.tvReqNumber);
        reqSymbol = findViewById(R.id.tvReqSymbol);
        subtitleText = findViewById(R.id.tvSubtitle);
        errorText = findViewById(R.id.tvError);
        signupButton = findViewById(R.id.btnSignup);
        pbLoading = findViewById(R.id.pbLoading);

        SessionPrefs.Role role = SessionPrefs.getRole(this);
        if (role == SessionPrefs.Role.PARENT) {
            subtitleText.setText("Create your parent account to monitor and guide your child.");
        } else {
            subtitleText.setText("Create your child account for intentional device use.");
        }

        passwordField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePasswordState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        signupButton.setOnClickListener(v -> signup());
        findViewById(R.id.tvGoLogin).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
        });

        updatePasswordState();
    }

    private void signup() {
        String normalizedName = NameValidator.normalize(nameField.getText().toString());
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();
        String confirmPassword = confirmPasswordField.getText().toString();

        if (!NameValidator.isValid(normalizedName)) {
            showError("Name must contain only letters and spaces.");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Enter a valid email address.");
            return;
        }

        PasswordValidator.Result passwordResult = PasswordValidator.validate(password);
        if (!passwordResult.isValid()) {
            showError("Password must be 8+ chars and include a letter, number, and symbol.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match.");
            return;
        }

        setLoading(true);
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        updateUserProfileAndContinue(normalizedName);
                        return;
                    }
                    setLoading(false);
                    Exception exception = task.getException();
                    showError(mapFirebaseError(exception));
                });
    }

    private void updateUserProfileAndContinue(String displayName) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            setLoading(false);
            showError("Sign up failed. Please try again.");
            return;
        }

        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build();

        currentUser.updateProfile(profileUpdates).addOnCompleteListener(profileTask -> {
            if (!profileTask.isSuccessful()) {
                setLoading(false);
                showError("Account created, but profile update failed. Please log in.");
                return;
            }

            SessionPrefs.Role role = SessionPrefs.getRole(this);
            if (role == null) {
                setLoading(false);
                showError("Select a role in onboarding before signing up.");
                return;
            }

            profileStore.createProfile(currentUser, displayName, role).addOnCompleteListener(storeTask -> {
                if (!storeTask.isSuccessful()) {
                    setLoading(false);
                    showError("Account created, but role setup failed. Please try logging in again.");
                    return;
                }

                // Initialize session for Child or Parent
                String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                profileStore.claimSession(currentUser.getUid(), deviceId).addOnCompleteListener(sessionTask -> {
                    setLoading(false);
                    proceedToRoute(role);
                });
            });
        });
    }

    private void proceedToRoute(SessionPrefs.Role role) {
        if (role == SessionPrefs.Role.PARENT) {
            Intent intent = new Intent(this, ParentLinkChildActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else {
            SessionPrefs.setChildAuthComplete(this, true);
            Intent intent = new Intent(this, SetupActivity.class);
            intent.putExtra("next_activity", LauncherActivity.class.getName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
        finish();
    }

    private void setLoading(boolean loading) {
        signupButton.setEnabled(!loading);
        signupButton.setText(loading ? "" : "Create Account");
        if (pbLoading != null) {
            pbLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void showError(String message) {
        errorText.setVisibility(View.VISIBLE);
        errorText.setText(message);
    }

    private void updatePasswordState() {
        PasswordValidator.Result result = PasswordValidator.validate(passwordField.getText().toString());
        setRequirementState(reqLen, result.hasMinLength);
        setRequirementState(reqLetter, result.hasLetter);
        setRequirementState(reqNumber, result.hasNumber);
        setRequirementState(reqSymbol, result.hasSymbol);
    }

    private void setRequirementState(TextView view, boolean met) {
        view.setText((met ? "PASS " : "PENDING ") + view.getText().toString().replace("PASS ", "").replace("PENDING ", ""));
        view.setTextColor(getColor(met ? R.color.color_success : R.color.text_secondary));
    }

    private String mapFirebaseError(Exception exception) {
        if (exception instanceof FirebaseAuthUserCollisionException) {
            return "An account with this email already exists.";
        }
        if (exception instanceof FirebaseAuthWeakPasswordException) {
            return "Password is too weak. Use 8+ chars with letter, number, and symbol.";
        }
        if (exception instanceof FirebaseNetworkException) {
            return "Network error. Check connection and try again.";
        }
        if (exception instanceof FirebaseTooManyRequestsException) {
            return "Too many attempts. Try again later.";
        }
        return "Sign up failed. Please try again.";
    }
}
