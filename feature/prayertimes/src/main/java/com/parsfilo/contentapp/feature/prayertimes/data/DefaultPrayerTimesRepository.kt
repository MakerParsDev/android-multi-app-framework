package com.parsfilo.contentapp.feature.prayertimes.data

import android.os.Bundle
import com.parsfilo.contentapp.core.common.network.AppDispatchers
import com.parsfilo.contentapp.core.common.network.Dispatcher
import com.parsfilo.contentapp.core.database.dao.prayer.PrayerTimesDao
import com.parsfilo.contentapp.core.database.model.prayer.PrayerCityEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerCountryEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerDistrictEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerSyncStateEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerTimeEntity
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.core.firebase.AnalyticsEventName
import com.parsfilo.contentapp.core.firebase.AnalyticsParamKey
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmScheduler
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPrayerTimesRepository @Inject constructor(
    private val apiClient: EzanVaktiApiClient,
    private val prayerTimesDao: PrayerTimesDao,
    private val prayerPreferencesDataSource: PrayerPreferencesDataSource,
    private val locationResolver: PrayerLocationResolver,
    private val prayerAlarmScheduler: PrayerAlarmScheduler,
    private val appAnalytics: AppAnalytics,
    @Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : PrayerTimesRepository {

    override fun observeCurrentSelection(): Flow<PrayerLocationSelection?> {
        return prayerPreferencesDataSource.preferences.mapLatest { prefs ->
            val districtId = prefs.selectedDistrictId ?: return@mapLatest null
            findSelectionByDistrictId(districtId)
        }
    }

    override fun observeMode(): Flow<PrayerTimesMode> {
        return prayerPreferencesDataSource.preferences.map { prefs ->
            runCatching { PrayerTimesMode.valueOf(prefs.mode) }
                .getOrDefault(PrayerTimesMode.AUTO)
        }
    }

    override fun observeAlarmSettings(): Flow<PrayerAlarmSettings> {
        return prayerPreferencesDataSource.preferences.map { prefs ->
            PrayerAlarmSettings(
                enabled = prefs.alarmEnabled,
                offsetMinutes = prefs.alarmOffsetMinutes,
                selectedPrayerKeys = prefs.selectedAlarmPrayerKeys,
                soundUri = prefs.alarmSoundUri,
            )
        }
    }

    override fun observeTodayAndUpcoming(districtId: Int): Flow<List<PrayerTimesDay>> {
        val todayIso = PrayerDateTime.todayIso()
        val (fromDate, toDate) = PrayerCachePolicy.dateWindow(todayIso)

        return prayerTimesDao.observePrayerTimes(
            districtId = districtId,
            fromDate = fromDate,
            toDate = toDate,
        ).map { entities -> entities.map { it.asExternalModel() } }
    }

    override suspend fun refreshIfNeeded(
        districtId: Int,
        force: Boolean,
    ): RefreshResult = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val todayIso = PrayerDateTime.todayIso()

        prayerTimesDao.touchDistrictAccess(districtId = districtId, lastAccessAt = now)
        val syncState = prayerTimesDao.getSyncState(districtId)
        val hasToday = prayerTimesDao.countByDistrictAndDate(districtId, todayIso) > 0

        if (
            !PrayerCachePolicy.shouldRefresh(
                nowMillis = now,
                lastSyncAtMillis = syncState?.lastSyncAt,
                coverageEndIso = syncState?.coverageEnd,
                todayIso = todayIso,
                hasTodayCache = hasToday,
                force = force,
            )
        ) {
            appAnalytics.logEvent(AnalyticsEventName.PRAYER_CACHE_HIT)
            return@withContext RefreshResult.SkippedFresh
        }

        appAnalytics.logEvent(AnalyticsEventName.PRAYER_CACHE_MISS)

        try {
            val remoteItems = apiClient.getPrayerTimes(districtId)
            val entities = remoteItems.mapNotNull { item ->
                val localDateIso = PrayerDateTime.parseMiladiToIso(item.miladiDateShort)
                    ?: return@mapNotNull null
                PrayerTimeEntity(
                    districtId = districtId,
                    localDate = localDateIso,
                    imsak = item.imsak,
                    gunes = item.gunes,
                    ogle = item.ogle,
                    ikindi = item.ikindi,
                    aksam = item.aksam,
                    yatsi = item.yatsi,
                    fetchedAt = now,
                )
            }

            if (entities.isEmpty()) {
                throw IllegalStateException("Prayer times response is empty")
            }

            prayerTimesDao.upsertPrayerTimes(entities)
            val (windowStart, windowEnd) = PrayerCachePolicy.dateWindow(todayIso)
            prayerTimesDao.deletePrayerTimesOutsideRange(
                districtId = districtId,
                minDate = windowStart,
                maxDate = windowEnd,
            )

            val coverageStart = entities.minOf { it.localDate }
            val coverageEnd = entities.maxOf { it.localDate }
            prayerTimesDao.upsertSyncState(
                PrayerSyncStateEntity(
                    districtId = districtId,
                    lastSyncAt = now,
                    coverageStart = coverageStart,
                    coverageEnd = coverageEnd,
                    lastAccessAt = now,
                )
            )

            prayerPreferencesDataSource.setLastSuccessfulRefreshAt(now)
            pruneCacheByLru(activeDistrictId = districtId)
            runCatching { prayerAlarmScheduler.scheduleNextForCurrentFlavor() }
                .onFailure { Timber.w(it, "Unable to reschedule prayer alarm after refresh") }
            RefreshResult.Refreshed
        } catch (e: IOException) {
            handleRefreshFailure(error = e, districtId = districtId, hasToday = hasToday)
        } catch (e: SecurityException) {
            handleRefreshFailure(error = e, districtId = districtId, hasToday = hasToday)
        } catch (e: IllegalStateException) {
            handleRefreshFailure(error = e, districtId = districtId, hasToday = hasToday)
        } catch (e: IllegalArgumentException) {
            handleRefreshFailure(error = e, districtId = districtId, hasToday = hasToday)
        }
    }

    override suspend fun resolveAndSelectByDeviceLocation(): ResolveResult = withContext(ioDispatcher) {
        if (!locationResolver.hasLocationPermission()) {
            appAnalytics.logEvent(AnalyticsEventName.PRAYER_RESOLVE_FALLBACK)
            return@withContext ResolveResult.PermissionDenied
        }

        val candidate = runCatching { locationResolver.resolveAddressCandidate() }
            .onFailure { Timber.w(it, "resolveAddressCandidate failed") }
            .getOrNull()
            ?: run {
                appAnalytics.logEvent(AnalyticsEventName.PRAYER_RESOLVE_FALLBACK)
                return@withContext ResolveResult.LocationUnavailable
            }

        return@withContext try {
            val countries = getCountries(forceRefresh = false)
            val country = PrayerLocationMatcher.bestMatch(
                input = candidate.country,
                items = countries,
                trName = { it.nameTr },
                enName = { it.nameEn },
            ) ?: run {
                appAnalytics.logEvent(AnalyticsEventName.PRAYER_RESOLVE_FALLBACK)
                return@withContext ResolveResult.NoMatch
            }

            val cities = getCities(country.id, forceRefresh = false)
            val city = PrayerLocationMatcher.bestMatch(
                input = candidate.city,
                items = cities,
                trName = { it.nameTr },
                enName = { it.nameEn },
            ) ?: PrayerLocationMatcher.bestMatch(
                input = candidate.district,
                items = cities,
                trName = { it.nameTr },
                enName = { it.nameEn },
            ) ?: run {
                appAnalytics.logEvent(AnalyticsEventName.PRAYER_RESOLVE_FALLBACK)
                return@withContext ResolveResult.NoMatch
            }

            val districts = getDistricts(city.id, forceRefresh = false)
            val district = PrayerLocationMatcher.bestMatch(
                input = candidate.district,
                items = districts,
                trName = { it.nameTr },
                enName = { it.nameEn },
            ) ?: PrayerLocationMatcher.bestMatch(
                input = candidate.city,
                items = districts,
                trName = { it.nameTr },
                enName = { it.nameEn },
            )
                ?: districts.singleOrNull()
                ?: run {
                    appAnalytics.logEvent(AnalyticsEventName.PRAYER_RESOLVE_FALLBACK)
                    return@withContext ResolveResult.NoMatch
                }

            setMode(PrayerTimesMode.AUTO)
            prayerPreferencesDataSource.setManualSelection(
                countryId = country.id,
                cityId = city.id,
                districtId = district.id,
            )
            prayerPreferencesDataSource.setLastAutoDistrictId(district.id)
            val selection = PrayerLocationSelection(
                countryId = country.id,
                cityId = city.id,
                districtId = district.id,
                displayName = "${district.nameTr}, ${city.nameTr}, ${country.nameTr}",
            )
            refreshIfNeeded(district.id, force = true)
            appAnalytics.logEvent(
                AnalyticsEventName.PRAYER_RESOLVE_SUCCESS,
                Bundle().apply { putInt(AnalyticsParamKey.DISTRICT_ID, district.id) }
            )
            ResolveResult.Success(selection)
        } catch (e: IOException) {
            logResolveFailure(error = e)
        } catch (e: SecurityException) {
            logResolveFailure(error = e)
        } catch (e: IllegalStateException) {
            logResolveFailure(error = e)
        } catch (e: IllegalArgumentException) {
            logResolveFailure(error = e)
        }
    }

    override suspend fun suggestManualSelectionByDeviceLocation(): PrayerLocationSuggestion? =
        withContext(ioDispatcher) {
            if (!locationResolver.hasLocationPermission()) return@withContext null
            val candidate = runCatching { locationResolver.resolveAddressCandidate() }
                .onFailure { Timber.w(it, "resolveAddressCandidate failed for suggestion") }
                .getOrNull()
                ?: return@withContext null

            return@withContext runCatching {
                val countries = getCountries(forceRefresh = false)
                val country = PrayerLocationMatcher.bestMatch(
                    input = candidate.country,
                    items = countries,
                    trName = { it.nameTr },
                    enName = { it.nameEn },
                ) ?: return@withContext null

                val cities = getCities(country.id, forceRefresh = false)
                val city = PrayerLocationMatcher.bestMatch(
                    input = candidate.city,
                    items = cities,
                    trName = { it.nameTr },
                    enName = { it.nameEn },
                ) ?: PrayerLocationMatcher.bestMatch(
                    input = candidate.district,
                    items = cities,
                    trName = { it.nameTr },
                    enName = { it.nameEn },
                )

                PrayerLocationSuggestion(
                    countryId = country.id,
                    cityId = city?.id,
                )
            }.getOrElse {
                Timber.w(it, "suggestManualSelectionByDeviceLocation failed")
                null
            }
        }

    override suspend fun getCountries(forceRefresh: Boolean): List<PrayerCountry> = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val cached = prayerTimesDao.getCountries()
        val minFetchedAt = prayerTimesDao.getMinCountryFetchedAt()
        val isFresh = cached.isNotEmpty() && minFetchedAt != null && now - minFetchedAt <= LIST_TTL_MS

        if (!forceRefresh && isFresh) {
            return@withContext cached.map { it.asExternalModel() }
        }

        runCatching {
            val remote = apiClient.getCountries().map {
                PrayerCountryEntity(
                    countryId = it.id,
                    nameTr = it.nameTr,
                    nameEn = it.nameEn,
                    fetchedAt = now,
                )
            }
            if (remote.isNotEmpty()) {
                prayerTimesDao.deleteCountries()
                prayerTimesDao.upsertCountries(remote)
                return@withContext remote.map { it.asExternalModel() }
            }
        }.onFailure {
            Timber.w(it, "Failed to refresh countries")
        }

        cached.map { it.asExternalModel() }
    }

    override suspend fun getCities(
        countryId: Int,
        forceRefresh: Boolean,
    ): List<PrayerCity> = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val cached = prayerTimesDao.getCities(countryId)
        val minFetchedAt = prayerTimesDao.getMinCityFetchedAt(countryId)
        val isFresh = cached.isNotEmpty() && minFetchedAt != null && now - minFetchedAt <= LIST_TTL_MS

        if (!forceRefresh && isFresh) {
            return@withContext cached.map { it.asExternalModel() }
        }

        runCatching {
            val remote = apiClient.getCities(countryId).map {
                PrayerCityEntity(
                    cityId = it.id,
                    countryId = countryId,
                    nameTr = it.nameTr,
                    nameEn = it.nameEn,
                    fetchedAt = now,
                )
            }
            if (remote.isNotEmpty()) {
                prayerTimesDao.deleteCitiesByCountry(countryId)
                prayerTimesDao.upsertCities(remote)
                return@withContext remote.map { it.asExternalModel() }
            }
        }.onFailure {
            Timber.w(it, "Failed to refresh cities")
        }

        cached.map { it.asExternalModel() }
    }

    override suspend fun getDistricts(
        cityId: Int,
        forceRefresh: Boolean,
    ): List<PrayerDistrict> = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val cached = prayerTimesDao.getDistricts(cityId)
        val minFetchedAt = prayerTimesDao.getMinDistrictFetchedAt(cityId)
        val isFresh = cached.isNotEmpty() && minFetchedAt != null && now - minFetchedAt <= LIST_TTL_MS

        if (!forceRefresh && isFresh) {
            return@withContext cached.map { it.asExternalModel() }
        }

        runCatching {
            val remote = apiClient.getDistricts(cityId).map {
                PrayerDistrictEntity(
                    districtId = it.id,
                    cityId = cityId,
                    nameTr = it.nameTr,
                    nameEn = it.nameEn,
                    fetchedAt = now,
                )
            }
            if (remote.isNotEmpty()) {
                prayerTimesDao.deleteDistrictsByCity(cityId)
                prayerTimesDao.upsertDistricts(remote)
                return@withContext remote.map { it.asExternalModel() }
            }
        }.onFailure {
            Timber.w(it, "Failed to refresh districts")
        }

        cached.map { it.asExternalModel() }
    }

    override suspend fun setMode(mode: PrayerTimesMode): Unit = withContext(ioDispatcher) {
        prayerPreferencesDataSource.setMode(
            mode = when (mode) {
                PrayerTimesMode.AUTO -> com.parsfilo.contentapp.core.datastore.PrayerTimesMode.AUTO
                PrayerTimesMode.MANUAL -> com.parsfilo.contentapp.core.datastore.PrayerTimesMode.MANUAL
            }
        )
    }

    override suspend fun setAlarmEnabled(enabled: Boolean): Unit = withContext(ioDispatcher) {
        prayerPreferencesDataSource.setAlarmEnabled(enabled)
        runCatching { prayerAlarmScheduler.scheduleNextForCurrentFlavor() }
            .onFailure { Timber.w(it, "Unable to reschedule prayer alarm after enabled update") }
    }

    override suspend fun setAlarmOffsetMinutes(minutes: Int): Unit = withContext(ioDispatcher) {
        prayerPreferencesDataSource.setAlarmOffsetMinutes(minutes)
        runCatching { prayerAlarmScheduler.scheduleNextForCurrentFlavor() }
            .onFailure { Timber.w(it, "Unable to reschedule prayer alarm after offset update") }
    }

    override suspend fun setSelectedAlarmPrayerKeys(keys: Set<String>): Unit = withContext(ioDispatcher) {
        prayerPreferencesDataSource.setSelectedAlarmPrayerKeys(keys)
        runCatching { prayerAlarmScheduler.scheduleNextForCurrentFlavor() }
            .onFailure { Timber.w(it, "Unable to reschedule prayer alarm after prayer keys update") }
    }

    override suspend fun setManualSelection(
        countryId: Int,
        cityId: Int,
        districtId: Int,
    ): Unit = withContext(ioDispatcher) {
        prayerPreferencesDataSource.setMode(com.parsfilo.contentapp.core.datastore.PrayerTimesMode.MANUAL)
        prayerPreferencesDataSource.setManualSelection(
            countryId = countryId,
            cityId = cityId,
            districtId = districtId,
        )
        refreshIfNeeded(districtId = districtId, force = false)
        runCatching { prayerAlarmScheduler.scheduleNextForCurrentFlavor() }
            .onFailure { Timber.w(it, "Unable to reschedule prayer alarm after manual selection") }
    }

    private suspend fun pruneCacheByLru(activeDistrictId: Int) {
        val lruIds = prayerTimesDao.getDistrictIdsByRecentAccess()
        val idsToDrop = PrayerCachePolicy.pruneDistrictIds(
            recentDistrictIds = lruIds,
            activeDistrictId = activeDistrictId,
            maxCachedDistricts = MAX_CACHED_DISTRICTS,
        )
        idsToDrop.forEach { districtId ->
            prayerTimesDao.deletePrayerTimesByDistrict(districtId)
            prayerTimesDao.deleteSyncStateByDistrict(districtId)
        }
    }

    private suspend fun findSelectionByDistrictId(districtId: Int): PrayerLocationSelection? {
        val district = prayerTimesDao.getDistrictById(districtId) ?: return null
        val city = prayerTimesDao.getCityById(district.cityId) ?: return null
        val country = prayerTimesDao.getCountryById(city.countryId) ?: return null
        return PrayerLocationSelection(
            countryId = country.countryId,
            cityId = city.cityId,
            districtId = district.districtId,
            displayName = "${district.nameTr}, ${city.nameTr}, ${country.nameTr}",
        )
    }

    private fun buildApiErrorBundle(
        districtId: Int,
        httpStatusCode: Int?,
        errorName: String,
    ): Bundle? {
        return runCatching {
            Bundle().apply {
                putInt(AnalyticsParamKey.DISTRICT_ID, districtId)
                putInt(AnalyticsParamKey.HTTP_STATUS_CODE, httpStatusCode ?: UNKNOWN_HTTP_CODE)
                putString(AnalyticsParamKey.ERROR, errorName)
            }
        }.getOrNull()
    }

    private fun handleRefreshFailure(
        error: Throwable,
        districtId: Int,
        hasToday: Boolean,
    ): RefreshResult {
        Timber.w(error, "Prayer times refresh failed for district=%s", districtId)
        val httpStatusCode = (error as? EzanVaktiHttpException)?.statusCode
        appAnalytics.logEvent(
            AnalyticsEventName.PRAYER_API_ERROR,
            buildApiErrorBundle(
                districtId = districtId,
                httpStatusCode = httpStatusCode,
                errorName = error::class.java.simpleName,
            )
        )
        if (httpStatusCode == INVALID_SELECTION_HTTP_STATUS) {
            return RefreshResult.InvalidSelection
        }
        return if (hasToday) {
            RefreshResult.ServedFromCache
        } else {
            RefreshResult.Failed(error)
        }
    }

    private fun logResolveFailure(error: Throwable): ResolveResult {
        Timber.w(error, "resolveAndSelectByDeviceLocation failed")
        appAnalytics.logEvent(AnalyticsEventName.PRAYER_RESOLVE_FALLBACK)
        return ResolveResult.Failed(error)
    }

    private companion object {
        private const val INVALID_SELECTION_HTTP_STATUS = 422
        private const val UNKNOWN_HTTP_CODE = -1
        private const val MAX_CACHED_DISTRICTS = 3
        private const val LIST_TTL_MS = 30L * 24 * 60 * 60 * 1000
    }
}

internal fun PrayerTimeEntity.asExternalModel(): PrayerTimesDay {
    return PrayerTimesDay(
        localDate = localDate,
        imsak = imsak,
        gunes = gunes,
        ogle = ogle,
        ikindi = ikindi,
        aksam = aksam,
        yatsi = yatsi,
    )
}

internal fun PrayerCountryEntity.asExternalModel(): PrayerCountry {
    return PrayerCountry(
        id = countryId,
        nameTr = nameTr,
        nameEn = nameEn,
    )
}

internal fun PrayerCityEntity.asExternalModel(): PrayerCity {
    return PrayerCity(
        id = cityId,
        countryId = countryId,
        nameTr = nameTr,
        nameEn = nameEn,
    )
}

internal fun PrayerDistrictEntity.asExternalModel(): PrayerDistrict {
    return PrayerDistrict(
        id = districtId,
        cityId = cityId,
        nameTr = nameTr,
        nameEn = nameEn,
    )
}
