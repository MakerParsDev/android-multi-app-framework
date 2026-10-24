package com.parsfilo.contentapp.feature.prayertimes.data

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.database.dao.prayer.PrayerTimesDao
import com.parsfilo.contentapp.core.database.model.prayer.PrayerCityEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerCountryEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerDistrictEntity
import com.parsfilo.contentapp.core.database.model.prayer.PrayerSyncStateEntity
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesData
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmScheduler
import com.parsfilo.contentapp.feature.prayertimes.model.RefreshResult
import com.parsfilo.contentapp.feature.prayertimes.model.ResolveResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DefaultPrayerTimesRepositoryTest {

    private val apiClient: EzanVaktiApiClient = mockk()
    private val prayerTimesDao: PrayerTimesDao = mockk()
    private val preferencesDataSource: PrayerPreferencesDataSource = mockk(relaxed = true)
    private val locationResolver: PrayerLocationResolver = mockk(relaxed = true)
    private val prayerAlarmScheduler: PrayerAlarmScheduler = mockk(relaxed = true)
    private val appAnalytics: AppAnalytics = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: DefaultPrayerTimesRepository

    @Before
    fun setUp() {
        repository = DefaultPrayerTimesRepository(
            apiClient = apiClient,
            prayerTimesDao = prayerTimesDao,
            prayerPreferencesDataSource = preferencesDataSource,
            locationResolver = locationResolver,
            prayerAlarmScheduler = prayerAlarmScheduler,
            appAnalytics = appAnalytics,
            ioDispatcher = testDispatcher,
        )

        every { preferencesDataSource.preferences } returns flowOf(
            PrayerPreferencesData(
                mode = com.parsfilo.contentapp.core.datastore.PrayerTimesMode.AUTO.name,
                selectedCountryId = null,
                selectedCityId = null,
                selectedDistrictId = null,
                lastAutoDistrictId = null,
                locationPermissionPrompted = false,
                lastSuccessfulRefreshAt = 0L,
                alarmEnabled = false,
                alarmOffsetMinutes = 0,
                selectedAlarmPrayerKeys = emptySet(),
                alarmSoundUri = null,
            )
        )

        every { appAnalytics.logEvent(any(), any()) } just Runs
        coEvery { prayerTimesDao.touchDistrictAccess(any(), any()) } just Runs
    }

    @Test
    fun `GPS proposal cannot persist location mode or alarms before user confirmation`() =
        runTest(testDispatcher) {
            every { locationResolver.hasLocationPermission() } returns true
            coEvery { locationResolver.resolveAddressCandidate() } returns PrayerAddressCandidate(
                country = "Türkiye",
                city = "İzmir",
                district = "Konak",
            )
            val now = System.currentTimeMillis()
            coEvery { prayerTimesDao.getCountries() } returns listOf(
                PrayerCountryEntity(90, "TÜRKİYE", "Turkey", now),
            )
            coEvery { prayerTimesDao.getMinCountryFetchedAt() } returns now
            coEvery { prayerTimesDao.getCities(90) } returns listOf(
                PrayerCityEntity(35, 90, "İZMİR", "Izmir", now),
            )
            coEvery { prayerTimesDao.getMinCityFetchedAt(90) } returns now
            coEvery { prayerTimesDao.getDistricts(35) } returns listOf(
                PrayerDistrictEntity(3501, 35, "KONAK", "Konak", now),
            )
            coEvery { prayerTimesDao.getMinDistrictFetchedAt(35) } returns now

            val proposal = repository.resolveDeviceLocationProposal()
            assertThat(proposal).isInstanceOf(ResolveResult.Success::class.java)
            val selection = (proposal as ResolveResult.Success).selection
            assertThat(selection.districtId).isEqualTo(3501)
            coVerify(exactly = 0) { preferencesDataSource.setMode(any()) }
            coVerify(exactly = 0) { preferencesDataSource.setManualSelection(any(), any(), any()) }
            coVerify(exactly = 0) {
                preferencesDataSource.setAcceptedAutoSelection(any(), any(), any())
            }
            coVerify(exactly = 0) { preferencesDataSource.setLastAutoDistrictId(any()) }
            coVerify(exactly = 0) { prayerTimesDao.touchDistrictAccess(any(), any()) }
            coVerify(exactly = 0) { prayerAlarmScheduler.scheduleNextForCurrentFlavor() }

            repository.commitResolvedAutoSelection(selection)
            coVerify(exactly = 1) {
                preferencesDataSource.setAcceptedAutoSelection(90, 35, 3501)
            }
            coVerify(exactly = 0) { preferencesDataSource.setManualSelection(any(), any(), any()) }
            coVerify(exactly = 0) { preferencesDataSource.setLastAutoDistrictId(any()) }
            coVerify(exactly = 0) { preferencesDataSource.setMode(any()) }
        }

    @Test
    fun `permission rejection does not mutate previously accepted location`() =
        runTest(testDispatcher) {
            every { locationResolver.hasLocationPermission() } returns false
            assertThat(repository.resolveDeviceLocationProposal())
                .isEqualTo(ResolveResult.PermissionDenied)
            coVerify(exactly = 0) { preferencesDataSource.setManualSelection(any(), any(), any()) }
            coVerify(exactly = 0) { preferencesDataSource.setLastAutoDistrictId(any()) }
        }

    @Test
    fun `cancelled geolocation must propagate instead of proposing stale location`() =
        runTest(testDispatcher) {
            every { locationResolver.hasLocationPermission() } returns true
            coEvery { locationResolver.resolveAddressCandidate() } throws
                CancellationException("synthetic location cancellation")
            val outcome = runCatching { repository.resolveDeviceLocationProposal() }
            assertThat(outcome.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
            coVerify(exactly = 0) { preferencesDataSource.setManualSelection(any(), any(), any()) }
        }

    @Test
    fun enabledPreferencePreservesIntentWhileFalseAlarmResultIsObservable() = runTest(testDispatcher) {
        coEvery { prayerAlarmScheduler.scheduleNextForCurrentFlavor() } returns false
        val scheduled = repository.setAlarmEnabled(true)
        assertThat(scheduled).isFalse()
        coVerify(exactly = 1) { preferencesDataSource.setAlarmEnabled(true) }
        coVerify(exactly = 1) { prayerAlarmScheduler.scheduleNextForCurrentFlavor() }
    }

    @Test
    fun schedulerExceptionIsReportedAsFailure() = runTest(testDispatcher) {
        coEvery { prayerAlarmScheduler.scheduleNextForCurrentFlavor() } throws
            IllegalStateException("synthetic alarm failure")
        assertThat(repository.setAlarmEnabled(true)).isFalse()
        coVerify(exactly = 1) { preferencesDataSource.setAlarmEnabled(true) }
    }

    @Test
    fun schedulerCancellationPropagates() = runTest(testDispatcher) {
        coEvery { prayerAlarmScheduler.scheduleNextForCurrentFlavor() } throws
            CancellationException("cancelled")
        val result = runCatching { repository.setAlarmEnabled(true) }
        assertThat(result.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun `refreshIfNeeded returns SkippedFresh when cache is still healthy`() = runTest(testDispatcher) {
        coEvery { prayerTimesDao.getSyncState(DISTRICT_ID) } returns PrayerSyncStateEntity(
            districtId = DISTRICT_ID,
            lastSyncAt = System.currentTimeMillis(),
            coverageStart = "2026-02-01",
            coverageEnd = "2999-12-31",
            lastAccessAt = System.currentTimeMillis(),
        )
        coEvery { prayerTimesDao.countByDistrictAndDate(DISTRICT_ID, any()) } returns 1

        val result = repository.refreshIfNeeded(districtId = DISTRICT_ID, force = false)

        assertThat(result).isEqualTo(RefreshResult.SkippedFresh)
        verify(exactly = 0) { apiClient.getPrayerTimes(any()) }
        verify { appAnalytics.logEvent("prayer_cache_hit") }
    }

    @Test
    fun `refreshIfNeeded serves stale cache on network failure`() = runTest(testDispatcher) {
        coEvery { prayerTimesDao.getSyncState(DISTRICT_ID) } returns PrayerSyncStateEntity(
            districtId = DISTRICT_ID,
            lastSyncAt = System.currentTimeMillis() - STALE_MILLIS,
            coverageStart = "2026-02-01",
            coverageEnd = "2999-12-31",
            lastAccessAt = System.currentTimeMillis(),
        )
        coEvery { prayerTimesDao.countByDistrictAndDate(DISTRICT_ID, any()) } returns 1
        every { apiClient.getPrayerTimes(DISTRICT_ID) } throws IOException("temporary outage")

        val result = repository.refreshIfNeeded(districtId = DISTRICT_ID, force = false)

        assertThat(result).isEqualTo(RefreshResult.ServedFromCache)
    }

    @Test
    fun `refreshIfNeeded returns Failed when no cache and network fails`() = runTest(testDispatcher) {
        coEvery { prayerTimesDao.getSyncState(DISTRICT_ID) } returns null
        coEvery { prayerTimesDao.countByDistrictAndDate(DISTRICT_ID, any()) } returns 0
        every { apiClient.getPrayerTimes(DISTRICT_ID) } throws IOException("offline")

        val result = repository.refreshIfNeeded(districtId = DISTRICT_ID, force = false)

        assertThat(result).isInstanceOf(RefreshResult.Failed::class.java)
    }

    @Test
    fun `refreshIfNeeded propagates cancellation instead of returning Failed`() = runTest(testDispatcher) {
        coEvery { prayerTimesDao.getSyncState(DISTRICT_ID) } returns null
        coEvery { prayerTimesDao.countByDistrictAndDate(DISTRICT_ID, any()) } returns 0
        every { apiClient.getPrayerTimes(DISTRICT_ID) } throws CancellationException("cancelled")

        val attempt = runCatching { repository.refreshIfNeeded(districtId = DISTRICT_ID) }

        assertThat(attempt.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun `refreshIfNeeded returns InvalidSelection when api reports 422`() = runTest(testDispatcher) {
        coEvery { prayerTimesDao.getSyncState(DISTRICT_ID) } returns null
        coEvery { prayerTimesDao.countByDistrictAndDate(DISTRICT_ID, any()) } returns 0
        every {
            apiClient.getPrayerTimes(DISTRICT_ID)
        } throws EzanVaktiHttpException(statusCode = 422, endpoint = "/vakitler/$DISTRICT_ID")

        val result = repository.refreshIfNeeded(districtId = DISTRICT_ID, force = false)

        assertThat(result).isEqualTo(RefreshResult.InvalidSelection)
    }

    @Test
    fun `refreshIfNeeded persists remote data and prunes LRU districts`() = runTest(testDispatcher) {
        coEvery { prayerTimesDao.getSyncState(DISTRICT_ID) } returns null
        coEvery { prayerTimesDao.countByDistrictAndDate(DISTRICT_ID, any()) } returns 0
        every { apiClient.getPrayerTimes(DISTRICT_ID) } returns listOf(
            ApiPrayerTime(
                miladiDateShort = "18.02.2026",
                imsak = "05:50",
                gunes = "07:15",
                ogle = "13:10",
                ikindi = "16:20",
                aksam = "18:52",
                yatsi = "20:10",
            ),
            ApiPrayerTime(
                miladiDateShort = "19.02.2026",
                imsak = "05:48",
                gunes = "07:13",
                ogle = "13:10",
                ikindi = "16:21",
                aksam = "18:53",
                yatsi = "20:11",
            ),
        )
        coEvery { prayerTimesDao.upsertPrayerTimes(any()) } just Runs
        coEvery { prayerTimesDao.deletePrayerTimesOutsideRange(any(), any(), any()) } just Runs
        coEvery { prayerTimesDao.upsertSyncState(any()) } just Runs
        coEvery { preferencesDataSource.setLastSuccessfulRefreshAt(any()) } just Runs
        coEvery { prayerTimesDao.getDistrictIdsByRecentAccess() } returns listOf(34, 33, 32, 31)
        coEvery { prayerTimesDao.deletePrayerTimesByDistrict(any()) } just Runs
        coEvery { prayerTimesDao.deleteSyncStateByDistrict(any()) } just Runs

        val result = repository.refreshIfNeeded(districtId = DISTRICT_ID, force = false)

        assertThat(result).isEqualTo(RefreshResult.Refreshed)
        coVerify(exactly = 1) { prayerTimesDao.upsertPrayerTimes(match { it.size == 2 }) }
        coVerify { prayerTimesDao.deletePrayerTimesByDistrict(31) }
        coVerify { prayerTimesDao.deleteSyncStateByDistrict(31) }
    }

    private companion object {
        private const val DISTRICT_ID = 34
        private const val STALE_MILLIS = 13L * 60 * 60 * 1000
    }
}
