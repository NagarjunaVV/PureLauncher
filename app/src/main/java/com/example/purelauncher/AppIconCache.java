package com.example.purelauncher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppIconCache {
    private static final ConcurrentHashMap<String, Drawable> cache = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static boolean isPreloaded = false;

    public static synchronized void preload(Context context) {
        if (isPreloaded) return;
        Context appContext = context.getApplicationContext();
        PackageManager pm = appContext.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo app : apps) {
            try {
                Drawable icon = pm.getApplicationIcon(app.packageName);
                cache.put(app.packageName, icon);
            } catch (Exception ignored) {}
        }
        isPreloaded = true;
    }

    public static Drawable getIcon(Context context, String packageName) {
        Drawable cached = cache.get(packageName);
        if (cached != null) {
            return cached;
        }
        try {
            Drawable icon = context.getPackageManager().getApplicationIcon(packageName);
            cache.put(packageName, icon);
            return icon;
        } catch (Exception e) {
            try {
                return context.getPackageManager().getDefaultActivityIcon();
            } catch (Exception ignored) {
                return null;
            }
        }
    }
    
    public static void loadIconAsync(Context context, String packageName, ImageViewCallback callback) {
        Drawable cached = cache.get(packageName);
        if (cached != null) {
            callback.onIconLoaded(cached);
            return;
        }
        executor.execute(() -> {
            Drawable icon = getIcon(context, packageName);
            callback.onIconLoaded(icon);
        });
    }

    public interface ImageViewCallback {
        void onIconLoaded(Drawable icon);
    }
}
