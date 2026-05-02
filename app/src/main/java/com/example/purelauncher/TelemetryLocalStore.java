package com.example.purelauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

final class TelemetryLocalStore {

    private static final String PREFS_NAME = "purelauncher_telemetry_local";
    private static final String KEY_DAY              = "day";
    private static final String KEY_NOTIFICATION_COUNT = "notification_count";
    private static final String KEY_FRICTION_COUNT   = "friction_count";
    /** Per-day total prefix: "nd_{date}" */
    private static final String PFX_DAY_NOTIF = "nd_";
    /** Per-app per-day prefix: "na_{date}_{pkg}" */
    private static final String PFX_APP_NOTIF = "na_";

    private TelemetryLocalStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void ensureSameDay(Context context) {
        String today = LocalDate.now().toString();
        SharedPreferences p = prefs(context);
        if (!today.equals(p.getString(KEY_DAY, ""))) {
            p.edit()
                    .putString(KEY_DAY, today)
                    .putInt(KEY_NOTIFICATION_COUNT, 0)
                    .putInt(KEY_FRICTION_COUNT, 0)
                    .apply();
        }
    }

    /** Increments today's total AND per-package count (call from NotificationListenerService). */
    static void incrementNotification(Context context, String packageName) {
        ensureSameDay(context);
        String today = LocalDate.now().toString();
        SharedPreferences p = prefs(context);
        SharedPreferences.Editor ed = p.edit();

        // Legacy rolling counter used by home card
        ed.putInt(KEY_NOTIFICATION_COUNT, p.getInt(KEY_NOTIFICATION_COUNT, 0) + 1);

        // Persistent per-day total (survives day rollover — keyed by date string)
        String dayKey = PFX_DAY_NOTIF + today;
        ed.putInt(dayKey, p.getInt(dayKey, 0) + 1);

        // Per-app for today
        if (packageName != null && !packageName.isEmpty()) {
            String appKey = PFX_APP_NOTIF + today + "_" + packageName;
            ed.putInt(appKey, p.getInt(appKey, 0) + 1);
        }
        ed.apply();
    }

    static int getNotificationCount(Context context) {
        ensureSameDay(context);
        return prefs(context).getInt(KEY_NOTIFICATION_COUNT, 0);
    }

    /**
     * 7-day notification totals ending at {@code endDate} (index 6 = endDate).
     */
    static long[] getNotificationTotalsForWeek(Context context, LocalDate endDate) {
        SharedPreferences p = prefs(context);
        long[] totals = new long[7];
        for (int i = 0; i < 7; i++) {
            String date = endDate.minusDays(6 - i).toString();
            totals[i] = p.getInt(PFX_DAY_NOTIF + date, 0);
        }
        return totals;
    }

    /** Per-app notification counts for a specific date. */
    static Map<String, Integer> getPerAppNotificationsForDate(Context context, LocalDate date) {
        SharedPreferences p = prefs(context);
        String prefix = PFX_APP_NOTIF + date.toString() + "_";
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            if (e.getKey().startsWith(prefix) && e.getValue() instanceof Integer) {
                result.put(e.getKey().substring(prefix.length()), (Integer) e.getValue());
            }
        }
        return result;
    }

    static void incrementFriction(Context context) {
        ensureSameDay(context);
        SharedPreferences p = prefs(context);
        p.edit().putInt(KEY_FRICTION_COUNT, p.getInt(KEY_FRICTION_COUNT, 0) + 1).apply();
    }

    static int getFrictionCount(Context context) {
        ensureSameDay(context);
        return prefs(context).getInt(KEY_FRICTION_COUNT, 0);
    }
}
