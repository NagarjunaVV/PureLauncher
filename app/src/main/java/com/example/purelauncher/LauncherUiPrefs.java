package com.example.purelauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;

final class LauncherUiPrefs {

    static final String THEME_SYSTEM = "system";
    static final String THEME_LIGHT = "light";
    static final String THEME_DARK = "dark";

    static final String FONT_SIZE_SMALL = "small";
    static final String FONT_SIZE_MEDIUM = "medium";
    static final String FONT_SIZE_LARGE = "large";

    static final String FONT_DEFAULT = "default";
    static final String FONT_MONOSPACE = "monospace";
    static final String FONT_GOLDMAN = "goldman";
    static final String FONT_OPENDYSLEXIC = "opendyslexic";
    static final String FONT_NDOT57 = "ndot57";

    private static final String PREFS = "launcher_ui_prefs";
    private static final String KEY_THEME = "theme";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_FONT = "font";

    private LauncherUiPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String getTheme(Context context) {
        return prefs(context).getString(KEY_THEME, THEME_SYSTEM);
    }

    static void setTheme(Context context, String theme) {
        prefs(context).edit().putString(KEY_THEME, theme).apply();
    }

    static String getFontSize(Context context) {
        return prefs(context).getString(KEY_FONT_SIZE, FONT_SIZE_MEDIUM);
    }

    static void setFontSize(Context context, String fontSize) {
        prefs(context).edit().putString(KEY_FONT_SIZE, fontSize).apply();
    }

    static String getFont(Context context) {
        return prefs(context).getString(KEY_FONT, FONT_DEFAULT);
    }

    static void setFont(Context context, String font) {
        prefs(context).edit().putString(KEY_FONT, font).apply();
    }

    static void applyTheme(Context context) {
        String theme = getTheme(context);
        int mode;
        switch (theme) {
            case THEME_LIGHT:
                mode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case THEME_DARK:
                mode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            case THEME_SYSTEM:
            default:
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    static void applyTypography(View root, Context context) {
        float scale;
        switch (getFontSize(context)) {
            case FONT_SIZE_SMALL:
                scale = 0.9f;
                break;
            case FONT_SIZE_LARGE:
                scale = 1.15f;
                break;
            case FONT_SIZE_MEDIUM:
            default:
                scale = 1f;
                break;
        }
        Typeface typeface = resolveTypeface(getFont(context));
        applyTypographyRecursive(root, typeface, scale);
    }

    private static Typeface resolveTypeface(String font) {
        switch (font) {
            case FONT_MONOSPACE:
                return Typeface.MONOSPACE;
            case FONT_GOLDMAN:
                return Typeface.create("sans-serif-medium", Typeface.NORMAL);
            case FONT_OPENDYSLEXIC:
                return Typeface.create("sans-serif", Typeface.NORMAL);
            case FONT_NDOT57:
                return Typeface.create("sans-serif-condensed", Typeface.NORMAL);
            case FONT_DEFAULT:
            default:
                return Typeface.DEFAULT;
        }
    }

    private static void applyTypographyRecursive(View view, Typeface typeface, float scale) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setTypeface(typeface);
            float sizeInPx = textView.getTextSize();
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sizeInPx * scale);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyTypographyRecursive(group.getChildAt(i), typeface, scale);
            }
        }
    }
}
