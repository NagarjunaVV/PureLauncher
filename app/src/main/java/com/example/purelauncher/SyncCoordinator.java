package com.example.purelauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SyncCoordinator {

    private static final String PREFS_SYNC = "child_sync_prefs";
    private static final String KEY_LAST_SYNC_REQUEST_ID = "last_sync_request_id";
    public static final String ACTION_SYNC_START = "com.example.purelauncher.SYNC_START";
    public static final String ACTION_SYNC_COMPLETE = "com.example.purelauncher.SYNC_COMPLETE";
    public static final String EXTRA_SYNC_REQUEST_ID = "syncRequestId";

    public static void syncToFirestore(Context context, String requestId) {
        Intent startIntent = new Intent(ACTION_SYNC_START);
        startIntent.setPackage(context.getPackageName());
        startIntent.putExtra(EXTRA_SYNC_REQUEST_ID, requestId);
        context.sendBroadcast(startIntent);
        new Thread(() -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                sendSyncComplete(context, requestId);
                return;
            }
            
            UserProfileStore profileStore = new UserProfileStore();
            try {
                String parentUid = Tasks.await(profileStore.getLinkedParentUid(user));
                boolean isParentManaged = parentUid != null && !parentUid.trim().isEmpty();
                
                if (isParentManaged) {
                    applyParentVaultFromFirestore(context, user.getUid());
                }
            } catch (Exception e) {
                // Ignore
            }

            TelemetryRepository repo = new TelemetryRepository();
            TelemetrySnapshot local = repo.collectLocalSnapshot(context);
            
            List<AppSearchActivity.AppEntry> apps = getInstalledApps(context);
            Set<String> vaulted = VaultPrefs.getVaultedPackages(context);

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            String uid = user.getUid();
            WriteBatch batch = db.batch();

            DocumentReference metricsRef = db.collection("child_metrics").document(uid);
            Map<String, Object> metrics = buildMetricsMap(local);
            batch.set(metricsRef, metrics, SetOptions.merge());

            for (AppSearchActivity.AppEntry app : apps) {
                if (app == null || app.packageName == null) {
                    continue;
                }
                DocumentReference appRef = db.collection("child_apps")
                        .document(uid)
                        .collection("apps")
                        .document(app.packageName);
                Map<String, Object> data = new HashMap<>();
                data.put("name", app.label == null ? app.packageName : app.label);
                data.put("packageName", app.packageName);
                batch.set(appRef, data, SetOptions.merge());
            }

            PackageManager pm = context.getPackageManager();
            for (String pkg : vaulted) {
                if (pkg == null || pkg.trim().isEmpty()) {
                    continue;
                }
                DocumentReference vaultRef = db.collection("child_vault")
                        .document(uid)
                        .collection("apps")
                        .document(pkg);
                Map<String, Object> data = new HashMap<>();
                data.put("name", getLabelForPackage(pm, pkg));
                data.put("packageName", pkg);
                data.put("friction", VaultPrefs.getFrictionType(context, pkg));
                data.put("dailyLimitMinutes", VaultPrefs.getAppLimitMinutes(context, pkg));
                batch.set(vaultRef, data, SetOptions.merge());
            }

            LocalDate today = LocalDate.now();
            for (int i = 0; i < 7; i++) {
                LocalDate date = today.minusDays(6 - i);
                List<TelemetryRepository.AppUsageEntry> usage = repo.getAppUsageForDate(context, date);
                List<Map<String, Object>> appRows = new ArrayList<>();
                long totalMinutes = 0L;
                for (TelemetryRepository.AppUsageEntry entry : usage) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("packageName", entry.packageName);
                    row.put("name", getLabelForPackage(pm, entry.packageName));
                    row.put("minutes", entry.minutes);
                    row.put("launchCount", entry.launchCount);
                    appRows.add(row);
                    totalMinutes += entry.minutes;
                }
                DocumentReference usageRef = db.collection("child_usage")
                        .document(uid)
                        .collection("days")
                        .document(date.toString());
                Map<String, Object> usageData = new HashMap<>();
                usageData.put("date", date.toString());
                usageData.put("apps", appRows);
                usageData.put("totalMinutes", totalMinutes);
                usageData.put("lastUpdated", FieldValue.serverTimestamp());
                batch.set(usageRef, usageData, SetOptions.merge());
            }

            DocumentReference requestRef = db.collection("sync_requests").document(uid);
            Map<String, Object> ack = new HashMap<>();
            ack.put("lastSyncedAt", FieldValue.serverTimestamp());
            ack.put("lastSyncedRequestId", requestId);
            batch.set(requestRef, ack, SetOptions.merge());

            batch.commit().addOnSuccessListener(aVoid -> {
                saveLastSyncRequestId(context, requestId);
                sendSyncComplete(context, requestId);
            }).addOnFailureListener(e -> sendSyncComplete(context, requestId));
        }).start();
    }

    private static void applyParentVaultFromFirestore(Context context, String uid) {
        try {
            QuerySnapshot vaultSnap = Tasks.await(FirebaseFirestore.getInstance()
                    .collection("child_vault")
                    .document(uid)
                    .collection("apps")
                    .get());
            Set<String> newVault = new HashSet<>();
            for (DocumentSnapshot doc : vaultSnap.getDocuments()) {
                String pkg = doc.getString("packageName");
                if (pkg == null || pkg.trim().isEmpty()) {
                    pkg = doc.getId();
                }
                newVault.add(pkg);
                Long friction = doc.getLong("friction");
                if (friction != null) {
                    VaultPrefs.setFrictionType(context, pkg, friction.intValue());
                }
                Long limit = doc.getLong("dailyLimitMinutes");
                if (limit != null) {
                    VaultPrefs.setAppLimitMinutes(context, pkg, limit.intValue());
                }
            }
            VaultPrefs.setVaultedPackages(context, newVault);
        } catch (Exception ignored) {
        }
    }

    private static Map<String, Object> buildMetricsMap(TelemetrySnapshot local) {
        Map<String, Object> data = new HashMap<>();
        long screenMinutes = 0L;
        if (local != null && local.dailyUsageMinutes.length > 0) {
            screenMinutes = local.dailyUsageMinutes[local.dailyUsageMinutes.length - 1];
        }
        data.put("screenTimeMinutes", screenMinutes);
        data.put("unlockCount", local == null ? 0 : local.unlockCount);
        data.put("frictionCount", local == null ? 0 : local.frictionCount);
        data.put("vaultedCount", local == null ? 0 : local.vaultedCount);
        data.put("dailyUsageMinutes", toLongList(local == null ? new long[0] : local.dailyUsageMinutes));
        data.put("lastUpdated", FieldValue.serverTimestamp());
        return data;
    }

    private static List<Long> toLongList(long[] values) {
        List<Long> out = new ArrayList<>();
        if (values == null) {
            return out;
        }
        for (long v : values) {
            out.add(v);
        }
        return out;
    }

    public static String getLastSyncRequestId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_SYNC_REQUEST_ID, "");
    }

    static void saveLastSyncRequestId(Context context, String requestId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_SYNC_REQUEST_ID, requestId).apply();
    }

    private static void sendSyncComplete(Context context, String requestId) {
        Intent completeIntent = new Intent(ACTION_SYNC_COMPLETE);
        completeIntent.setPackage(context.getPackageName());
        completeIntent.putExtra(EXTRA_SYNC_REQUEST_ID, requestId);
        context.sendBroadcast(completeIntent);
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network nw = cm.getActiveNetwork();
            if (nw == null) {
                return false;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(nw);
            return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        }
        // noinspection deprecation
        NetworkInfo info = cm.getActiveNetworkInfo();
        // noinspection deprecation
        return info != null && info.isConnected();
    }

    private static String getLabelForPackage(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(info);
            return label == null ? pkg : label.toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            return pkg;
        }
    }

    private static List<AppSearchActivity.AppEntry> getInstalledApps(Context context) {
        List<AppSearchActivity.AppEntry> apps = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> list = pm.queryIntentActivities(intent, 0);
        for (android.content.pm.ResolveInfo info : list) {
            String pkg = info.activityInfo.packageName;
            CharSequence label = info.loadLabel(pm);
            if (label != null && !pkg.equals(context.getPackageName())) {
                apps.add(new AppSearchActivity.AppEntry(label.toString(), "", pkg));
            }
        }
        return apps;
    }
}
