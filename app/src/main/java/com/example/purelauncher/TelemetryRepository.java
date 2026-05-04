package com.example.purelauncher;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TelemetryRepository {

    TelemetryRepository() { }

    // ── Snapshot (home card - local only) ─────────────────────────────────

    TelemetrySnapshot collectLocalSnapshot(Context context) {
        long weeklyMinutes   = getWeeklyScreenTimeMinutes(context);
        long[] dailyUsage    = getRecentDailyUsageMinutes(context);
        long monthlyMinutes  = getMonthlyScreenTimeMinutes(context);
        long[] monthlyUsage  = getRecentMonthlyUsageMinutes(context);
        int unlockCount      = getUnlockCountToday(context);
        int frictionCount    = TelemetryLocalStore.getFrictionCount(context);
        int vaultedCount     = VaultPrefs.getVaultedPackages(context).size();
        return new TelemetrySnapshot(weeklyMinutes, dailyUsage, monthlyMinutes, monthlyUsage,
                unlockCount, frictionCount, vaultedCount);
    }

    // Firebase sync methods removed as per request.
    // They will be implemented later.

    // ── Core: INTERVAL_DAILY bucket-filtered query ──────────────────────────────

    private Map<String, Long> getDailyBucketForegroundMs(Context context, LocalDate date) {
        UsageStatsManager usm = usm(context);
        if (usm == null) return Collections.emptyMap();

        long qStart = epochStart(date.minusDays(1));
        long qEnd   = epochStart(date.plusDays(2));
        List<UsageStats> stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, qStart, qEnd);
        if (stats == null) return Collections.emptyMap();

        Map<String, Long> result = new HashMap<>();
        for (UsageStats s : stats) {
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

    long getAppUsageMsForDate(Context context, LocalDate date, String packageName) {
        if (packageName == null) return 0L;
        Long value = getDailyBucketForegroundMs(context, date).get(packageName);
        return value == null ? 0L : value;
    }

    long getTotalUsageMinutesForDate(Context context, LocalDate date) {
        Map<String, Long> perApp = getDailyBucketForegroundMs(context, date);
        long totalMs = 0;
        for (long ms : perApp.values()) totalMs += ms;
        return totalMs / 60_000L;
    }

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

    // ── DTO ───────────────────────────────────────────────────────────────────

    static final class AppUsageEntry {
        final String packageName;
        final long   minutes;
        final int    launchCount;
        AppUsageEntry(String p, long m, int l) { packageName=p; minutes=m; launchCount=l; }
    }
}
