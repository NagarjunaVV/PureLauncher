package com.example.purelauncher;

import android.content.Context;
import android.content.SharedPreferences;

final class SessionPrefs {

    enum Role {
        PARENT,
        CHILD
    }

    private static final String PREFS_NAME = "purelauncher_session";
    private static final String KEY_ONBOARDING_COMPLETE = "onboarding_complete";
    private static final String KEY_ROLE = "role";
    private static final String KEY_PERSONAL_TOUR_COMPLETE = "personal_tour_complete";
    private static final String KEY_PERSONAL_PERMISSIONS_COMPLETE = "personal_permissions_complete";
    private static final String KEY_CHILD_AUTH_COMPLETE = "child_auth_complete";
    private static final String KEY_PARENT_TOUR_COMPLETE = "parent_tour_complete";
    private static final String KEY_PARENT_LINKING_COMPLETE = "parent_linking_complete";

    private SessionPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    static boolean isOnboardingComplete(Context context) {
        return prefs(context).getBoolean(KEY_ONBOARDING_COMPLETE, false);
    }

    static void setOnboardingComplete(Context context, boolean complete) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply();
    }

    static void setRole(Context context, Role role) {
        prefs(context).edit().putString(KEY_ROLE, role.name()).apply();
    }

    static Role getRole(Context context) {
        String rawRole = prefs(context).getString(KEY_ROLE, null);
        if (rawRole == null) {
            return null;
        }
        try {
            return Role.valueOf(rawRole);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static boolean isPersonalTourComplete(Context context) {
        return prefs(context).getBoolean(KEY_PERSONAL_TOUR_COMPLETE, false);
    }

    static void setPersonalTourComplete(Context context, boolean complete) {
        prefs(context).edit().putBoolean(KEY_PERSONAL_TOUR_COMPLETE, complete).apply();
    }

    static boolean isPersonalPermissionsComplete(Context context) {
        return prefs(context).getBoolean(KEY_PERSONAL_PERMISSIONS_COMPLETE, false);
    }

    static void setPersonalPermissionsComplete(Context context, boolean complete) {
        prefs(context).edit().putBoolean(KEY_PERSONAL_PERMISSIONS_COMPLETE, complete).apply();
    }

    static boolean isChildAuthComplete(Context context) {
        return prefs(context).getBoolean(KEY_CHILD_AUTH_COMPLETE, false);
    }

    static void setChildAuthComplete(Context context, boolean complete) {
        prefs(context).edit().putBoolean(KEY_CHILD_AUTH_COMPLETE, complete).apply();
    }

    static boolean isParentTourComplete(Context context) {
        return prefs(context).getBoolean(KEY_PARENT_TOUR_COMPLETE, false);
    }

    static void setParentTourComplete(Context context, boolean complete) {
        prefs(context).edit().putBoolean(KEY_PARENT_TOUR_COMPLETE, complete).apply();
    }

    static boolean isParentLinkingComplete(Context context) {
        return prefs(context).getBoolean(KEY_PARENT_LINKING_COMPLETE, false);
    }

    static void setParentLinkingComplete(Context context, boolean complete) {
        prefs(context).edit().putBoolean(KEY_PARENT_LINKING_COMPLETE, complete).apply();
    }
}