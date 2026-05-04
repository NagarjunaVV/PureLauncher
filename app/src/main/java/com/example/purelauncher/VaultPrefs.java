package com.example.purelauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages preferences for the App Vault, including friction types, daily clicks,
 * and app usage limits.
 */
final class VaultPrefs {

    private static final String PREFS_NAME = "purelauncher_vault";
    private static final String KEY_VAULTED_PACKAGES = "vaulted_packages";
    private static final String KEY_FRICTION_TYPE = "friction_type_"; // + package
    private static final String KEY_DAILY_CLICKS = "clicks_"; // + date + package
    private static final String KEY_APP_LIMIT = "limit_"; // + package (in minutes)
    private static final String KEY_LIMIT_LAST_CHANGED = "limit_changed_"; // + package
    private static final String KEY_LAST_UNLOCKED_PKG = "last_unlocked_pkg";

    public static final int FRICTION_NONE = 0;
    public static final int FRICTION_PLUS_ONE = 1;
    public static final int FRICTION_X2 = 2;
    public static final int FRICTION_X3 = 3;

    private VaultPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    static Set<String> getVaultedPackages(Context context) {
        Set<String> value = prefs(context).getStringSet(KEY_VAULTED_PACKAGES, Collections.emptySet());
        return new HashSet<>(value);
    }

    static void setVaultedPackages(Context context, Set<String> packages) {
        prefs(context).edit().putStringSet(KEY_VAULTED_PACKAGES, new HashSet<>(packages)).apply();
    }

    static void addVaultedPackage(Context context, String pkg) {
        Set<String> set = getVaultedPackages(context);
        set.add(pkg);
        setVaultedPackages(context, set);
        // By default, added to vault should have +1 as friction.
        setFrictionType(context, pkg, FRICTION_PLUS_ONE);
    }

    static void removeVaultedPackage(Context context, String pkg) {
        Set<String> set = getVaultedPackages(context);
        set.remove(pkg);
        setVaultedPackages(context, set);
    }

    static int getFrictionType(Context context, String pkg) {
        return prefs(context).getInt(KEY_FRICTION_TYPE + pkg, FRICTION_PLUS_ONE);
    }

    static void setFrictionType(Context context, String pkg, int type) {
        prefs(context).edit().putInt(KEY_FRICTION_TYPE + pkg, type).apply();
    }

    static int getAppLimitMinutes(Context context, String pkg) {
        return prefs(context).getInt(KEY_APP_LIMIT + pkg, 0); 
    }

    static void setAppLimitMinutes(Context context, String pkg, int minutes) {
        String today = LocalDate.now().toString();
        prefs(context).edit()
                .putInt(KEY_APP_LIMIT + pkg, minutes)
                .putString(KEY_LIMIT_LAST_CHANGED + pkg, today)
                .apply();
    }

    static boolean canChangeLimitToday(Context context, String pkg) {
        String today = LocalDate.now().toString();
        String lastChanged = prefs(context).getString(KEY_LIMIT_LAST_CHANGED + pkg, "");
        return !today.equals(lastChanged);
    }

    static int getDailyClicks(Context context, String pkg) {
        String today = LocalDate.now().toString();
        return prefs(context).getInt(KEY_DAILY_CLICKS + today + "_" + pkg, 0);
    }

    static void incrementDailyClicks(Context context, String pkg) {
        String today = LocalDate.now().toString();
        int current = getDailyClicks(context, pkg);
        prefs(context).edit().putInt(KEY_DAILY_CLICKS + today + "_" + pkg, current + 1).apply();
    }

    static int getRequiredClicks(Context context, String pkg) {
        int type = getFrictionType(context, pkg);
        int dailyCount = getDailyClicks(context, pkg);
        
        switch (type) {
            case FRICTION_PLUS_ONE:
                return dailyCount + 1;
            case FRICTION_X2:
                return (int) Math.pow(2, dailyCount);
            case FRICTION_X3:
                return (int) Math.pow(3, dailyCount);
            default:
                return 1;
        }
    }

    static void setLastUnlockedPkg(Context context, String pkg) {
        prefs(context).edit().putString(KEY_LAST_UNLOCKED_PKG, pkg).apply();
    }

    static String getLastUnlockedPkg(Context context) {
        return prefs(context).getString(KEY_LAST_UNLOCKED_PKG, "");
    }
}
