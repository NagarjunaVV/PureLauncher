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

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user == null ? "child" : user.getUid();
        String pairingPayload = QrCodeUtils.buildPairingPayload(uid);

        TextView tokenView = findViewById(R.id.tvQrTokenValue);
        tokenView.setText(uid);

        ImageView qrPreview = findViewById(R.id.ivQrPreview);
        try {
            qrPreview.setImageBitmap(QrCodeUtils.generateQrBitmap(pairingPayload, 600));
        } catch (WriterException e) {
            qrPreview.setImageDrawable(null);
            tokenView.setText(uid + "\nQR unavailable");
        }

        Button backHome = findViewById(R.id.btnBackHome);
        backHome.setOnClickListener(v -> finish());

        Button logout = findViewById(R.id.btnLogout);
        logout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            SessionPrefs.setChildAuthComplete(this, false);
            SessionPrefs.setPersonalPermissionsComplete(this, false);
            SessionPrefs.setPersonalTourComplete(this, false);
            startActivity(new android.content.Intent(this, MainActivity.class));
            finishAffinity();
        });
    }
}
