package com.parsfilo.contentapp.core.firebase

import android.os.Bundle

/**
 * Analytics extension for Notifications.
 * Extracted from Legacy to ensure visibility for feature modules.
 */
fun AppAnalytics.logPushReceived(type: String?) {
    logEvent(
        AnalyticsEventName.PUSH_RECEIVED,
        Bundle().apply {
            putString(AnalyticsParamKey.PUSH_TYPE, type ?: "unknown")
        }
    )
}

fun AppAnalytics.logPushOpen(type: String?) {
    logEvent(
        AnalyticsEventName.PUSH_OPEN,
        Bundle().apply {
            putString(AnalyticsParamKey.PUSH_TYPE, type ?: "unknown")
        }
    )
}

fun AppAnalytics.logNotificationOpen() {
    logEvent(AnalyticsEventName.NOTIFICATION_OPEN, null)
}

fun AppAnalytics.logNotificationMarkRead() {
    logEvent(AnalyticsEventName.NOTIFICATION_MARK_READ, null)
}

fun AppAnalytics.logNotificationMarkUnread() {
    logEvent(AnalyticsEventName.NOTIFICATION_MARK_UNREAD, null)
}

fun AppAnalytics.logNotificationsMarkAllRead() {
    logEvent(AnalyticsEventName.NOTIFICATIONS_MARK_ALL_READ, null)
}

fun AppAnalytics.logNotificationDelete() {
    logEvent(AnalyticsEventName.NOTIFICATION_DELETE, null)
}

fun AppAnalytics.logNotificationsDeleteAll() {
    logEvent(AnalyticsEventName.NOTIFICATIONS_DELETE_ALL, null)
}
