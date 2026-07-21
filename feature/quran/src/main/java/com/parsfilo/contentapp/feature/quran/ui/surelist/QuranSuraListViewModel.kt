package com.parsfilo.contentapp.feature.quran.ui.surelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.common.result.Result
import com.parsfilo.contentapp.core.database.model.quran.QuranLastReadEntity
import com.parsfilo.contentapp.core.model.QuranSura
import com.parsfilo.contentapp.feature.ads.AdGateChecker
import com.parsfilo.contentapp.feature.quran.data.QuranRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SuraListViewModelState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val suras: List<QuranSura> = emptyList(),
    val query: String = "",
    val lastRead: QuranLastReadEntity? = null,
    val shouldShowAds: Boolean = false,
) {
    val filteredSuras: List<QuranSura>
        get() {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isBlank()) return suras
            return suras.filter { sura ->
                sura.number.toString() == normalizedQuery ||
                    sura.nameLatin.contains(normalizedQuery, ignoreCase = true) ||
                    sura.nameTurkish.contains(normalizedQuery, ignoreCase = true) ||
                    sura.nameArabic.contains(normalizedQuery)
            }
        }
}

@HiltViewModel
class QuranSuraListViewModel @Inject constructor(
    private val repository: QuranRepository,
    private val adGateChecker: AdGateChecker,
) : ViewModel() {

    private val mutableState = MutableStateFlow(SuraListViewModelState())

    val uiState: StateFlow<SuraListViewModelState> = combine(
        mutableState,
        repository.observeSuras(),
        repository.observeLastRead(),
        adGateChecker.shouldShowAds,
    ) { state, suras, lastRead, shouldShowAds ->
        state.copy(
            isLoading = false,
            suras = suras,
            lastRead = lastRead,
            shouldShowAds = shouldShowAds,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SuraListViewModelState(),
    )

    init {
        syncIfNeeded()
    }

    fun onSearchQueryChange(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    fun retrySync() {
        syncIfNeeded(force = true)
    }

    private fun syncIfNeeded(force: Boolean = false) {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.syncSurasIfNeeded(force = force)) {
                is Result.Success -> mutableState.update { it.copy(isLoading = false, errorMessage = null) }
                is Result.Error -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exception.message,
                    )
                }
                Result.Loading -> Unit
            }
        }
    }
}
