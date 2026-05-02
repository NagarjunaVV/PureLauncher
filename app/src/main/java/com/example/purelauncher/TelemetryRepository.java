package com.example.purelauncher;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TelemetryRepository {

    private static final String TAG = "TelemetryRepository";
    private static final String COLLECTION_USAGE_METRICS = "usage_metrics";
    private static final String COLLECTION_DAILY = "daily";
    private static final String KEY_WEEKLY        = "weeklyScreenTimeMinutes";
    private static final String KEY_NOTIFICATIONS  = "notificationCount";
    private static final String KEY_UNLOCKS        = "unlockCount";
    private static final String KEY_FRICTION       = "frictionCount";
    private static final String KEY_VAULTED        = "vaultedCount";
    private static final String KEY_DAILY_USAGE    = "dailyUsageMinutes";
    private static final String KEY_UPDATED_AT     = "updatedAt";

    private final FirebaseFirestore firestore;

    TelemetryRepository() { firestore = FirebaseFirestore.getInstance(); }

    // ── Snapshot (home card + Firestore sync) ─────────────────────────────────

    TelemetrySnapshot collectLocalSnapshot(Context context) {
        long weeklyMinutes   = getWeeklyScreenTimeMinutes(context);
        long[] dailyUsage    = getRecentDailyUsageMinutes(context);
        long monthlyMinutes  = getMonthlyScreenTimeMinutes(context);
        long[] monthlyUsage  = getRecentMonthlyUsageMinutes(context);
        int notifCount       = TelemetryLocalStore.getNotificationCount(context);
        int unlockCount      = getUnlockCountToday(context);
        int frictionCount    = TelemetryLocalStore.getFrictionCount(context);
        int vaultedCount     = VaultPrefs.getVaultedPackages(context).size();
        return new TelemetrySnapshot(weeklyMinutes, dailyUsage, monthlyMinutes, monthlyUsage,
                notifCount, unlockCount, frictionCount, vaultedCount);
    }

    Task<Void> syncCurrentChild(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || SessionPrefs.getRole(context) != SessionPrefs.Role.CHILD)
            return Tasks.forResult(null);
        return syncChildTelemetry(context, user.getUid());
    }

    Task<Void> syncChildTelemetry(Context context, String childUid) {
        Map<String, Object> payload = toMap(collectLocalSnapshot(context));
        String dayKey = LocalDate.now().toString();
        return firestore.collection(COLLECTION_USAGE_METRICS).document(childUid)
                .collection(COLLECTION_DAILY).document(dayKey)
                .set(payload, SetOptions.merge())
                .addOnSuccessListener(v -> Log.d(TAG, "Synced: " + childUid))
                .addOnFailureListener(e -> Log.e(TAG, "Sync failed: " + childUid, e));
    }

    Task<TelemetrySnapshot> getLatestSnapshotForChild(String childUid) {
        if (childUid == null || childUid.trim().isEmpty()) return Tasks.forResult(null);
        String dayKey = LocalDate.now().toString();
        return firestore.collection(COLLECTION_USAGE_METRICS).document(childUid)
                .collection(COLLECTION_DAILY).document(dayKey).get()
                .addOnFailureListener(e -> Log.e(TAG, "Get snapshot failed", e))
                .continueWith(t -> parseSnapshot(t.getResult()));
    }

    // ── Core: INTERVAL_DAILY bucket-filtered query ──────────────────────────────

    /**
     * Query INTERVAL_DAILY usage stats for a single calendar date, filtering
     * results to ONLY the daily bucket whose {@code getFirstTimeStamp()} falls
     * on the requested date.  This is the exact same data source Digital
     * Wellbeing reads — {@code getTotalTimeInForeground()} from the system's
     * daily aggregation.
     *
     * We query a wider window (±1 day) then filter, to guard against
     * bucket-boundary edge cases on different OEMs.
     *
     * @return package-name → foreground milliseconds (only entries > 0)
     */
    private Map<String, Long> getDailyBucketForegroundMs(Context context, LocalDate date) {
        UsageStatsManager usm = usm(context);
        if (usm == null) return Collections.emptyMap();

        // Query ±1 day to capture the correct bucket regardless of OEM quirks
        long qStart = epochStart(date.minusDays(1));
        long qEnd   = epochStart(date.plusDays(2));
        List<UsageStats> stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, qStart, qEnd);
        if (stats == null) return Collections.emptyMap();

        Map<String, Long> result = new HashMap<>();
        for (UsageStats s : stats) {
            // ── KEY FILTER: only keep entries from the requested date's bucket ──
            LocalDate bucketDay = java.time.Instant.ofEpochMilli(s.getFirstTimeStamp())
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            if (!bucketDay.equals(date)) continue;

            long fg = s.getTotalTimeInForeground();
            if (fg > 0) {
                result.merge(s.getPackageName(), fg, Long::sum);
            }
        }
        return result;
    }

    // ── Per-date queries (ScreenTimeActivity) ─────────────────────────────────

    /**
     * Screen-time minutes per app for one day.
     * Uses the same INTERVAL_DAILY bucket that Digital Wellbeing reads,
     * filtered to the exact requested calendar date.
     */
    List<AppUsageEntry> getAppUsageForDate(Context context, LocalDate date) {
        Map<String, Long> perApp = getDailyBucketForegroundMs(context, date);
        Map<String, Integer> launches = getAppLaunchCountForDate(context, date);

        List<AppUsageEntry> list = new ArrayList<>();
        for (Map.Entry<String, Long> entry : perApp.entrySet()) {
            long mins = entry.getValue() / 60_000L;
            if (mins > 0) {
                int lc = launches.getOrDefault(entry.getKey(), 0);
                list.add(new AppUsageEntry(entry.getKey(), mins, lc));
            }
        }
        list.sort((a, b) -> Long.compare(b.minutes, a.minutes));
        return list;
    }

    /**
     * Total screen-time minutes for one day.
     * Uses the SAME filtered bucket as {@code getAppUsageForDate} so the
     * total always equals the sum of per-app values.
     */
    long getTotalUsageMinutesForDate(Context context, LocalDate date) {
        Map<String, Long> perApp = getDailyBucketForegroundMs(context, date);
        long totalMs = 0;
        for (long ms : perApp.values()) totalMs += ms;
        return totalMs / 60_000L;
    }

    /**
     * Open-counts per app for one day (ACTIVITY_RESUMED events).
     */
    Map<String, Integer> getAppLaunchCountForDate(Context context, LocalDate date) {
        UsageStatsManager usm = usm(context);
        if (usm == null) return Collections.emptyMap();

        UsageEvents events = usm.queryEvents(epochStart(date), epochStart(date.plusDays(1)));
        if (events == null) return Collections.emptyMap();

        Map<String, Integer> counts = new HashMap<>();
        UsageEvents.Event ev = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(ev);
            if (ev.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED)
                counts.merge(ev.getPackageName(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * 7-day screen-time totals (minutes) ending at {@code lastDay}, index 6 = lastDay.
     * Each day uses the same bucket-filtered INTERVAL_DAILY query.
     */
    long[] getDailyUsageForWeekEndingAt(Context context, LocalDate lastDay) {
        long[] daily = new long[7];
        LocalDate startDate = lastDay.minusDays(6);

        for (int i = 0; i < 7; i++) {
            Map<String, Long> perApp = getDailyBucketForegroundMs(context, startDate.plusDays(i));
            long totalMs = 0;
            for (long ms : perApp.values()) totalMs += ms;
            daily[i] = totalMs / 60_000L;
        }
        return daily;
    }

    /**
     * 7-day launch-count totals ending at {@code lastDay}, index 6 = lastDay.
     * Queries all ACTIVITY_RESUMED events across the 7-day window in one call.
     */
    long[] getDailyLaunchCountsForWeekEndingAt(Context context, LocalDate lastDay) {
        UsageStatsManager usm = usm(context);
        long[] daily = new long[7];
        if (usm == null) return daily;

        LocalDate startDate = lastDay.minusDays(6);
        UsageEvents events = usm.queryEvents(epochStart(startDate), epochStart(lastDay.plusDays(1)));
        if (events == null) return daily;

        UsageEvents.Event ev = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(ev);
            if (ev.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                LocalDate day = java.time.Instant.ofEpochMilli(ev.getTimeStamp())
                        .atZone(ZoneId.systemDefault()).toLocalDate();
                long idx = ChronoUnit.DAYS.between(startDate, day);
                if (idx >= 0 && idx < 7) daily[(int) idx]++;
            }
        }
        return daily;
    }

    // ── Weekly / monthly totals (home card) ───────────────────────────────────

    private long getWeeklyScreenTimeMinutes(Context context) {
        long[] daily = getDailyUsageForWeekEndingAt(context, LocalDate.now());
        long sum = 0;
        for (long d : daily) sum += d;
        return sum;
    }

    private long getMonthlyScreenTimeMinutes(Context context) {
        return sumUsageStats(context, LocalDate.now().minusDays(29), LocalDate.now());
    }

    long[] getRecentDailyUsageMinutes(Context context) {
        return getDailyUsageForWeekEndingAt(context, LocalDate.now());
    }

    long[] getRecentMonthlyUsageMinutes(Context context) {
        UsageStatsManager usm = usm(context);
        long[] daily = new long[30];
        if (usm == null) return daily;

        LocalDate startDate = LocalDate.now().minusDays(29);
        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                epochStart(startDate), System.currentTimeMillis());
        if (stats == null) return daily;

        // Merge by package per day to avoid double-counting
        @SuppressWarnings("unchecked")
        Map<String, Long>[] dayMaps = new HashMap[30];
        for (int i = 0; i < 30; i++) dayMaps[i] = new HashMap<>();

        for (UsageStats s : stats) {
            long fg = s.getTotalTimeInForeground();
            if (fg <= 0) continue;
            LocalDate day = java.time.Instant.ofEpochMilli(s.getFirstTimeStamp())
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            long idx = ChronoUnit.DAYS.between(startDate, day);
            if (idx >= 0 && idx < 30) {
                dayMaps[(int) idx].merge(s.getPackageName(), fg, Long::sum);
            }
        }

        for (int i = 0; i < 30; i++) {
            long totalMs = 0;
            for (long ms : dayMaps[i].values()) totalMs += ms;
            daily[i] = totalMs / 60_000L;
        }
        return daily;
    }

    private long sumUsageStats(Context context, LocalDate from, LocalDate to) {
        UsageStatsManager usm = usm(context);
        if (usm == null) return 0;
        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                epochStart(from), epochStart(to.plusDays(1)));
        if (stats == null) return 0;
        // Merge by package to avoid double-counting across bucket boundaries
        Map<String, Long> perApp = new HashMap<>();
        for (UsageStats s : stats) {
            long fg = s.getTotalTimeInForeground();
            if (fg > 0) perApp.merge(s.getPackageName(), fg, Long::sum);
        }
        long total = 0;
        for (long ms : perApp.values()) total += ms;
        return total / 60_000L;
    }

    private int getUnlockCountToday(Context context) {
        UsageStatsManager usm = usm(context);
        if (usm == null) return 0;
        UsageEvents events = usm.queryEvents(epochStart(LocalDate.now()), System.currentTimeMillis());
        if (events == null) return 0;
        int count = 0;
        UsageEvents.Event ev = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(ev);
            if (ev.getEventType() == UsageEvents.Event.KEYGUARD_HIDDEN) count++;
        }
        return count;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UsageStatsManager usm(Context context) {
        return (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    private long epochStart(LocalDate d) {
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private Map<String, Object> toMap(TelemetrySnapshot s) {
        Map<String, Object> data = new HashMap<>();
        data.put(KEY_WEEKLY,        s.weeklyScreenTimeMinutes);
        data.put(KEY_DAILY_USAGE,   toList(s.dailyUsageMinutes));
        data.put("monthlyScreenTimeMinutes", s.monthlyScreenTimeMinutes);
        data.put("monthlyUsageMinutes",      toList(s.monthlyUsageMinutes));
        data.put(KEY_NOTIFICATIONS, s.notificationCount);
        data.put(KEY_UNLOCKS,       s.unlockCount);
        data.put(KEY_FRICTION,      s.frictionCount);
        data.put(KEY_VAULTED,       s.vaultedCount);
        data.put(KEY_UPDATED_AT,    System.currentTimeMillis());
        return data;
    }

    private TelemetrySnapshot parseSnapshot(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        Long weekly  = doc.getLong(KEY_WEEKLY);
        Long monthly = doc.getLong("monthlyScreenTimeMinutes");
        Long notif   = doc.getLong(KEY_NOTIFICATIONS);
        Long unlock  = doc.getLong(KEY_UNLOCKS);
        Long fric    = doc.getLong(KEY_FRICTION);
        Long vault   = doc.getLong(KEY_VAULTED);
        long[] daily = parseArr(doc.get(KEY_DAILY_USAGE), weekly == null ? 0 : weekly, 7);
        long[] month = parseArr(doc.get("monthlyUsageMinutes"), monthly == null ? 0 : monthly, 30);
        return new TelemetrySnapshot(orZ(weekly), daily, orZ(monthly), month,
                (int) orZ(notif), (int) orZ(unlock), (int) orZ(fric), (int) orZ(vault));
    }

    private long orZ(Long v) { return v == null ? 0 : v; }

    private List<Long> toList(long[] arr) {
        List<Long> l = new ArrayList<>();
        if (arr != null) for (long v : arr) l.add(v);
        return l;
    }

    private long[] parseArr(Object raw, long total, int size) {
        long[] out = new long[size];
        if (raw instanceof List<?>) {
            List<?> list = (List<?>) raw;
            int lim = Math.min(size, list.size());
            for (int i = 0; i < lim; i++)
                if (list.get(i) instanceof Number) out[i] = ((Number) list.get(i)).longValue();
            return out;
        }
        long perDay = size == 0 ? 0 : total / size;
        for (int i = 0; i < out.length; i++) out[i] = perDay;
        return out;
    }

    // ── DTO ───────────────────────────────────────────────────────────────────

    static final class AppUsageEntry {
        final String packageName;
        final long   minutes;
        final int    launchCount;
        AppUsageEntry(String p, long m, int l) { packageName=p; minutes=m; launchCount=l; }
    }
}
