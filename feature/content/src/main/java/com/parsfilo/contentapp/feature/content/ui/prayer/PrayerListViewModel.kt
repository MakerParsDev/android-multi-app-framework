package com.parsfilo.contentapp.feature.content.ui.prayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.model.Prayer
import com.parsfilo.contentapp.feature.ads.AdGateChecker
import com.parsfilo.contentapp.feature.content.data.PrayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PrayerListUiState {
    data object Loading : PrayerListUiState

    data class Success(
        val prayers: List<Prayer>,
        val shouldShowAds: Boolean
    ) : PrayerListUiState

    data class Error(val throwable: Throwable) : PrayerListUiState
}

@HiltViewModel
class PrayerListViewModel @Inject constructor(
    private val prayerRepository: PrayerRepository,
    private val adGateChecker: AdGateChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow<PrayerListUiState>(PrayerListUiState.Loading)
    val uiState: StateFlow<PrayerListUiState> = _uiState.asStateFlow()

    init {
        loadPrayers()
    }

    private fun loadPrayers() {
        viewModelScope.launch {
            val prayers = runCatching { prayerRepository.getPrayers() }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = PrayerListUiState.Error(error)
                }
                .getOrNull()
                ?: return@launch

            adGateChecker.shouldShowAds.collect { shouldShowAds ->
                _uiState.value = PrayerListUiState.Success(
                    prayers = prayers,
                    shouldShowAds = shouldShowAds
                )
            }
        }
    }
}
