package com.example.purelauncher;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class DialogFrictionGateActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.dialog_friction_gate);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (SessionPrefs.getRole(this) == SessionPrefs.Role.CHILD) {
            TelemetryLocalStore.incrementFriction(this);
            new TelemetryRepository().syncCurrentChild(this);
        }

        findViewById(R.id.ivClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnUnlock).setOnClickListener(v -> {
            if (SessionPrefs.getRole(this) == SessionPrefs.Role.CHILD) {
                TelemetryLocalStore.incrementFriction(this);
                new TelemetryRepository().syncCurrentChild(this);
            }
            finish();
        });
        findViewById(R.id.tvChangedMind).setOnClickListener(v -> finish());
    }
}