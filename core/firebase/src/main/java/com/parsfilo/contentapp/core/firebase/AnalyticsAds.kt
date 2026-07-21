package com.parsfilo.contentapp.core.firebase

fun AppAnalytics.logAdShown(adType: String, adUnitId: String) {
    logEvent(
        AnalyticsEventName.AD_SHOWN,
        android.os.Bundle().apply {
            putString(AnalyticsParamKey.AD_TYPE, adType)
            putString(AnalyticsParamKey.AD_UNIT_ID, adUnitId)
        }
    )
}

fun AppAnalytics.logAdClicked(adType: String) {
    logEvent(
        AnalyticsEventName.AD_CLICKED,
        android.os.Bundle().apply {
            putString(AnalyticsParamKey.AD_TYPE, adType)
        }
    )
}

fun AppAnalytics.logAdFailedToLoad(adType: String, errorCode: Int, errorMessage: String) {
    logEvent(
        AnalyticsEventName.AD_FAILED_TO_LOAD,
        android.os.Bundle().apply {
            putString(AnalyticsParamKey.AD_TYPE, adType)
            putLong(AnalyticsParamKey.ERROR_CODE, errorCode.toLong())
            putString(AnalyticsParamKey.ERROR_MESSAGE, errorMessage)
        }
    )
}
