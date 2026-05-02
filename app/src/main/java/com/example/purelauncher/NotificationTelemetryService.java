package com.example.purelauncher;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationTelemetryService extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (SessionPrefs.getRole(this) == SessionPrefs.Role.CHILD) {
            // Track per-package count for the notifications filter in ScreenTimeActivity
            TelemetryLocalStore.incrementNotification(this, sbn.getPackageName());
            new TelemetryRepository().syncCurrentChild(this);
        }
    }
}
