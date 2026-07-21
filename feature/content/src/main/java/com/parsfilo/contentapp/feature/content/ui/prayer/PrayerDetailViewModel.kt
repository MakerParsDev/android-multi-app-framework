package com.parsfilo.contentapp.feature.content.ui.prayer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.model.DisplayMode
import com.parsfilo.contentapp.core.model.Prayer
import com.parsfilo.contentapp.feature.ads.AdGateChecker
import com.parsfilo.contentapp.feature.content.data.PrayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PrayerDetailUiState {
    data object Loading : PrayerDetailUiState

    data class Success(
        val prayer: Prayer,
        val displayMode: DisplayMode,
        val fontSize: Int,
        val shouldShowAds: Boolean
    ) : PrayerDetailUiState

    data class Error(val throwable: Throwable) : PrayerDetailUiState
}

@HiltViewModel
class PrayerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val prayerRepository: PrayerRepository,
    private val preferencesDataSource: PreferencesDataSource,
    private val adGateChecker: AdGateChecker
) : ViewModel() {

    private val prayerId: Int = checkNotNull(savedStateHandle["prayerId"])

    private val _uiState = MutableStateFlow<PrayerDetailUiState>(PrayerDetailUiState.Loading)
    val uiState: StateFlow<PrayerDetailUiState> = _uiState.asStateFlow()

    init {
        loadPrayerDetail()
    }

    private fun loadPrayerDetail() {
        viewModelScope.launch {
            val prayer = runCatching { prayerRepository.getPrayerById(prayerId) }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = PrayerDetailUiState.Error(error)
                    return@launch
                }

            if (prayer == null) {
                _uiState.value = PrayerDetailUiState.Error(IllegalStateException("Namaz/Dua bulunamadı"))
                return@launch
            }

            combine(
                preferencesDataSource.userData,
                adGateChecker.shouldShowAds
            ) { userData, shouldShowAds ->
                val displayMode = runCatching { DisplayMode.valueOf(userData.displayMode) }
                    .getOrDefault(DisplayMode.ARABIC)

                PrayerDetailUiState.Success(
                    prayer = prayer,
                    displayMode = displayMode,
                    fontSize = userData.fontSize,
                    shouldShowAds = shouldShowAds
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun updateDisplayMode(mode: DisplayMode) {
        viewModelScope.launch {
            preferencesDataSource.setDisplayMode(mode.name)
        }
    }
}
