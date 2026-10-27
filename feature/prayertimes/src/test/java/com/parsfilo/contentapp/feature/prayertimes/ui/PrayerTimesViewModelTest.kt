package com.parsfilo.contentapp.feature.prayertimes.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.feature.prayertimes.R
import com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmSoundPlayer
import com.parsfilo.contentapp.feature.prayertimes.data.PrayerTimesRepository
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAlarmScheduleStatus
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
import kotlinx.coroutines.CompletableDeferred
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
    fun enableWithNoLocationKeepsIntentButReportsNoAlarm() = runTest(testDispatcher) {
        val repo = FakePrayerTimesRepository().apply { modeFlow.value = PrayerTimesMode.MANUAL }
        val viewModel = PrayerTimesViewModel(repo, prayerPreferencesDataSource, prayerAlarmSoundPlayer)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.onAlarmEnabledChanged(true)
        runCurrent()
        assertThat(repo.alarmSettingsFlow.value.enabled).isTrue()
        assertThat(viewModel.uiState.value.alarmScheduleStatus)
            .isEqualTo(PrayerAlarmScheduleStatus.NOT_SCHEDULED)
        assertThat(viewModel.snackBarMessage.value)
            .isEqualTo(UiText.StringResource(R.string.prayertimes_alarm_choose_location))
        collector.cancel()
    }

    @Test
    fun lateStartupRevalidationCannotOverrideNewerDisable() = runTest(testDispatcher) {
        val oldResult = CompletableDeferred<Boolean>()
        val repo = FakePrayerTimesRepository().apply {
            modeFlow.value = PrayerTimesMode.MANUAL
            alarmSettingsFlow.value = PrayerAlarmSettings(enabled = true)
            selectionFlow.value = PrayerLocationSelection(1, 2, 3, "old district")
            scheduleBarrier = oldResult
        }
        val vm = PrayerTimesViewModel(repo, prayerPreferencesDataSource, prayerAlarmSoundPlayer)
        val collector = backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertThat(repo.scheduleAttempts).isEqualTo(1)

        vm.onAlarmEnabledChanged(false)
        runCurrent()
        assertThat(vm.uiState.value.alarmScheduleStatus)
            .isEqualTo(PrayerAlarmScheduleStatus.DISABLED)

        oldResult.complete(true)
        runCurrent()
        assertThat(vm.uiState.value.alarmScheduleStatus)
            .isEqualTo(PrayerAlarmScheduleStatus.DISABLED)
        assertThat(repo.alarmSettingsFlow.value.enabled).isFalse()
        collector.cancel()
    }

    @Test
    fun changingDistrictClearsPreviouslyConfirmedScheduleBeforeRefresh() = runTest(testDispatcher) {
        val repo = FakePrayerTimesRepository().apply {
            modeFlow.value = PrayerTimesMode.MANUAL
            alarmSettingsFlow.value = PrayerAlarmSettings(enabled = true)
            selectionFlow.value = PrayerLocationSelection(1, 2, 3, "old district")
            scheduleResult = true
        }
        val vm = PrayerTimesViewModel(repo, prayerPreferencesDataSource, prayerAlarmSoundPlayer)
        val collector = backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertThat(vm.uiState.value.alarmScheduleStatus)
            .isEqualTo(PrayerAlarmScheduleStatus.SCHEDULED)

        val newDistrictRefresh = CompletableDeferred<Unit>()
        repo.districtRefreshBarrier = newDistrictRefresh
        repo.selectionFlow.value = PrayerLocationSelection(1, 2, 4, "new district")
        runCurrent()
        assertThat(vm.uiState.value.alarmScheduleStatus)
            .isEqualTo(PrayerAlarmScheduleStatus.UNKNOWN)

        newDistrictRefresh.complete(Unit)
        runCurrent()
        assertThat(vm.uiState.value.alarmScheduleStatus)
            .isEqualTo(PrayerAlarmScheduleStatus.SCHEDULED)
        collector.cancel()
    }

    @Test
    fun successfulRegistrationIsDistinctFromEnabledPreference() = runTest(testDispatcher) {
        val repo = FakePrayerTimesRepository().apply {
            modeFlow.value = PrayerTimesMode.MANUAL
            scheduleResult = true
        }
        val viewModel = PrayerTimesViewModel(repo, prayerPreferencesDataSource, prayerAlarmSoundPlayer)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.onAlarmEnabledChanged(true)
        runCurrent()
        assertThat(viewModel.uiState.value.alarmScheduleStatus)
            .isEqualTo(PrayerAlarmScheduleStatus.SCHEDULED)
        assertThat(viewModel.snackBarMessage.value).isNull()
        collector.cancel()
    }

    @Test
    fun restartWithDistrictRevalidatesFailedSchedule() = runTest(testDispatcher) {
        val repo = FakePrayerTimesRepository().apply {
            modeFlow.value = PrayerTimesMode.MANUAL
            alarmSettingsFlow.value = PrayerAlarmSettings(enabled = true)
            scheduleResult = false
            selectionFlow.value = PrayerLocationSelection(1, 2, 3, "test district")
        }
        val viewModel = PrayerTimesViewModel(repo, prayerPreferencesDataSource, prayerAlarmSoundPlayer)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        assertThat(repo.scheduleAttempts).isAtLeast(1)
        assertThat(viewModel.uiState.value.alarmScheduleStatus)
            .isEqualTo(PrayerAlarmScheduleStatus.NOT_SCHEDULED)
        collector.cancel()
    }

    @Test
    fun refreshAfterDataArrivesConfirmsPreviouslyFailedAlarm() = runTest(testDispatcher) {
        val repo = FakePrayerTimesRepository().apply {
            modeFlow.value = PrayerTimesMode.MANUAL
            alarmSettingsFlow.value = PrayerAlarmSettings(enabled = true)
            selectionFlow.value = PrayerLocationSelection(1, 2, 3, "test district")
            scheduleResult = false
        }
        val viewModel = PrayerTimesViewModel(repo, prayerPreferencesDataSource, prayerAlarmSoundPlayer)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        assertThat(viewModel.uiState.value.alarmScheduleStatus)
            .isEqualTo(PrayerAlarmScheduleStatus.NOT_SCHEDULED)
        repo.scheduleResult = true
        viewModel.refresh()
        runCurrent()
        assertThat(viewModel.uiState.value.alarmScheduleStatus)
            .isEqualTo(PrayerAlarmScheduleStatus.SCHEDULED)
        collector.cancel()
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
        val uiCollector = backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.resolveByDeviceLocation()
        runCurrent()

        assertThat(repository.committedAutoSelections).isEmpty()
        assertThat(repository.selectionFlow.value).isNull()
        assertThat(viewModel.uiState.value.pendingResolvedLocationName).isEqualTo("Test")
        viewModel.confirmResolvedLocationSuggestion()
        runCurrent()
        assertThat(repository.committedAutoSelections).hasSize(1)
        assertThat(repository.refreshCalls).contains(RefreshCall(districtId = 42, force = false))
        assertThat(viewModel.snackBarMessage.value).isEqualTo(
            UiText.StringResource(
                R.string.prayertimes_location_found,
                listOf("Test"),
            ),
        )
        uiCollector.cancel()
    }

    @Test
    fun `rejecting proposed district preserves accepted manual district and mode`() =
        runTest(testDispatcher) {
            val accepted = PrayerLocationSelection(1, 2, 7, "Accepted A")
            val proposed = PrayerLocationSelection(1, 2, 8, "Proposed B")
            val repository = FakePrayerTimesRepository().apply {
                modeFlow.value = PrayerTimesMode.MANUAL
                selectionFlow.value = accepted
                resolveResult = ResolveResult.Success(proposed)
            }
            val vm = PrayerTimesViewModel(repository, prayerPreferencesDataSource, prayerAlarmSoundPlayer)
            val collector = backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
            vm.resolveByDeviceLocation()
            runCurrent()
            assertThat(vm.uiState.value.pendingResolvedLocationName).isEqualTo("Proposed B")
            assertThat(repository.selectionFlow.value).isEqualTo(accepted)
            assertThat(repository.modeFlow.value).isEqualTo(PrayerTimesMode.MANUAL)
            vm.rejectResolvedLocationSuggestion()
            runCurrent()
            assertThat(repository.committedAutoSelections).isEmpty()
            assertThat(repository.selectionFlow.value).isEqualTo(accepted)
            assertThat(repository.modeFlow.value).isEqualTo(PrayerTimesMode.MANUAL)
            assertThat(repository.refreshCalls).doesNotContain(RefreshCall(8, false))
            collector.cancel()
        }

    @Test
    fun `accepting proposed district commits exactly once after yes`() =
        runTest(testDispatcher) {
            val accepted = PrayerLocationSelection(1, 2, 7, "Accepted A")
            val proposed = PrayerLocationSelection(1, 2, 8, "Proposed B")
            val repository = FakePrayerTimesRepository().apply {
                modeFlow.value = PrayerTimesMode.MANUAL
                selectionFlow.value = accepted
                resolveResult = ResolveResult.Success(proposed)
            }
            val vm = PrayerTimesViewModel(repository, prayerPreferencesDataSource, prayerAlarmSoundPlayer)
            val collector = backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
            vm.resolveByDeviceLocation()
            runCurrent()
            assertThat(repository.committedAutoSelections).isEmpty()
            assertThat(repository.refreshCalls).doesNotContain(RefreshCall(8, false))
            vm.confirmResolvedLocationSuggestion()
            vm.confirmResolvedLocationSuggestion()
            runCurrent()
            assertThat(repository.committedAutoSelections).containsExactly(proposed)
            assertThat(repository.selectionFlow.value).isEqualTo(proposed)
            assertThat(repository.modeFlow.value).isEqualTo(PrayerTimesMode.AUTO)
            assertThat(repository.refreshCalls).contains(RefreshCall(8, false))
            collector.cancel()
        }

    @Test
    fun `unaccepted proposal does not survive viewmodel recreation`() = runTest(testDispatcher) {
        val accepted = PrayerLocationSelection(1, 2, 7, "Accepted A")
        val proposed = PrayerLocationSelection(1, 2, 8, "Proposed B")
        val repository = FakePrayerTimesRepository().apply {
            modeFlow.value = PrayerTimesMode.MANUAL
            selectionFlow.value = accepted
            resolveResult = ResolveResult.Success(proposed)
        }
        val first = PrayerTimesViewModel(repository, prayerPreferencesDataSource, prayerAlarmSoundPlayer)
        val firstCollector = backgroundScope.launch { first.uiState.collect {} }
        runCurrent()
        first.resolveByDeviceLocation()
        runCurrent()
        assertThat(first.uiState.value.pendingResolvedLocationName).isEqualTo("Proposed B")
        val recreated = PrayerTimesViewModel(repository, prayerPreferencesDataSource, prayerAlarmSoundPlayer)
        val secondCollector = backgroundScope.launch { recreated.uiState.collect {} }
        runCurrent()
        assertThat(recreated.uiState.value.pendingResolvedLocationName).isNull()
        assertThat(recreated.uiState.value.selection).isEqualTo(accepted)
        assertThat(repository.committedAutoSelections).isEmpty()
        firstCollector.cancel()
        secondCollector.cancel()
    }

    @Test
    fun `same-district auto resolution needs no destructive confirmation`() =
        runTest(testDispatcher) {
            val accepted = PrayerLocationSelection(1, 2, 7, "Accepted A")
            val repository = FakePrayerTimesRepository().apply {
                modeFlow.value = PrayerTimesMode.MANUAL
                selectionFlow.value = accepted
                resolveResult = ResolveResult.Success(accepted)
            }
            val vm = PrayerTimesViewModel(repository, prayerPreferencesDataSource, prayerAlarmSoundPlayer)
            val collector = backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
            vm.resolveByDeviceLocation()
            runCurrent()
            assertThat(repository.committedAutoSelections).containsExactly(accepted)
            assertThat(vm.uiState.value.pendingResolvedLocationName).isNull()
            collector.cancel()
        }

    @Test
    fun `stale asynchronous result cannot override newly selected manual mode`() =
        runTest(testDispatcher) {
            val accepted = PrayerLocationSelection(1, 2, 7, "Accepted A")
            val rejected = PrayerLocationSelection(1, 2, 8, "Stale B")
            val result = CompletableDeferred<ResolveResult>()
            val repository = FakePrayerTimesRepository().apply {
                modeFlow.value = PrayerTimesMode.MANUAL
                selectionFlow.value = accepted
                resolveBarrier = result
            }
            val vm = PrayerTimesViewModel(repository, prayerPreferencesDataSource, prayerAlarmSoundPlayer)
            val collector = backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
            vm.resolveByDeviceLocation()
            runCurrent()
            vm.onModeChanged(PrayerTimesMode.MANUAL)
            runCurrent()
            result.complete(ResolveResult.Success(rejected))
            runCurrent()
            assertThat(vm.uiState.value.pendingResolvedLocationName).isNull()
            assertThat(repository.selectionFlow.value).isEqualTo(accepted)
            assertThat(repository.committedAutoSelections).isEmpty()
            collector.cancel()
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
        // A failed GPS lookup may not switch the accepted manual location to AUTO.
        assertThat(repository.modeFlow.value).isEqualTo(PrayerTimesMode.MANUAL)
    }

    @Test
    fun `location picker filters all three steps without losing canonical IDs`() = runTest(testDispatcher) {
        val turkey = PrayerCountry(id = 90, nameTr = "TÜRKİYE", nameEn = "Turkey")
        val germany = PrayerCountry(id = 49, nameTr = "ALMANYA", nameEn = "Germany")
        val city = PrayerCity(id = 18, countryId = 90, nameTr = "ÇANKIRI", nameEn = "Cankiri")
        val district = PrayerDistrict(id = 181, cityId = 18, nameTr = "ŞABANÖZÜ", nameEn = "Sabanozu")
        val repository = FakePrayerTimesRepository().apply {
            countries = listOf(turkey, germany)
            citiesByCountry[turkey.id] = listOf(city)
            districtsByCity[city.id] = listOf(district)
        }
        val viewModel = PrayerLocationPickerViewModel(repository)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onSearchQueryChanged("turkiye")
        runCurrent()
        assertThat(viewModel.uiState.value.filteredCountries).containsExactly(turkey)

        viewModel.onCountrySelected(turkey)
        runCurrent()
        viewModel.onSearchQueryChanged("cankiri")
        runCurrent()
        assertThat(viewModel.uiState.value.filteredCities).containsExactly(city)

        viewModel.onCitySelected(city)
        runCurrent()
        viewModel.onSearchQueryChanged("sabanozu")
        runCurrent()
        assertThat(viewModel.uiState.value.filteredDistricts).containsExactly(district)
        assertThat(viewModel.uiState.value.filteredDistricts.single().id).isEqualTo(181)

        viewModel.onSearchQueryChanged("unknown")
        runCurrent()
        assertThat(viewModel.uiState.value.filteredDistricts).isEmpty()
        viewModel.onSearchQueryChanged("")
        runCurrent()
        assertThat(viewModel.uiState.value.filteredDistricts).containsExactly(district)
        collector.cancel()
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

    @Test
    fun pickerEmptyCountryListCanRetryWithoutChangingSearch() = runTest(testDispatcher) {
        val country = PrayerCountry(id = 90, nameTr = "TÜRKİYE", nameEn = "Turkey")
        val repository = FakePrayerTimesRepository()
        val vm = PrayerLocationPickerViewModel(repository)
        val collector = backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertThat(vm.uiState.value.countries).isEmpty()
        vm.onSearchQueryChanged("turkiye")
        repository.countries = listOf(country)
        vm.retryCurrentStep()
        runCurrent()
        assertThat(repository.forcedCountryLoads).isEqualTo(1)
        assertThat(vm.uiState.value.searchQuery).isEqualTo("turkiye")
        assertThat(vm.uiState.value.filteredCountries).containsExactly(country)
        collector.cancel()
    }

    @Test
    fun pickerShowsRetryableLoadingErrorAndClearsItAfterRecovery() = runTest(testDispatcher) {
        val repository = FakePrayerTimesRepository().apply { failCountryLoad = true }
        val vm = PrayerLocationPickerViewModel(repository)
        val collector = backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.loadErrorRes)
            .isEqualTo(R.string.prayertimes_picker_load_failed)
        repository.failCountryLoad = false
        repository.countries = listOf(PrayerCountry(90, "TÜRKİYE", "Turkey"))
        vm.retryCurrentStep()
        runCurrent()
        assertThat(vm.uiState.value.loadErrorRes).isNull()
        assertThat(vm.uiState.value.filteredCountries).hasSize(1)
        collector.cancel()
    }

    @Test
    fun pickerEmptyOfflineFallbackSignalsErrorAndRetryRecovers() = runTest(testDispatcher) {
        val country = PrayerCountry(id = 90, nameTr = "TÜRKİYE", nameEn = "Turkey")
        val repository = FakePrayerTimesRepository().apply { countryLoadFailed = true }
        val vm = PrayerLocationPickerViewModel(repository)
        val collector = backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertThat(vm.uiState.value.countries).isEmpty()
        assertThat(vm.uiState.value.loadErrorRes)
            .isEqualTo(R.string.prayertimes_picker_load_failed)
        vm.retryCurrentStep()
        runCurrent()
        assertThat(vm.uiState.value.loadErrorRes)
            .isEqualTo(R.string.prayertimes_picker_load_failed)
        repository.countryLoadFailed = false
        repository.countries = listOf(country)
        vm.retryCurrentStep()
        runCurrent()
        assertThat(vm.uiState.value.loadErrorRes).isNull()
        assertThat(vm.uiState.value.filteredCountries).containsExactly(country)
        collector.cancel()
    }

    @Test
    fun pickerFailedCityFetchPreservesParentAndRetryWorks() = runTest(testDispatcher) {
        val country = PrayerCountry(90, "TÜRKİYE", "Turkey")
        val city = PrayerCity(18, 90, "ÇANKIRI", "Cankiri")
        val repo = FakePrayerTimesRepository().apply {
            countries = listOf(country)
            failCityLoad = true
        }
        val vm = PrayerLocationPickerViewModel(repo)
        val collector = backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.onCountrySelected(country)
        runCurrent()
        assertThat(vm.uiState.value.step).isEqualTo(LocationStep.CITY)
        assertThat(vm.uiState.value.selectedCountry).isEqualTo(country)
        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.loadErrorRes)
            .isEqualTo(R.string.prayertimes_picker_load_failed)
        repo.failCityLoad = false
        repo.citiesByCountry[country.id] = listOf(city)
        vm.retryCurrentStep()
        runCurrent()
        assertThat(vm.uiState.value.cities).containsExactly(city)
        assertThat(vm.uiState.value.selectedCountry).isEqualTo(country)
        assertThat(vm.uiState.value.loadErrorRes).isNull()
        collector.cancel()
    }

    @Test
    fun pickerSaveFailureRetainsSelectedDistrictAndRetrySucceeds() = runTest(testDispatcher) {
        val country = PrayerCountry(90, "TÜRKİYE", "Turkey")
        val city = PrayerCity(34, 90, "İSTANBUL", "Istanbul")
        val district = PrayerDistrict(341, 34, "KADIKÖY", "Kadikoy")
        val repo = FakePrayerTimesRepository().apply {
            countries = listOf(country)
            citiesByCountry[90] = listOf(city)
            districtsByCity[34] = listOf(district)
            failManualSave = true
        }
        val vm = PrayerLocationPickerViewModel(repo)
        val collector = backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.onCountrySelected(country)
        runCurrent()
        vm.onCitySelected(city)
        runCurrent()
        vm.onDistrictSelected(district)
        var savedCount = 0
        vm.saveSelection { savedCount++ }
        runCurrent()
        assertThat(savedCount).isEqualTo(0)
        assertThat(vm.uiState.value.selectedDistrict).isEqualTo(district)
        assertThat(vm.uiState.value.saveErrorRes)
            .isEqualTo(R.string.prayertimes_picker_save_failed)
        repo.failManualSave = false
        vm.saveSelection { savedCount++ }
        runCurrent()
        assertThat(savedCount).isEqualTo(1)
        assertThat(vm.uiState.value.saveErrorRes).isNull()
        assertThat(repo.selectionFlow.value?.districtId).isEqualTo(district.id)
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
    var resolveBarrier: CompletableDeferred<ResolveResult>? = null
    var countries: List<PrayerCountry> = emptyList()
    var countryLoadFailed = false
    var failCountryLoad = false
    var failCityLoad = false
    var failDistrictLoad = false
    var failManualSave = false
    var forcedCountryLoads = 0
    val citiesByCountry: MutableMap<Int, List<PrayerCity>> = mutableMapOf()
    val districtsByCity: MutableMap<Int, List<PrayerDistrict>> = mutableMapOf()

    override fun observeCurrentSelection(): Flow<PrayerLocationSelection?> = selectionFlow

    override fun observeMode(): Flow<PrayerTimesMode> = modeFlow

    override fun observeAlarmSettings(): Flow<PrayerAlarmSettings> = alarmSettingsFlow

    override fun observeTodayAndUpcoming(districtId: Int): Flow<List<PrayerTimesDay>> {
        return timesFlow
    }

    override suspend fun refreshIfNeeded(
        districtId: Int,
        force: Boolean,
        scheduleAlarmAfterRefresh: Boolean,
    ): RefreshResult {
        refreshCalls += RefreshCall(districtId = districtId, force = force)
        if (districtId == 4) districtRefreshBarrier?.await()
        return refreshResult
    }

    val committedAutoSelections = mutableListOf<PrayerLocationSelection>()

    override suspend fun resolveDeviceLocationProposal(): ResolveResult {
        resolveCalls += 1
        return resolveBarrier?.await() ?: resolveResult
    }

    override suspend fun commitResolvedAutoSelection(selection: PrayerLocationSelection) {
        committedAutoSelections += selection
        selectionFlow.value = selection
        modeFlow.value = PrayerTimesMode.AUTO
    }

    override suspend fun suggestManualSelectionByDeviceLocation(): PrayerLocationSuggestion? =
        suggestion

    override suspend fun getCountries(forceRefresh: Boolean): List<PrayerCountry> {
        if (forceRefresh) forcedCountryLoads++
        if (failCountryLoad) throw java.io.IOException("synthetic offline fixture")
        return countries
    }

    override suspend fun getCountriesForPicker(
        forceRefresh: Boolean,
    ): com.parsfilo.contentapp.feature.prayertimes.data.LocationLoadResult<PrayerCountry> {
        val result = getCountries(forceRefresh)
        return com.parsfilo.contentapp.feature.prayertimes.data.LocationLoadResult(result, countryLoadFailed)
    }

    override suspend fun getCities(countryId: Int, forceRefresh: Boolean): List<PrayerCity> {
        if (failCityLoad) throw java.io.IOException("synthetic city offline")
        return citiesByCountry[countryId].orEmpty()
    }

    override suspend fun getDistricts(cityId: Int, forceRefresh: Boolean): List<PrayerDistrict> {
        if (failDistrictLoad) throw java.io.IOException("synthetic district offline")
        return districtsByCity[cityId].orEmpty()
    }

    override suspend fun setMode(mode: PrayerTimesMode) {
        modeFlow.value = mode
    }

    var scheduleResult = false
    var scheduleAttempts = 0
    var scheduleBarrier: CompletableDeferred<Boolean>? = null
    var districtRefreshBarrier: CompletableDeferred<Unit>? = null

    override suspend fun setAlarmEnabled(enabled: Boolean): Boolean {
        alarmSettingsFlow.value = alarmSettingsFlow.value.copy(enabled = enabled)
        return enabled && scheduleResult
    }

    override suspend fun setAlarmOffsetMinutes(minutes: Int): Boolean {
        alarmSettingsFlow.value = alarmSettingsFlow.value.copy(offsetMinutes = minutes)
        return alarmSettingsFlow.value.enabled && scheduleResult
    }

    override suspend fun setSelectedAlarmPrayerKeys(keys: Set<String>): Boolean {
        alarmSettingsFlow.value = alarmSettingsFlow.value.copy(selectedPrayerKeys = keys)
        return alarmSettingsFlow.value.enabled && scheduleResult
    }

    override suspend fun tryScheduleAlarm(): Boolean {
        scheduleAttempts += 1
        return scheduleBarrier?.await() ?: (alarmSettingsFlow.value.enabled && scheduleResult)
    }

    override suspend fun setManualSelection(countryId: Int, cityId: Int, districtId: Int) {
        if (failManualSave) throw java.io.IOException("synthetic persistence failure")
        selectionFlow.value = PrayerLocationSelection(
            countryId = countryId,
            cityId = cityId,
            districtId = districtId,
            displayName = "$districtId",
        )
    }
}
