package com.parsfilo.contentapp.core.firebase

import android.os.Bundle

fun AppAnalytics.logTabSelected(tab: String) {
    logEvent(
        AnalyticsEventName.TAB_SELECTED,
        Bundle().apply {
            putString(AnalyticsParamKey.TAB, tab)
        },
    )
}
