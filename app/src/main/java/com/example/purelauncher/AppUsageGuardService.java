package com.example.purelauncher;

import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
 * 1. If the app is in the vault and its limit is reached, it blocks the app.
 * 2. If the app is in the vault and was not unlocked via the friction gate, it shows the gate.
 */
public class AppUsageGuardService extends Service {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastTopPackage = "";
    private String activePackage = "";
    private long activePackageStartedAt = 0L;
    private String currentBlockPackage = "";
    private String scheduledLimitPackage = "";
    private long scheduledLimitAt = 0L;
    
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                // Clear last unlocked when screen goes off so re-entry requires friction
                VaultPrefs.setLastUnlockedPkg(context, "");
            }
        }
    };

    private final Runnable monitorRunnable = new Runnable() {
        @Override
        public void run() {
            checkCurrentAppUsage();
            handler.postDelayed(this, 1000); // Check every 1 second for "immediate" response
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(monitorRunnable);
        if (intent != null && intent.hasExtra("watchPackage")) {
            String watchPackage = intent.getStringExtra("watchPackage");
            if (watchPackage != null && !watchPackage.trim().isEmpty()) {
                activePackage = watchPackage;
                activePackageStartedAt = System.currentTimeMillis();
                scheduleLimitBlockIfNeeded(watchPackage);
            }
        }
        handler.post(monitorRunnable);
        return START_STICKY;
    }

    private void checkCurrentAppUsage() {
        String topPackage = getTopPackageName();
        if (topPackage == null) return;

        Set<String> vaulted = VaultPrefs.getVaultedPackages(this);

        // Logic for re-triggering friction on app switch or re-entry
        if (!topPackage.equals(lastTopPackage)) {
            activePackage = topPackage;
            activePackageStartedAt = System.currentTimeMillis();
            if (!topPackage.equals(currentBlockPackage)) {
                currentBlockPackage = "";
            }
            // If we moved away FROM a vaulted app, clear the "unlocked" status
            // so that if they go back to it (even from RAM), the gate appears again.
            if (vaulted.contains(lastTopPackage) && !topPackage.equals(lastTopPackage)) {
                VaultPrefs.setLastUnlockedPkg(this, "");
            }
            lastTopPackage = topPackage;
        }

        if (vaulted.contains(topPackage)) {
            // 1. Check App Limit
            int limitMinutes = VaultPrefs.getAppLimitMinutes(this, topPackage);
            if (limitMinutes > 0) {
                long currentMs = getLiveUsageMs(topPackage);
                scheduleLimitBlockIfNeeded(topPackage);
                if (currentMs >= limitMinutes * 60_000L) {
                    blockApp(topPackage);
                    return; // Don't check friction if blocked
                }
            }

            // 2. Check Friction Gate
            // If the top app is vaulted but it's not recorded as "recently unlocked"
            if (!topPackage.equals(VaultPrefs.getLastUnlockedPkg(this))) {
                // Also ensure we aren't already showing the gate (which is in our own package)
                if (!topPackage.equals(getPackageName())) {
                    showFrictionGate(topPackage);
                }
            }
        }
    }

    private void blockApp(String packageName) {
        if (packageName.equals(currentBlockPackage)) {
            return;
        }
        currentBlockPackage = packageName;
        Intent intent = new Intent(this, LimitReachedActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("packageName", packageName);
        intent.putExtra("appName", getAppName(packageName));
        startActivity(intent);
    }

    private void showFrictionGate(String packageName) {
        Intent intent = new Intent(this, DialogFrictionGateActivity.class);
        // FLAG_ACTIVITY_REORDER_TO_FRONT helps if it's already in the back stack
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.putExtra("packageName", packageName);
        intent.putExtra("appName", getAppName(packageName));
        startActivity(intent);
    }

    private void scheduleLimitBlockIfNeeded(String packageName) {
        int limitMinutes = VaultPrefs.getAppLimitMinutes(this, packageName);
        if (limitMinutes <= 0) return;
        long remainingMs = limitMinutes * 60_000L - getLiveUsageMs(packageName);
        if (remainingMs <= 0L) {
            blockApp(packageName);
            return;
        }
        long targetAt = System.currentTimeMillis() + Math.min(remainingMs, 60_000L);
        if (packageName.equals(scheduledLimitPackage) && scheduledLimitAt > System.currentTimeMillis()) {
            return;
        }
        scheduledLimitPackage = packageName;
        scheduledLimitAt = targetAt;
        handler.postDelayed(() -> {
            scheduledLimitPackage = "";
            scheduledLimitAt = 0L;
            String topPackage = getTopPackageName();
            boolean stillActive = packageName.equals(topPackage)
                    || (topPackage == null && packageName.equals(activePackage))
                    || (getPackageName().equals(topPackage) && packageName.equals(activePackage));
            if (stillActive
                    && VaultPrefs.getAppLimitMinutes(this, packageName) > 0
                    && getLiveUsageMs(packageName) >= VaultPrefs.getAppLimitMinutes(this, packageName) * 60_000L) {
                blockApp(packageName);
            }
        }, Math.min(remainingMs, 60_000L));
    }

    private String getTopPackageName() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return activePackage;
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - 10 * 60_000L, now);
        String foregroundPackage = activePackage == null ? "" : activePackage;
        long lastForegroundAt = activePackageStartedAt;
        if (events != null) {
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                if ((type == UsageEvents.Event.ACTIVITY_RESUMED
                        || type == UsageEvents.Event.MOVE_TO_FOREGROUND)
                        && event.getTimeStamp() >= lastForegroundAt) {
                    foregroundPackage = event.getPackageName();
                    lastForegroundAt = event.getTimeStamp();
                } else if ((type == UsageEvents.Event.ACTIVITY_PAUSED
                        || type == UsageEvents.Event.ACTIVITY_STOPPED
                        || type == UsageEvents.Event.MOVE_TO_BACKGROUND)
                        && event.getPackageName() != null
                        && event.getPackageName().equals(foregroundPackage)
                        && event.getTimeStamp() >= lastForegroundAt) {
                    foregroundPackage = "";
                    lastForegroundAt = event.getTimeStamp();
                }
            }
        }
        if (foregroundPackage != null && !foregroundPackage.trim().isEmpty()) {
            return foregroundPackage;
        }

        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000L, now);
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

    private long getLiveUsageMs(String packageName) {
        TelemetryRepository repo = new TelemetryRepository();
        long usageMs = repo.getAppUsageMsForDate(this, LocalDate.now(), packageName);
        if (packageName.equals(activePackage) && activePackageStartedAt > 0L) {
            usageMs += Math.max(0L, System.currentTimeMillis() - activePackageStartedAt);
        }
        return usageMs;
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
        unregisterReceiver(screenReceiver);
        handler.removeCallbacks(monitorRunnable);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
