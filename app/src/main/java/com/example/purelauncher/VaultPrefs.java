package com.example.purelauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class VaultPrefs {

    private static final String PREFS_NAME = "purelauncher_vault";
    private static final String KEY_VAULTED_PACKAGES = "vaulted_packages";

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
    }

    static void removeVaultedPackage(Context context, String pkg) {
        Set<String> set = getVaultedPackages(context);
        set.remove(pkg);
        setVaultedPackages(context, set);
    }
}
