package com.parsfilo.contentapp.feature.prayertimes.model

data class PrayerAlarmSettings(
    val enabled: Boolean = false,
    val offsetMinutes: Int = 0,
    val selectedPrayerKeys: Set<String> = emptySet(),
    val soundUri: String? = null,
)
