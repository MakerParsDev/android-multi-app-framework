package com.parsfilo.contentapp.core.firebase

fun AppAnalytics.logVerseRead(verseId: Int, displayMode: String) {
    logEvent(
        AnalyticsEventName.VERSE_READ,
        android.os.Bundle().apply {
            putLong(AnalyticsParamKey.VERSE_ID, verseId.toLong())
            putString(AnalyticsParamKey.CONTENT_ID, "verse:$verseId")
            putString(AnalyticsParamKey.DISPLAY_MODE, displayMode)
        }
    )
}

fun AppAnalytics.logDisplayModeChanged(oldMode: String, newMode: String) {
    logEvent(
        AnalyticsEventName.DISPLAY_MODE_CHANGED,
        android.os.Bundle().apply {
            putString(AnalyticsParamKey.OLD_MODE, oldMode)
            putString(AnalyticsParamKey.NEW_MODE, newMode)
        }
    )
}
