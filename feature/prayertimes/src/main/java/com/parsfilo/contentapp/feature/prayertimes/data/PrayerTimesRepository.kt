package com.parsfilo.contentapp.feature.prayertimes.data

import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAlarmSettings
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerCity
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerCountry
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerDistrict
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerLocationSelection
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerLocationSuggestion
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerTimesDay
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerTimesMode
import com.parsfilo.contentapp.feature.prayertimes.model.RefreshResult
import com.parsfilo.contentapp.feature.prayertimes.model.ResolveResult
import kotlinx.coroutines.flow.Flow

interface PrayerTimesRepository {
    fun observeCurrentSelection(): Flow<PrayerLocationSelection?>

    fun observeMode(): Flow<PrayerTimesMode>

    fun observeAlarmSettings(): Flow<PrayerAlarmSettings>

    fun observeTodayAndUpcoming(districtId: Int): Flow<List<PrayerTimesDay>>

    suspend fun refreshIfNeeded(
        districtId: Int,
        force: Boolean = false,
    ): RefreshResult

    suspend fun resolveAndSelectByDeviceLocation(): ResolveResult

    suspend fun suggestManualSelectionByDeviceLocation(): PrayerLocationSuggestion?

    suspend fun getCountries(forceRefresh: Boolean = false): List<PrayerCountry>

    suspend fun getCities(
        countryId: Int,
        forceRefresh: Boolean = false,
    ): List<PrayerCity>

    suspend fun getDistricts(
        cityId: Int,
        forceRefresh: Boolean = false,
    ): List<PrayerDistrict>

    suspend fun setMode(mode: PrayerTimesMode)

    suspend fun setAlarmEnabled(enabled: Boolean)

    suspend fun setAlarmOffsetMinutes(minutes: Int)

    suspend fun setSelectedAlarmPrayerKeys(keys: Set<String>)

    suspend fun setManualSelection(
        countryId: Int,
        cityId: Int,
        districtId: Int,
    )
}
