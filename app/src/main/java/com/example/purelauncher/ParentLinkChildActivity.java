package com.example.purelauncher;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ParentLinkChildActivity extends AppCompatActivity {

    private EditText childUidInput;
    private TextView statusView;
    private final UserProfileStore userProfileStore = new UserProfileStore();
    private ActivityResultLauncher<Void> cameraScanLauncher;
    private ActivityResultLauncher<String[]> galleryPickerLauncher;

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

        childUidInput = findViewById(R.id.etChildUid);
        statusView = findViewById(R.id.tvLinkStatus);

        cameraScanLauncher = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap == null) {
                Toast.makeText(this, "Scan canceled.", Toast.LENGTH_SHORT).show();
                return;
            }
            applyScannedBitmap(bitmap);
        });

        galleryPickerLauncher = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) {
                Toast.makeText(this, "No image selected.", Toast.LENGTH_SHORT).show();
                return;
            }
            decodeGalleryImage(uri);
        });

        findViewById(R.id.btnLinkTyped).setOnClickListener(v -> linkTypedChild());
        findViewById(R.id.btnScanQr).setOnClickListener(v -> cameraScanLauncher.launch(null));
        findViewById(R.id.btnGallery).setOnClickListener(v -> galleryPickerLauncher.launch(new String[]{"image/*"}));
        findViewById(R.id.btnUnlink).setOnClickListener(v -> unlinkChild());

        findViewById(R.id.btnContinue).setOnClickListener(v -> {
            SessionPrefs.setParentLinkingComplete(this, true);
            startActivity(new Intent(this, ActivityParentDashboardActivity.class));
            finish();
        });

        refreshCurrentLinkState();
    }

    private void linkTypedChild() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please login again.", Toast.LENGTH_SHORT).show();
            return;
        }
        String childUid = childUidInput.getText().toString().trim();
        if (childUid.isEmpty()) {
            Toast.makeText(this, "Enter child UID from child QR screen.", Toast.LENGTH_SHORT).show();
            return;
        }
        linkChild(user, childUid);
    }

    private void applyScannedBitmap(Bitmap bitmap) {
        try {
            String rawText = QrCodeUtils.decodeQrBitmap(bitmap);
            String childUid = QrCodeUtils.extractChildUid(rawText);
            if (childUid == null || childUid.isEmpty()) {
                Toast.makeText(this, "Could not read QR code.", Toast.LENGTH_SHORT).show();
                return;
            }
            childUidInput.setText(childUid);
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                linkChild(user, childUid);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Unable to decode QR image.", Toast.LENGTH_SHORT).show();
        }
    }

    private void decodeGalleryImage(Uri uri) {
        try {
            Bitmap bitmap;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                bitmap = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(getContentResolver(), uri));
            } else {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            }
            applyScannedBitmap(bitmap);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open image.", Toast.LENGTH_SHORT).show();
        }
    }

    private void linkChild(FirebaseUser user, String childUid) {
        userProfileStore.setLinkedChildUid(user, childUid).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Child linked successfully.", Toast.LENGTH_SHORT).show();
                refreshCurrentLinkState();
            } else {
                String message = task.getException() == null ? "Failed to link child." : task.getException().getMessage();
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void unlinkChild() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please login again.", Toast.LENGTH_SHORT).show();
            return;
        }
        userProfileStore.unlinkLinkedChild(user).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Link removed.", Toast.LENGTH_SHORT).show();
                childUidInput.setText("");
                refreshCurrentLinkState();
            } else {
                Toast.makeText(this, "Failed to remove link.", Toast.LENGTH_SHORT).show();
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
            statusView.setText("Status: linked to " + childUid.trim());
            childUidInput.setText(childUid.trim());
        });
    }
}
