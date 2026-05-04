package com.example.purelauncher;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class LimitReachedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_limit_reached);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        String packageName = getIntent().getStringExtra("packageName");
        String appName = getIntent().getStringExtra("appName");

        TextView tvAppName = findViewById(R.id.tvAppName);
        ImageView ivAppIcon = findViewById(R.id.ivAppIcon);

        tvAppName.setText(appName);
        if (packageName != null) {
            try {
                Drawable icon = getPackageManager().getApplicationIcon(packageName);
                ivAppIcon.setImageDrawable(icon);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        findViewById(R.id.ivClose).setOnClickListener(v -> returnToVault());
        findViewById(R.id.btnBackToVault).setOnClickListener(v -> returnToVault());
    }

    private void returnToVault() {
        android.content.Intent intent = new android.content.Intent(this, LauncherActivity.class);
        intent.putExtra("openVault", true);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
