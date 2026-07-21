package com.parsfilo.contentapp.feature.prayertimes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.feature.prayertimes.R
import com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerAlarmSoundPlayer
import com.parsfilo.contentapp.feature.prayertimes.data.HijriCalendarConverter
import com.parsfilo.contentapp.feature.prayertimes.data.NextPrayerInfo
import com.parsfilo.contentapp.feature.prayertimes.data.PrayerDateTime
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PrayerTimesViewModel @Inject constructor(
    private val repository: PrayerTimesRepository,
    private val prayerPreferencesDataSource: PrayerPreferencesDataSource,
    private val prayerAlarmSoundPlayer: PrayerAlarmSoundPlayer,
) : ViewModel() {

    private val _snackBarMessage = MutableStateFlow<UiText?>(null)
    private val resolvingLocationState = MutableStateFlow(false)
    private val refreshingState = MutableStateFlow(false)
    private val pendingResolvedLocationNameState = MutableStateFlow<String?>(null)
    private val _events = MutableSharedFlow<PrayerTimesUiEvent>(extraBufferCapacity = 1)

    val snackBarMessage: StateFlow<UiText?> = _snackBarMessage
    val events = _events.asSharedFlow()

    val currentTimeMillis: StateFlow<Long> = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = System.currentTimeMillis(),
    )

    init {
        viewModelScope.launch {
            repository.observeCurrentSelection()
                .map { it?.districtId }
                .distinctUntilChanged()
                .collect { districtId ->
                    if (districtId != null) {
                        val result = repository.refreshIfNeeded(districtId = districtId, force = false)
                        when (result) {
                            RefreshResult.InvalidSelection -> {
                                _snackBarMessage.value = UiText.StringResource(
                                    R.string.prayertimes_selection_refresh_needed,
                                )
                            }

                            is RefreshResult.Failed -> {
                                _snackBarMessage.value = UiText.StringResource(
                                    R.string.prayertimes_refresh_failed,
                                )
                            }

                            else -> Unit
                        }
                    }
                }
        }

        viewModelScope.launch {
            combine(
                repository.observeMode(),
                repository.observeCurrentSelection(),
            ) { mode, selection ->
                mode to selection
            }
                .distinctUntilChanged()
                .collect { (mode, selection) ->
                    if (mode == PrayerTimesMode.AUTO && selection == null && !resolvingLocationState.value) {
                        resolveByDeviceLocation(openPickerOnFailure = false)
                    }
                }
        }
    }

    private val coreUiInputs = combine(
        repository.observeMode(),
        repository.observeCurrentSelection(),
        repository.observeAlarmSettings(),
        resolvingLocationState,
        pendingResolvedLocationNameState,
    ) { mode, selection, alarmSettings, isResolvingLocation, pendingResolvedLocationName ->
        CoreUiInputs(
            mode = mode,
            selection = selection,
            alarmSettings = alarmSettings,
            isResolvingLocation = isResolvingLocation,
            pendingResolvedLocationName = pendingResolvedLocationName,
        )
    }

    val uiState: StateFlow<PrayerTimesUiState> = combine(
        coreUiInputs,
        refreshingState,
        currentTimeMillis,
    ) { core, isRefreshing, nowMillis ->
        UiInputs(
            mode = core.mode,
            selection = core.selection,
            alarmSettings = core.alarmSettings,
            isResolvingLocation = core.isResolvingLocation,
            pendingResolvedLocationName = core.pendingResolvedLocationName,
            isRefreshing = isRefreshing,
            nowMillis = nowMillis,
        )
    }
        .flatMapLatest { inputs ->
            val timesFlow: Flow<List<PrayerTimesDay>> = if (inputs.selection == null) {
                MutableStateFlow(emptyList())
            } else {
                repository.observeTodayAndUpcoming(inputs.selection.districtId)
            }

            timesFlow.map { days ->
                val nextPrayer = PrayerDateTime.findNextPrayer(
                    days = days,
                    nowMillis = inputs.nowMillis,
                )
                val nextCountdown = nextPrayer?.let {
                    PrayerDateTime.formatCountdown(it.triggerMillis - inputs.nowMillis)
                }.orEmpty()

                PrayerTimesUiState(
                    mode = inputs.mode,
                    selection = inputs.selection,
                    days = days,
                    alarmSettings = inputs.alarmSettings,
                    isResolvingLocation = inputs.isResolvingLocation,
                    pendingResolvedLocationName = inputs.pendingResolvedLocationName,
                    isRefreshing = inputs.isRefreshing,
                    currentTimeMillis = inputs.nowMillis,
                    nextPrayer = nextPrayer?.asUi(),
                    nextPrayerCountdown = nextCountdown,
                    hijriDateText = HijriCalendarConverter.todayHijriString(),
                    gregorianDateText = gregorianTodayString(),
                    ramadanProgress = buildRamadanProgress(),
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PrayerTimesUiState(),
        )

    fun onModeChanged(mode: PrayerTimesMode) {
        viewModelScope.launch {
            repository.setMode(mode)
            if (mode == PrayerTimesMode.MANUAL) {
                pendingResolvedLocationNameState.value = null
            }
            if (mode == PrayerTimesMode.AUTO) {
                resolveByDeviceLocation(openPickerOnFailure = false)
            }
        }
    }

    fun onAlarmEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAlarmEnabled(enabled)
        }
    }

    fun onAlarmOffsetChanged(minutes: Int) {
        viewModelScope.launch {
            repository.setAlarmOffsetMinutes(minutes)
        }
    }

    fun onAlarmPrayerKeysChanged(keys: Set<String>) {
        viewModelScope.launch {
            repository.setSelectedAlarmPrayerKeys(keys)
        }
    }

    fun onAlarmSoundUriChanged(uri: String?) {
        viewModelScope.launch {
            prayerPreferencesDataSource.setAlarmSoundUri(uri)
        }
    }

    fun onTestAlarmSound(uri: String?) {
        prayerAlarmSoundPlayer.playPreview(uri)
    }

    fun refresh() {
        viewModelScope.launch {
            refreshingState.value = true
            try {
                val districtId = uiState.value.selection?.districtId ?: return@launch
                when (repository.refreshIfNeeded(districtId, force = true)) {
                    RefreshResult.InvalidSelection -> {
                        _snackBarMessage.value = UiText.StringResource(
                            R.string.prayertimes_selection_refresh_needed,
                        )
                    }

                    is RefreshResult.Failed -> {
                        _snackBarMessage.value = UiText.StringResource(
                            R.string.prayertimes_refresh_failed,
                        )
                    }

                    else -> Unit
                }
            } finally {
                refreshingState.value = false
            }
        }
    }

    fun resolveByDeviceLocation() {
        resolveByDeviceLocation(openPickerOnFailure = true)
    }

    private fun resolveByDeviceLocation(openPickerOnFailure: Boolean) {
        viewModelScope.launch {
            val previousDistrictId = repository.observeCurrentSelection().first()?.districtId
            resolvingLocationState.value = true
            try {
                when (val result = repository.resolveAndSelectByDeviceLocation()) {
                    ResolveResult.PermissionDenied -> {
                        _snackBarMessage.value = UiText.StringResource(
                            R.string.prayertimes_permission_needed,
                        )
                        if (openPickerOnFailure) {
                            _events.tryEmit(PrayerTimesUiEvent.OpenLocationPicker)
                        }
                    }

                    ResolveResult.LocationUnavailable,
                    ResolveResult.NoMatch,
                    is ResolveResult.Failed,
                    -> {
                        _snackBarMessage.value = UiText.StringResource(
                            R.string.prayertimes_resolve_failed,
                        )
                        if (openPickerOnFailure) {
                            _events.tryEmit(PrayerTimesUiEvent.OpenLocationPicker)
                        }
                    }

                    is ResolveResult.Success -> {
                        _snackBarMessage.value = UiText.StringResource(
                            R.string.prayertimes_location_found,
                            listOf(result.selection.displayName),
                        )
                        if (result.selection.districtId != previousDistrictId) {
                            pendingResolvedLocationNameState.value = result.selection.displayName
                        }
                        repository.refreshIfNeeded(result.selection.districtId, force = false)
                    }
                }
            } finally {
                resolvingLocationState.value = false
            }
        }
    }

    fun confirmResolvedLocationSuggestion() {
        pendingResolvedLocationNameState.value = null
    }

    fun rejectResolvedLocationSuggestion() {
        pendingResolvedLocationNameState.value = null
        viewModelScope.launch {
            _events.emit(PrayerTimesUiEvent.OpenLocationPicker)
        }
    }

    fun clearSnackMessage() {
        _snackBarMessage.value = null
    }

    private val gregorianFormatter = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
    private var cachedGregorianDate = ""
    private var cachedGregorianDay = -1

    private fun gregorianTodayString(): String {
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        if (today != cachedGregorianDay) {
            cachedGregorianDate = gregorianFormatter.format(System.currentTimeMillis())
            cachedGregorianDay = today
        }
        return cachedGregorianDate
    }

    private fun buildRamadanProgress(): RamadanProgressUi? {
        val now = java.util.Calendar.getInstance()
        val (year, month, day) = HijriCalendarConverter.toHijri(
            now.get(java.util.Calendar.YEAR),
            now.get(java.util.Calendar.MONTH) + 1,
            now.get(java.util.Calendar.DAY_OF_MONTH),
        )
        return if (month == 9) {
            RamadanProgressUi(
                hijriYear = year,
                day = day.coerceIn(1, 30),
                totalDays = 30,
            )
        } else {
            null
        }
    }
}

data class PrayerTimesUiState(
    val mode: PrayerTimesMode = PrayerTimesMode.AUTO,
    val selection: PrayerLocationSelection? = null,
    val days: List<PrayerTimesDay> = emptyList(),
    val alarmSettings: PrayerAlarmSettings = PrayerAlarmSettings(),
    val isResolvingLocation: Boolean = false,
    val pendingResolvedLocationName: String? = null,
    val isRefreshing: Boolean = false,
    val currentTimeMillis: Long = System.currentTimeMillis(),
    val nextPrayer: NextPrayerUi? = null,
    val nextPrayerCountdown: String = "",
    val hijriDateText: String = "",
    val gregorianDateText: String = "",
    val ramadanProgress: RamadanProgressUi? = null,
)

data class NextPrayerUi(
    val prayerKey: String,
    val timeHm: String,
)

data class RamadanProgressUi(
    val hijriYear: Int,
    val day: Int,
    val totalDays: Int,
)

sealed interface PrayerTimesUiEvent {
    data object OpenLocationPicker : PrayerTimesUiEvent
}

internal fun NextPrayerInfo.asUi(): NextPrayerUi {
    return NextPrayerUi(
        prayerKey = prayerKey,
        timeHm = timeHm,
    )
}

@HiltViewModel
class PrayerLocationPickerViewModel @Inject constructor(
    private val repository: PrayerTimesRepository,
) : ViewModel() {

    private val pickerBaseState = MutableStateFlow(PrayerLocationPickerUiState())
    private val pickerSearchQuery = MutableStateFlow("")

    val uiState: StateFlow<PrayerLocationPickerUiState> = combine(
        pickerBaseState,
        pickerSearchQuery,
    ) { state, query ->
        val normalized = query.trim()
        val filteredCountries = if (normalized.isBlank()) {
            state.countries
        } else {
            state.countries.filter { it.nameTr.contains(normalized, ignoreCase = true) }
        }
        val filteredCities = if (normalized.isBlank()) {
            state.cities
        } else {
            state.cities.filter { it.nameTr.contains(normalized, ignoreCase = true) }
        }
        val filteredDistricts = if (normalized.isBlank()) {
            state.districts
        } else {
            state.districts.filter { it.nameTr.contains(normalized, ignoreCase = true) }
        }
        state.copy(
            searchQuery = query,
            filteredCountries = filteredCountries,
            filteredCities = filteredCities,
            filteredDistricts = filteredDistricts,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PrayerLocationPickerUiState(),
    )

    init {
        viewModelScope.launch {
            loadInitialState()
        }
    }

    fun onSearchQueryChanged(query: String) {
        pickerSearchQuery.value = query
    }

    private suspend fun loadInitialState() {
        pickerBaseState.value = pickerBaseState.value.copy(isLoading = true)
        val countries = repository.getCountries(forceRefresh = false)
        val currentSelection = repository.observeCurrentSelection().first()
        val suggestion: PrayerLocationSuggestion? = if (currentSelection == null) {
            repository.suggestManualSelectionByDeviceLocation()
        } else {
            null
        }

        val selectedCountryId = currentSelection?.countryId ?: suggestion?.countryId
        val selectedCountry = countries.firstOrNull { it.id == selectedCountryId }
        if (selectedCountry == null) {
            pickerBaseState.value = pickerBaseState.value.copy(
                countries = countries,
                filteredCountries = countries,
                step = LocationStep.COUNTRY,
                isLoading = false,
            )
            return
        }

        val cities = repository.getCities(selectedCountry.id, forceRefresh = false)
        val selectedCityId = currentSelection?.cityId ?: suggestion?.cityId
        val selectedCity = cities.firstOrNull { it.id == selectedCityId }
        if (selectedCity == null) {
            pickerBaseState.value = pickerBaseState.value.copy(
                countries = countries,
                selectedCountry = selectedCountry,
                cities = cities,
                filteredCountries = countries,
                filteredCities = cities,
                step = LocationStep.CITY,
                isLoading = false,
            )
            return
        }

        val districts = repository.getDistricts(selectedCity.id, forceRefresh = false)
        val selectedDistrict = currentSelection?.let { selection ->
            districts.firstOrNull { it.id == selection.districtId }
        }
        pickerBaseState.value = pickerBaseState.value.copy(
            countries = countries,
            selectedCountry = selectedCountry,
            cities = cities,
            selectedCity = selectedCity,
            districts = districts,
            selectedDistrict = selectedDistrict,
            filteredCountries = countries,
            filteredCities = cities,
            filteredDistricts = districts,
            step = if (selectedDistrict != null) LocationStep.DISTRICT else LocationStep.CITY,
            isLoading = false,
        )
    }

    fun onCountrySelected(country: PrayerCountry) {
        viewModelScope.launch {
            pickerBaseState.value = pickerBaseState.value.copy(isLoading = true)
            val cities = repository.getCities(country.id, forceRefresh = false)
            pickerSearchQuery.value = ""
            pickerBaseState.value = pickerBaseState.value.copy(
                selectedCountry = country,
                selectedCity = null,
                selectedDistrict = null,
                cities = cities,
                districts = emptyList(),
                filteredCities = cities,
                filteredDistricts = emptyList(),
                step = LocationStep.CITY,
                isLoading = false,
            )
        }
    }

    fun onCitySelected(city: PrayerCity) {
        viewModelScope.launch {
            pickerBaseState.value = pickerBaseState.value.copy(isLoading = true)
            val districts = repository.getDistricts(city.id, forceRefresh = false)
            pickerSearchQuery.value = ""
            pickerBaseState.value = pickerBaseState.value.copy(
                selectedCity = city,
                selectedDistrict = null,
                districts = districts,
                filteredDistricts = districts,
                step = LocationStep.DISTRICT,
                isLoading = false,
            )
        }
    }

    fun onDistrictSelected(district: PrayerDistrict) {
        pickerBaseState.value = pickerBaseState.value.copy(selectedDistrict = district)
    }

    fun onStepSelected(step: LocationStep) {
        val state = pickerBaseState.value
        when {
            step == LocationStep.COUNTRY -> {
                pickerBaseState.value = state.copy(step = LocationStep.COUNTRY)
            }
            step == LocationStep.CITY && state.selectedCountry != null -> {
                pickerBaseState.value = state.copy(step = LocationStep.CITY)
            }
            step == LocationStep.DISTRICT && state.selectedCity != null -> {
                pickerBaseState.value = state.copy(step = LocationStep.DISTRICT)
            }
        }
    }

    fun saveSelection(onSaved: () -> Unit) {
        viewModelScope.launch {
            val state = pickerBaseState.value
            val country = state.selectedCountry ?: return@launch
            val city = state.selectedCity ?: return@launch
            val district = state.selectedDistrict ?: return@launch

            runCatching {
                repository.setManualSelection(
                    countryId = country.id,
                    cityId = city.id,
                    districtId = district.id,
                )
            }.onSuccess {
                onSaved()
            }.onFailure {
                Timber.w(it, "Failed to save prayer location selection")
            }
        }
    }
}

enum class LocationStep {
    COUNTRY,
    CITY,
    DISTRICT,
}

data class PrayerLocationPickerUiState(
    val countries: List<PrayerCountry> = emptyList(),
    val cities: List<PrayerCity> = emptyList(),
    val districts: List<PrayerDistrict> = emptyList(),
    val filteredCountries: List<PrayerCountry> = emptyList(),
    val filteredCities: List<PrayerCity> = emptyList(),
    val filteredDistricts: List<PrayerDistrict> = emptyList(),
    val selectedCountry: PrayerCountry? = null,
    val selectedCity: PrayerCity? = null,
    val selectedDistrict: PrayerDistrict? = null,
    val step: LocationStep = LocationStep.COUNTRY,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
)

private data class UiInputs(
    val mode: PrayerTimesMode,
    val selection: PrayerLocationSelection?,
    val alarmSettings: PrayerAlarmSettings,
    val isResolvingLocation: Boolean,
    val pendingResolvedLocationName: String?,
    val isRefreshing: Boolean,
    val nowMillis: Long,
)

private data class CoreUiInputs(
    val mode: PrayerTimesMode,
    val selection: PrayerLocationSelection?,
    val alarmSettings: PrayerAlarmSettings,
    val isResolvingLocation: Boolean,
    val pendingResolvedLocationName: String?,
)
