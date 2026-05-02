package com.example.purelauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Persists the ordered list of app-shortcut packages shown on the widget page. */
final class WidgetPrefs {

    private static final String PREFS  = "purelauncher_widgets";
    private static final String KEY    = "shortcut_packages";
    private static final String DELIM  = "|||";

    private WidgetPrefs() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static List<String> getPackages(Context ctx) {
        String raw = prefs(ctx).getString(KEY, "");
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\\|\\|\\|")));
    }

    static void add(Context ctx, String pkg) {
        List<String> list = getPackages(ctx);
        if (!list.contains(pkg)) list.add(pkg);
        save(ctx, list);
    }

    static void remove(Context ctx, String pkg) {
        List<String> list = getPackages(ctx);
        list.remove(pkg);
        save(ctx, list);
    }

    private static void save(Context ctx, List<String> list) {
        prefs(ctx).edit().putString(KEY, String.join(DELIM, list)).apply();
    }
}
