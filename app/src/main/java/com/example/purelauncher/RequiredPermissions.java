package com.example.purelauncher;

import android.app.AppOpsManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

final class RequiredPermissions {

    private RequiredPermissions() {
    }

    static boolean allGranted(Context context) {
        return isDefaultLauncher(context)
                && Settings.canDrawOverlays(context)
                && hasUsageAccess(context);
    }

    static boolean isDefaultLauncher(Context context) {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        PackageManager packageManager = context.getPackageManager();
        android.content.pm.ResolveInfo resolveInfo = packageManager.resolveActivity(
                homeIntent,
                PackageManager.MATCH_DEFAULT_ONLY
        );
        return resolveInfo != null
                && resolveInfo.activityInfo != null
                && context.getPackageName().equals(resolveInfo.activityInfo.packageName);
    }

    static boolean hasUsageAccess(Context context) {
        AppOpsManager appOpsManager =
                (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOpsManager == null) {
            return false;
        }
        int mode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.getPackageName()
            );
        } else {
            mode = appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.getPackageName()
            );
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    static Intent defaultHomeIntent(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = context.getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME);
            }
        }
        return new Intent(Settings.ACTION_HOME_SETTINGS);
    }
}
