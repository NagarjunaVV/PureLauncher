package com.example.purelauncher;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.zxing.WriterException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ChildQrActivity facilitates the secure pairing process by generating a unique 
 * association token in Firestore. This token is represented as a QR code, 
 * which the parent scans to establish a linked relationship between the two accounts.
 */
public class ChildQrActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_child_qr);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        TextView statusView = findViewById(R.id.tvQrStatus);
        ImageView qrPreview = findViewById(R.id.ivQrPreview);

        if (!NetworkUtils.isOnline(this)) {
            if (statusView != null) {
                statusView.setText("No internet connection");
            }
            qrPreview.setImageDrawable(null);
        } else {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            String uid = user == null ? "child" : user.getUid();
            String dynamicToken = UUID.randomUUID().toString();

            Map<String, Object> payload = new HashMap<>();
            payload.put("uid", uid);
            payload.put("token", dynamicToken);
            payload.put("updatedAt", FieldValue.serverTimestamp());

            FirebaseFirestore.getInstance()
                    .collection("child_link_tokens")
                    .document(uid)
                    .set(payload)
                    .addOnSuccessListener(unused -> {
                        String pairingPayload = QrCodeUtils.buildPairingPayload(uid, dynamicToken);
                        if (statusView != null) {
                            statusView.setText("Ready to scan");
                        } // Exception Handling With Firestore
                        try {
                            qrPreview.setImageBitmap(QrCodeUtils.generateQrBitmap(pairingPayload, 600));
                        } catch (WriterException e) {
                            qrPreview.setImageDrawable(null);
                            if (statusView != null) {
                                statusView.setText("QR unavailable");
                            }
                        }
                    })
                    .addOnFailureListener(error -> {
                        qrPreview.setImageDrawable(null);
                        if (statusView != null) {
                            statusView.setText("Failed to load QR");
                        }
                    });
        }
    }
}
