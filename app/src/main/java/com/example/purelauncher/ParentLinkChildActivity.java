package com.example.purelauncher;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ParentLinkChildActivity extends AppCompatActivity {

    private TextView statusView;
    private final UserProfileStore userProfileStore = new UserProfileStore();
    private ActivityResultLauncher<Intent> qrScanLauncher;
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private static final long TOKEN_EXPIRY_MS = 5 * 60 * 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_parent_link_child);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        statusView = findViewById(R.id.tvLinkStatus);

        qrScanLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                return;
            }
            String rawPayload = result.getData().getStringExtra(ParentQrScannerActivity.EXTRA_QR_PAYLOAD);
            QrCodeUtils.PairingPayload payload = QrCodeUtils.extractPairingPayload(rawPayload);
            if (payload == null) {
                Toast.makeText(this, "Could not read QR code.", Toast.LENGTH_SHORT).show();
                return;
            }
            validateAndLink(payload);
        });

        findViewById(R.id.btnOpenScanner).setOnClickListener(v -> {
            Intent intent = new Intent(this, ParentQrScannerActivity.class);
            qrScanLauncher.launch(intent);
        });

        refreshCurrentLinkState();
    }

    private void linkChild(FirebaseUser user, String childUid) {
        userProfileStore.setLinkedChildUid(user, childUid).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Child linked successfully.", Toast.LENGTH_SHORT).show();
                SessionPrefs.setParentLinkingComplete(this, true);
                Intent intent = new Intent(this, SetupActivity.class);
                intent.putExtra("next_activity", ActivityParentDashboardActivity.class.getName());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                String message = task.getException() == null ? "Failed to link child." : task.getException().getMessage();
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshCurrentLinkState() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            statusView.setText("Status: sign in to link a child.");
            return;
        }
        userProfileStore.getLinkedChildUid(user).addOnCompleteListener(task -> {
            String childUid = task.isSuccessful() ? task.getResult() : null;
            if (childUid == null || childUid.trim().isEmpty()) {
                statusView.setText("Status: no child linked.");
                return;
            }
            statusView.setText("Status: linked");
        });
    }

    private void validateAndLink(QrCodeUtils.PairingPayload payload) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please login again.", Toast.LENGTH_SHORT).show();
            return;
        }

        statusView.setText("Status: validating QR...");
        firestore.collection("child_link_tokens")
                .document(payload.uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        statusView.setText("Status: invalid QR.");
                        Toast.makeText(this, "QR code is invalid or expired.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String serverToken = snapshot.getString("token");
                    Timestamp updatedAt = snapshot.getTimestamp("updatedAt");
                    if (serverToken == null || !serverToken.equals(payload.token)) {
                        statusView.setText("Status: invalid QR.");
                        Toast.makeText(this, "QR code is invalid or expired.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (updatedAt != null) {
                        long ageMs = System.currentTimeMillis() - updatedAt.toDate().getTime();
                        if (ageMs > TOKEN_EXPIRY_MS) {
                            statusView.setText("Status: QR expired.");
                            Toast.makeText(this, "QR code expired. Ask the child to refresh.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    linkChild(user, payload.uid);
                })
                .addOnFailureListener(error -> {
                    statusView.setText("Status: validation failed.");
                    Toast.makeText(this, "Unable to validate QR. Try again.", Toast.LENGTH_SHORT).show();
                });
    }
}
