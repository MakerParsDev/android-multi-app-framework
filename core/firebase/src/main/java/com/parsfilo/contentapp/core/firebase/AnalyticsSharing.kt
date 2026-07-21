package com.parsfilo.contentapp.core.firebase

fun AppAnalytics.logAppShared(platform: String) {
    logEvent(
        AnalyticsEventName.SHARE,
        android.os.Bundle().apply {
            putString(AnalyticsParamKey.CONTENT_TYPE, "app_recommendation")
            putString(AnalyticsParamKey.PLATFORM, platform)
        }
    )
}
