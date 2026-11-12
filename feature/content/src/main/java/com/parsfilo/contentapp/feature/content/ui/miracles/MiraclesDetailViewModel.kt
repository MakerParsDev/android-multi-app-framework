package com.parsfilo.contentapp.feature.content.ui.miracles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.model.MiraclesPrayer
import com.parsfilo.contentapp.feature.ads.AdGateChecker
import com.parsfilo.contentapp.feature.content.data.MiraclesPrayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MiraclesDetailUiState {
    data object Loading : MiraclesDetailUiState

    data class Success(
        val prayer: MiraclesPrayer,
        val shouldShowAds: Boolean,
        val fontSize: Int = 20,
    ) : MiraclesDetailUiState

    data class Error(val throwable: Throwable) : MiraclesDetailUiState
}

@HiltViewModel
class MiraclesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val prayerRepository: MiraclesPrayerRepository,
    private val adGateChecker: AdGateChecker,
    private val preferencesDataSource: PreferencesDataSource,
) : ViewModel() {

    private val prayerIndex: Int = checkNotNull(savedStateHandle["prayerIndex"])

    private val _uiState = MutableStateFlow<MiraclesDetailUiState>(MiraclesDetailUiState.Loading)
    val uiState: StateFlow<MiraclesDetailUiState> = _uiState.asStateFlow()

    init {
        loadPrayerDetail()
    }

    fun updateFontSize(fontSize: Int) {
        viewModelScope.launch {
            preferencesDataSource.setFontSize(fontSize.coerceIn(14, 40))
        }
    }

    private fun loadPrayerDetail() {
        viewModelScope.launch {
            val prayer = runCatching { prayerRepository.getPrayerByIndex(prayerIndex) }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = MiraclesDetailUiState.Error(error)
                    return@launch
                }

            if (prayer == null) {
                _uiState.value = MiraclesDetailUiState.Error(IllegalStateException("Dua bulunamadı"))
                return@launch
            }

            combine(
                adGateChecker.shouldShowAds,
                preferencesDataSource.userData,
            ) { shouldShowAds, userData ->
                MiraclesDetailUiState.Success(
                    prayer = prayer,
                    shouldShowAds = shouldShowAds,
                    fontSize = userData.fontSize,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
