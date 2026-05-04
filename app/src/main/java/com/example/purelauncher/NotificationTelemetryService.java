package com.example.purelauncher;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * DEPRECATED: Notification tracking has been removed from the codebase.
 * This service is kept as a stub to avoid manifest errors if not removed there yet.
 */
public class NotificationTelemetryService extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // Feature removed
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Feature removed
    }
}
