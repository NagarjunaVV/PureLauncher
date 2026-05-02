package com.example.purelauncher;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;

public class SetupActivity extends AppCompatActivity {
    private static final String TAG = "SetupActivity";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_setup);

        String nextClass = getIntent().getStringExtra("next_activity");
        CountDownLatch preloadLatch = new CountDownLatch(2);

        new Thread(() -> {
            try {
                AppIconCache.preload(getApplicationContext());
            } finally {
                preloadLatch.countDown();
            }
        }).start();

        new Thread(() -> {
            try {
                TelemetryRepository repo = new TelemetryRepository();
                repo.collectLocalSnapshot(getApplicationContext());
                repo.getDailyUsageForWeekEndingAt(getApplicationContext(), LocalDate.now());
            } finally {
                preloadLatch.countDown();
            }
        }).start();

        new Thread(() -> {
            try {
                preloadLatch.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            mainHandler.post(() -> launchNext(nextClass));
        }).start();
    }

    private void launchNext(String nextClass) {
        if (nextClass != null) {
            try {
                Class<?> clazz = Class.forName(nextClass);
                Intent intent = new Intent(this, clazz);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Unable to resolve next setup activity", e);
            }
        }
        finish();
    }
}
