package com.parsfilo.contentapp.feature.content.ui.miracles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.model.MiraclesPrayer
import com.parsfilo.contentapp.feature.ads.AdGateChecker
import com.parsfilo.contentapp.feature.content.data.MiraclesPrayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MiraclesListUiState {
    data object Loading : MiraclesListUiState

    data class Success(
        val prayers: List<MiraclesPrayer>,
        val shouldShowAds: Boolean
    ) : MiraclesListUiState

    data class Error(val throwable: Throwable) : MiraclesListUiState
}

@HiltViewModel
class MiraclesListViewModel @Inject constructor(
    private val prayerRepository: MiraclesPrayerRepository,
    private val adGateChecker: AdGateChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow<MiraclesListUiState>(MiraclesListUiState.Loading)
    val uiState: StateFlow<MiraclesListUiState> = _uiState.asStateFlow()

    init {
        loadPrayers()
    }

    private fun loadPrayers() {
        viewModelScope.launch {
            val prayers = runCatching { prayerRepository.getPrayers() }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = MiraclesListUiState.Error(error)
                }
                .getOrNull()
                ?: return@launch

            adGateChecker.shouldShowAds.collect { shouldShowAds ->
                _uiState.value = MiraclesListUiState.Success(
                    prayers = prayers,
                    shouldShowAds = shouldShowAds
                )
            }
        }
    }
}
