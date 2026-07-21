package com.parsfilo.contentapp.feature.prayertimes.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.feature.prayertimes.R
import com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmSoundPlayer
import com.parsfilo.contentapp.feature.prayertimes.data.PrayerTimesRepository
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
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrayerTimesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val prayerPreferencesDataSource: PrayerPreferencesDataSource = mockk(relaxed = true)
    private val prayerAlarmSoundPlayer: PrayerAlarmSoundPlayer = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `auto mode with unresolved location keeps user on home and shows message`() =
        runTest(testDispatcher) {
            val repository = FakePrayerTimesRepository().apply {
                modeFlow.value = PrayerTimesMode.AUTO
                selectionFlow.value = null
                resolveResult = ResolveResult.NoMatch
            }

            val viewModel = PrayerTimesViewModel(
                repository,
                prayerPreferencesDataSource,
                prayerAlarmSoundPlayer
            )

            viewModel.events.test {
                runCurrent()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }

            assertThat(viewModel.snackBarMessage.value).isEqualTo(
                UiText.StringResource(R.string.prayertimes_resolve_failed),
            )
        }

    @Test
    fun `selection change triggers background refresh`() = runTest(testDispatcher) {
        val repository = FakePrayerTimesRepository().apply {
            modeFlow.value = PrayerTimesMode.MANUAL
            selectionFlow.value = null
        }
        val viewModel =
            PrayerTimesViewModel(repository, prayerPreferencesDataSource, prayerAlarmSoundPlayer)

        repository.selectionFlow.value = PrayerLocationSelection(
            countryId = 1,
            cityId = 2,
            districtId = 3,
            displayName = "Kadikoy, Istanbul, Turkiye",
        )
        runCurrent()

        assertThat(repository.refreshCalls).contains(RefreshCall(districtId = 3, force = false))
        assertThat(viewModel.snackBarMessage.value).isNull()
    }

    @Test
    fun `manual refresh with invalid selection shows actionable message`() =
        runTest(testDispatcher) {
            val repository = FakePrayerTimesRepository().apply {
                modeFlow.value = PrayerTimesMode.MANUAL
                selectionFlow.value = PrayerLocationSelection(
                    countryId = 1,
                    cityId = 2,
                    districtId = 11,
                    displayName = "Test District",
                )
                refreshResult = RefreshResult.SkippedFresh
            }
            val viewModel = PrayerTimesViewModel(
                repository,
                prayerPreferencesDataSource,
                prayerAlarmSoundPlayer
            )
            val uiCollector = backgroundScope.launch { viewModel.uiState.collect {} }
            runCurrent()

            repository.refreshResult = RefreshResult.InvalidSelection
            viewModel.refresh()
            runCurrent()

            assertThat(viewModel.snackBarMessage.value).isEqualTo(
                UiText.StringResource(R.string.prayertimes_selection_refresh_needed),
            )
            uiCollector.cancel()
        }

    @Test
    fun `resolve success refreshes selected district`() = runTest(testDispatcher) {
        val repository = FakePrayerTimesRepository().apply {
            modeFlow.value = PrayerTimesMode.AUTO
            selectionFlow.value = null
            resolveResult = ResolveResult.Success(
                PrayerLocationSelection(
                    countryId = 1,
                    cityId = 2,
                    districtId = 42,
                    displayName = "Test",
                )
            )
        }

        val viewModel =
            PrayerTimesViewModel(repository, prayerPreferencesDataSource, prayerAlarmSoundPlayer)
        runCurrent()
        viewModel.resolveByDeviceLocation()
        runCurrent()

        assertThat(repository.refreshCalls).contains(RefreshCall(districtId = 42, force = false))
        assertThat(viewModel.snackBarMessage.value).isEqualTo(
            UiText.StringResource(
                R.string.prayertimes_location_found,
                listOf("Test"),
            ),
        )
    }

    @Test
    fun `manual resolve action opens picker on no match`() = runTest(testDispatcher) {
        val repository = FakePrayerTimesRepository().apply {
            modeFlow.value = PrayerTimesMode.MANUAL
            selectionFlow.value = PrayerLocationSelection(
                countryId = 1,
                cityId = 2,
                districtId = 3,
                displayName = "Manual Selection",
            )
            resolveResult = ResolveResult.NoMatch
        }

        val viewModel =
            PrayerTimesViewModel(repository, prayerPreferencesDataSource, prayerAlarmSoundPlayer)
        viewModel.events.test {
            runCurrent()
            viewModel.resolveByDeviceLocation()
            runCurrent()
            assertThat(awaitItem()).isEqualTo(PrayerTimesUiEvent.OpenLocationPicker)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching to auto mode triggers location resolve`() = runTest(testDispatcher) {
        val repository = FakePrayerTimesRepository().apply {
            modeFlow.value = PrayerTimesMode.MANUAL
            selectionFlow.value = PrayerLocationSelection(
                countryId = 1,
                cityId = 2,
                districtId = 3,
                displayName = "Manual Selection",
            )
            resolveResult = ResolveResult.NoMatch
        }

        val viewModel =
            PrayerTimesViewModel(repository, prayerPreferencesDataSource, prayerAlarmSoundPlayer)
        runCurrent()

        viewModel.onModeChanged(PrayerTimesMode.AUTO)
        runCurrent()

        assertThat(repository.resolveCalls).isEqualTo(1)
        assertThat(repository.modeFlow.value).isEqualTo(PrayerTimesMode.AUTO)
    }

    @Test
    fun `location picker preselects suggested country and city`() = runTest(testDispatcher) {
        val repository = FakePrayerTimesRepository().apply {
            countries = listOf(
                PrayerCountry(id = 90, nameTr = "Turkiye", nameEn = "Turkey")
            )
            citiesByCountry[90] = listOf(
                PrayerCity(id = 34, countryId = 90, nameTr = "Istanbul", nameEn = "Istanbul")
            )
            suggestion = PrayerLocationSuggestion(countryId = 90, cityId = 34)
        }

        val viewModel = PrayerLocationPickerViewModel(repository)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        repeat(3) { runCurrent() }

        assertThat(viewModel.uiState.value.selectedCountry?.id).isEqualTo(90)
        assertThat(viewModel.uiState.value.selectedCity?.id).isEqualTo(34)
        collector.cancel()
    }
}

private data class RefreshCall(
    val districtId: Int,
    val force: Boolean,
)

private class FakePrayerTimesRepository : PrayerTimesRepository {
    val modeFlow = MutableStateFlow(PrayerTimesMode.AUTO)
    val selectionFlow = MutableStateFlow<PrayerLocationSelection?>(null)
    val timesFlow = MutableStateFlow<List<PrayerTimesDay>>(emptyList())
    val alarmSettingsFlow = MutableStateFlow(PrayerAlarmSettings())

    var refreshResult: RefreshResult = RefreshResult.SkippedFresh
    var resolveResult: ResolveResult = ResolveResult.NoMatch
    var suggestion: PrayerLocationSuggestion? = null
    val refreshCalls = mutableListOf<RefreshCall>()
    var resolveCalls: Int = 0
    var countries: List<PrayerCountry> = emptyList()
    val citiesByCountry: MutableMap<Int, List<PrayerCity>> = mutableMapOf()
    val districtsByCity: MutableMap<Int, List<PrayerDistrict>> = mutableMapOf()

    override fun observeCurrentSelection(): Flow<PrayerLocationSelection?> = selectionFlow

    override fun observeMode(): Flow<PrayerTimesMode> = modeFlow

    override fun observeAlarmSettings(): Flow<PrayerAlarmSettings> = alarmSettingsFlow

    override fun observeTodayAndUpcoming(districtId: Int): Flow<List<PrayerTimesDay>> {
        return timesFlow
    }

    override suspend fun refreshIfNeeded(districtId: Int, force: Boolean): RefreshResult {
        refreshCalls += RefreshCall(districtId = districtId, force = force)
        return refreshResult
    }

    override suspend fun resolveAndSelectByDeviceLocation(): ResolveResult {
        resolveCalls += 1
        val result = resolveResult
        if (result is ResolveResult.Success) {
            selectionFlow.value = result.selection
        }
        return result
    }

    override suspend fun suggestManualSelectionByDeviceLocation(): PrayerLocationSuggestion? =
        suggestion

    override suspend fun getCountries(forceRefresh: Boolean): List<PrayerCountry> = countries

    override suspend fun getCities(countryId: Int, forceRefresh: Boolean): List<PrayerCity> {
        return citiesByCountry[countryId].orEmpty()
    }

    override suspend fun getDistricts(cityId: Int, forceRefresh: Boolean): List<PrayerDistrict> {
        return districtsByCity[cityId].orEmpty()
    }

    override suspend fun setMode(mode: PrayerTimesMode) {
        modeFlow.value = mode
    }

    override suspend fun setAlarmEnabled(enabled: Boolean) {
        alarmSettingsFlow.value = alarmSettingsFlow.value.copy(enabled = enabled)
    }

    override suspend fun setAlarmOffsetMinutes(minutes: Int) {
        alarmSettingsFlow.value = alarmSettingsFlow.value.copy(offsetMinutes = minutes)
    }

    override suspend fun setSelectedAlarmPrayerKeys(keys: Set<String>) {
        alarmSettingsFlow.value = alarmSettingsFlow.value.copy(selectedPrayerKeys = keys)
    }

    override suspend fun setManualSelection(countryId: Int, cityId: Int, districtId: Int) {
        selectionFlow.value = PrayerLocationSelection(
            countryId = countryId,
            cityId = cityId,
            districtId = districtId,
            displayName = "$districtId",
        )
    }
}
