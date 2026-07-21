package com.parsfilo.contentapp.core.firebase

fun AppAnalytics.logScreenView(screenName: String, screenClass: String) {
    logEvent(
        AnalyticsEventName.SCREEN_VIEW,
        android.os.Bundle().apply {
            putString(AnalyticsParamKey.SCREEN_NAME, screenName)
            putString(AnalyticsParamKey.SCREEN_CLASS, screenClass)
        }
    )
}
