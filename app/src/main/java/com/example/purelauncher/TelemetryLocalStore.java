package com.example.purelauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;

final class TelemetryLocalStore {

    private static final String PREFS_NAME = "purelauncher_telemetry_local";
    private static final String KEY_DAY              = "day";
    private static final String KEY_FRICTION_COUNT   = "friction_count";

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
                    .putInt(KEY_FRICTION_COUNT, 0)
                    .apply();
        }
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
