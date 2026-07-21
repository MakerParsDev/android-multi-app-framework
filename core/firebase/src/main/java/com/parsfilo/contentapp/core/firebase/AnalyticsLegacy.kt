package com.parsfilo.contentapp.core.firebase

/**
 * Legacy events for backward compatibility.
 */

fun AppAnalytics.logPaywallView() {
    logEvent(AnalyticsEventName.PAYWALL_VIEW)
}

fun AppAnalytics.logPurchaseStart(planId: String) {
    logEvent(
        AnalyticsEventName.PURCHASE_START,
        android.os.Bundle().apply {
            putString(AnalyticsParamKey.PLAN_ID, planId)
        }
    )
}

fun AppAnalytics.logPurchaseSuccess(planId: String) {
    logEvent(
        AnalyticsEventName.PURCHASE_SUCCESS,
        android.os.Bundle().apply {
            putString(AnalyticsParamKey.PLAN_ID, planId)
        }
    )
}

fun AppAnalytics.logPurchaseFailed(reason: String) {
    logEvent(
        AnalyticsEventName.PURCHASE_FAILED,
        android.os.Bundle().apply {
            putString(AnalyticsParamKey.REASON, reason)
        }
    )
}

fun AppAnalytics.logAdImpression(adType: String, adUnitId: String) {
    logEvent(
        AnalyticsEventName.AD_IMPRESSION,
        android.os.Bundle().apply {
            putString(AnalyticsParamKey.AD_TYPE, adType)
            putString(AnalyticsParamKey.AD_UNIT_ID, adUnitId)
        }
    )
}

// Moved to AnalyticsNotifications.kt

fun AppAnalytics.logContentPlayStart(verseId: Int?) {
    logEvent(
        AnalyticsEventName.CONTENT_PLAY_START,
        android.os.Bundle().apply {
            putLong(AnalyticsParamKey.VERSE_ID, (verseId ?: 0).toLong())
            putString(AnalyticsParamKey.CONTENT_ID, "verse:${verseId ?: 0}")
        }
    )
}

fun AppAnalytics.logContentPlayComplete() {
    logEvent(AnalyticsEventName.CONTENT_PLAY_COMPLETE)
}
