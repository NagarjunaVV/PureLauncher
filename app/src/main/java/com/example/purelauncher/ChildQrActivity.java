package com.example.purelauncher;

import android.os.Bundle;
import android.widget.Button;
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

        TextView tokenView = findViewById(R.id.tvQrTokenValue);
        ImageView qrPreview = findViewById(R.id.ivQrPreview);

        if (!NetworkUtils.isOnline(this)) {
            tokenView.setText("No internet connection");
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
                        tokenView.setText(dynamicToken);
                        try {
                            qrPreview.setImageBitmap(QrCodeUtils.generateQrBitmap(pairingPayload, 600));
                        } catch (WriterException e) {
                            qrPreview.setImageDrawable(null);
                            tokenView.setText("QR unavailable");
                        }
                    })
                    .addOnFailureListener(error -> {
                        qrPreview.setImageDrawable(null);
                        tokenView.setText("Failed to load QR");
                    });
        }

        Button backHome = findViewById(R.id.btnBackHome);
        backHome.setOnClickListener(v -> finish());

    }
}
