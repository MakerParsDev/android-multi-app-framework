package com.parsfilo.contentapp.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.parsfilo.contentapp.core.datastore.di.PrayerPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PrayerPreferencesDataSource @Inject constructor(
    @PrayerPreferencesStore
    private val prayerPreferences: DataStore<Preferences>,
) {
    val preferences: Flow<PrayerPreferencesData> = prayerPreferences.data.map { pref ->
        PrayerPreferencesData(
            mode = pref[Keys.MODE] ?: PrayerTimesMode.AUTO.name,
            selectedCountryId = pref[Keys.SELECTED_COUNTRY_ID],
            selectedCityId = pref[Keys.SELECTED_CITY_ID],
            selectedDistrictId = pref[Keys.SELECTED_DISTRICT_ID],
            lastAutoDistrictId = pref[Keys.LAST_AUTO_DISTRICT_ID],
            locationPermissionPrompted = pref[Keys.LOCATION_PERMISSION_PROMPTED] ?: false,
            lastSuccessfulRefreshAt = pref[Keys.LAST_SUCCESSFUL_REFRESH_AT] ?: 0L,
            alarmEnabled = pref[Keys.ALARM_ENABLED] ?: false,
            alarmOffsetMinutes = pref[Keys.ALARM_OFFSET_MINUTES] ?: 0,
            selectedAlarmPrayerKeys = pref[Keys.ALARM_SELECTED_PRAYER_KEYS] ?: emptySet(),
            alarmSoundUri = pref[Keys.ALARM_SOUND_URI],
        )
    }

    suspend fun setMode(mode: PrayerTimesMode) {
        prayerPreferences.edit { pref ->
            pref[Keys.MODE] = mode.name
        }
    }

    suspend fun setManualSelection(
        countryId: Int,
        cityId: Int,
        districtId: Int,
    ) {
        prayerPreferences.edit { pref ->
            pref[Keys.SELECTED_COUNTRY_ID] = countryId
            pref[Keys.SELECTED_CITY_ID] = cityId
            pref[Keys.SELECTED_DISTRICT_ID] = districtId
        }
    }

    suspend fun setLastAutoDistrictId(districtId: Int) {
        prayerPreferences.edit { pref ->
            pref[Keys.LAST_AUTO_DISTRICT_ID] = districtId
        }
    }

    suspend fun setLocationPermissionPrompted(prompted: Boolean) {
        prayerPreferences.edit { pref ->
            pref[Keys.LOCATION_PERMISSION_PROMPTED] = prompted
        }
    }

    suspend fun setLastSuccessfulRefreshAt(timestamp: Long) {
        prayerPreferences.edit { pref ->
            pref[Keys.LAST_SUCCESSFUL_REFRESH_AT] = timestamp
        }
    }

    suspend fun setAlarmEnabled(enabled: Boolean) {
        prayerPreferences.edit { pref ->
            pref[Keys.ALARM_ENABLED] = enabled
        }
    }

    suspend fun setAlarmOffsetMinutes(minutes: Int) {
        prayerPreferences.edit { pref ->
            pref[Keys.ALARM_OFFSET_MINUTES] = minutes.coerceIn(0, MAX_ALARM_OFFSET_MINUTES)
        }
    }

    suspend fun setSelectedAlarmPrayerKeys(keys: Set<String>) {
        prayerPreferences.edit { pref ->
            pref[Keys.ALARM_SELECTED_PRAYER_KEYS] = keys
        }
    }

    suspend fun setAlarmSoundUri(uri: String?) {
        prayerPreferences.edit { pref ->
            if (uri.isNullOrBlank()) {
                pref.remove(Keys.ALARM_SOUND_URI)
            } else {
                pref[Keys.ALARM_SOUND_URI] = uri
            }
        }
    }

    suspend fun clearSelection() {
        prayerPreferences.edit { pref ->
            pref.remove(Keys.SELECTED_COUNTRY_ID)
            pref.remove(Keys.SELECTED_CITY_ID)
            pref.remove(Keys.SELECTED_DISTRICT_ID)
        }
    }

    private object Keys {
        val MODE = stringPreferencesKey("prayer_mode")
        val SELECTED_COUNTRY_ID = intPreferencesKey("prayer_selected_country_id")
        val SELECTED_CITY_ID = intPreferencesKey("prayer_selected_city_id")
        val SELECTED_DISTRICT_ID = intPreferencesKey("prayer_selected_district_id")
        val LAST_AUTO_DISTRICT_ID = intPreferencesKey("prayer_last_auto_district_id")
        val LOCATION_PERMISSION_PROMPTED = booleanPreferencesKey("prayer_location_permission_prompted")
        val LAST_SUCCESSFUL_REFRESH_AT = longPreferencesKey("prayer_last_successful_refresh_at")
        val ALARM_ENABLED = booleanPreferencesKey("prayer_alarm_enabled")
        val ALARM_OFFSET_MINUTES = intPreferencesKey("prayer_alarm_offset_minutes")
        val ALARM_SELECTED_PRAYER_KEYS = stringSetPreferencesKey("prayer_alarm_selected_prayer_keys")
        val ALARM_SOUND_URI = stringPreferencesKey("prayer_alarm_sound_uri")
    }

    private companion object {
        private const val MAX_ALARM_OFFSET_MINUTES = 120
    }
}

enum class PrayerTimesMode {
    AUTO,
    MANUAL,
}

data class PrayerPreferencesData(
    val mode: String,
    val selectedCountryId: Int?,
    val selectedCityId: Int?,
    val selectedDistrictId: Int?,
    val lastAutoDistrictId: Int?,
    val locationPermissionPrompted: Boolean,
    val lastSuccessfulRefreshAt: Long,
    val alarmEnabled: Boolean,
    val alarmOffsetMinutes: Int,
    val selectedAlarmPrayerKeys: Set<String>,
    val alarmSoundUri: String?,
)
