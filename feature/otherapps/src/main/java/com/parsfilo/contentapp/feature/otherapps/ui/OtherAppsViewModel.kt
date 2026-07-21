package com.parsfilo.contentapp.feature.otherapps.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.feature.otherapps.data.OtherAppsRepository
import com.parsfilo.contentapp.feature.otherapps.model.OtherApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OtherAppsViewModel @Inject constructor(
    private val repository: OtherAppsRepository
) : ViewModel() {

    private val loadingState = MutableStateFlow(true)
    private val errorMessageState = MutableStateFlow<String?>(null)

    val uiState: StateFlow<OtherAppsUiState> = combine(
        repository.apps,
        loadingState.asStateFlow(),
        errorMessageState.asStateFlow()
    ) { apps, isLoading, errorMessage ->
        OtherAppsUiState(
            apps = apps,
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OtherAppsUiState()
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loadingState.value = true
            errorMessageState.value = null
            runCatching { repository.refreshIfNeeded() }
                .onFailure { throwable ->
                    errorMessageState.value = throwable.localizedMessage
                }
            loadingState.value = false
        }
    }
}

data class OtherAppsUiState(
    val apps: List<OtherApp> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
