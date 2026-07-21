package com.parsfilo.contentapp.core.firebase

fun AppAnalytics.logUserLogin(method: String) {
    logEvent(
        AnalyticsEventName.LOGIN,
        android.os.Bundle().apply {
            putString(AnalyticsParamKey.METHOD, method)
        }
    )
}

fun AppAnalytics.logUserLogout() {
    logEvent(AnalyticsEventName.USER_LOGOUT)
}
