package com.example.purelauncher;

import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Background service that monitors the currently open app.
 * If the app is in the vault and its limit is reached, it closes the app and shows the LimitReachedActivity.
 */
public class AppUsageGuardService extends Service {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable monitorRunnable = new Runnable() {
        @Override
        public void run() {
            checkCurrentAppUsage();
            handler.postDelayed(this, 5000); // Check every 5 seconds
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler.post(monitorRunnable);
        return START_STICKY;
    }

    private void checkCurrentAppUsage() {
        String topPackage = getTopPackageName();
        if (topPackage == null) return;

        Set<String> vaulted = VaultPrefs.getVaultedPackages(this);
        if (vaulted.contains(topPackage)) {
            int limitMinutes = VaultPrefs.getAppLimitMinutes(this, topPackage);
            if (limitMinutes > 0) {
                // Get usage for today
                TelemetryRepository repo = new TelemetryRepository();
                List<TelemetryRepository.AppUsageEntry> usageList = repo.getAppUsageForDate(this, LocalDate.now());
                long currentMins = 0;
                for (TelemetryRepository.AppUsageEntry entry : usageList) {
                    if (entry.packageName.equals(topPackage)) {
                        currentMins = entry.minutes;
                        break;
                    }
                }

                if (currentMins >= limitMinutes) {
                    // Block the app
                    Intent intent = new Intent(this, LimitReachedActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    intent.putExtra("packageName", topPackage);
                    intent.putExtra("appName", getAppName(topPackage));
                    startActivity(intent);
                }
            }
        }
    }

    private String getTopPackageName() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000 * 10, now);
        if (stats != null && !stats.isEmpty()) {
            SortedMap<Long, UsageStats> sortedStats = new TreeMap<>();
            for (UsageStats s : stats) {
                sortedStats.put(s.getLastTimeUsed(), s);
            }
            if (!sortedStats.isEmpty()) {
                return sortedStats.get(sortedStats.lastKey()).getPackageName();
            }
        }
        return null;
    }

    private String getAppName(String packageName) {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0)).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(monitorRunnable);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
