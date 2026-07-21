package com.parsfilo.contentapp.feature.content.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.common.result.Result
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import com.parsfilo.contentapp.core.firebase.logDisplayModeChanged
import com.parsfilo.contentapp.core.model.DisplayMode
import com.parsfilo.contentapp.core.model.Verse
import com.parsfilo.contentapp.feature.ads.AdGateChecker
import com.parsfilo.contentapp.feature.content.data.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContentViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val preferencesDataSource: PreferencesDataSource,
    private val adGateChecker: AdGateChecker,
    private val appAnalytics: AppAnalytics,
) : ViewModel() {
    private var defaultModeApplied = false
    private val reloadSignal = MutableStateFlow(0)

    private val versesFlow = reloadSignal.map {
        when (val result = contentRepository.getVerses()) {
            is Result.Success -> result.data
            is Result.Error -> throw result.exception
            is Result.Loading -> emptyList()
        }
    }

    val uiState: StateFlow<ContentUiState> = combine(
        versesFlow,
        preferencesDataSource.userData,
        adGateChecker.shouldShowAds
    ) { verses, userData, shouldShowAds ->
        val mode = try {
            DisplayMode.valueOf(userData.displayMode)
        } catch (_: Exception) {
            DisplayMode.ARABIC
        }

        ContentUiState.Success(
            verses = verses,
            displayMode = mode,
            fontSize = userData.fontSize,
            shouldShowAds = shouldShowAds
        ) as ContentUiState
    }
        .catch { emit(ContentUiState.Error(it)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ContentUiState.Loading
        )

    fun reload() {
        reloadSignal.update { it + 1 }
    }

    fun updateDisplayMode(mode: DisplayMode) {
        val oldMode = (uiState.value as? ContentUiState.Success)?.displayMode
        viewModelScope.launch {
            if (oldMode != null && oldMode != mode) {
                appAnalytics.logDisplayModeChanged(
                    oldMode = oldMode.name.lowercase(),
                    newMode = mode.name.lowercase(),
                )
            }
            preferencesDataSource.setDisplayMode(mode.name)
        }
    }

    fun ensureArabicDefault() {
        if (defaultModeApplied) return
        defaultModeApplied = true

        viewModelScope.launch {
            preferencesDataSource.setDisplayMode(DisplayMode.ARABIC.name)
        }
    }
}

sealed interface ContentUiState {
    data object Loading : ContentUiState

    data class Success(
        val verses: List<Verse>,
        val displayMode: DisplayMode,
        val fontSize: Int,
        val shouldShowAds: Boolean = true
    ) : ContentUiState

    data class Error(val throwable: Throwable) : ContentUiState
}
