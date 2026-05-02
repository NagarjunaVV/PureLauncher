package com.example.purelauncher;

final class TelemetrySnapshot {
    final long weeklyScreenTimeMinutes;
    final long[] dailyUsageMinutes;
    final long monthlyScreenTimeMinutes;
    final long[] monthlyUsageMinutes;
    final int notificationCount;
    final int unlockCount;
    final int frictionCount;
    final int vaultedCount;

    TelemetrySnapshot(long weeklyScreenTimeMinutes,
                      long[] dailyUsageMinutes,
                      long monthlyScreenTimeMinutes,
                      long[] monthlyUsageMinutes,
                      int notificationCount,
                      int unlockCount,
                      int frictionCount,
                      int vaultedCount) {
        this.weeklyScreenTimeMinutes = weeklyScreenTimeMinutes;
        this.dailyUsageMinutes = dailyUsageMinutes == null ? new long[7] : dailyUsageMinutes;
        this.monthlyScreenTimeMinutes = monthlyScreenTimeMinutes;
        this.monthlyUsageMinutes = monthlyUsageMinutes == null ? new long[30] : monthlyUsageMinutes;
        this.notificationCount = notificationCount;
        this.unlockCount = unlockCount;
        this.frictionCount = frictionCount;
        this.vaultedCount = vaultedCount;
    }
}
