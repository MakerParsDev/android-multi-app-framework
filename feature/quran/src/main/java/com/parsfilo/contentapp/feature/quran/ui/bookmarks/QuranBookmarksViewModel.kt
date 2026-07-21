package com.parsfilo.contentapp.feature.quran.ui.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.model.QuranBookmark
import com.parsfilo.contentapp.feature.ads.AdGateChecker
import com.parsfilo.contentapp.feature.quran.data.QuranRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuranBookmarkItem(
    val bookmark: QuranBookmark,
    val suraName: String,
)

data class QuranBookmarksUiState(
    val items: List<QuranBookmarkItem> = emptyList(),
    val shouldShowAds: Boolean = false,
)

@HiltViewModel
class QuranBookmarksViewModel @Inject constructor(
    private val repository: QuranRepository,
    private val adGateChecker: AdGateChecker,
) : ViewModel() {

    val uiState: StateFlow<QuranBookmarksUiState> = combine(
        repository.observeBookmarks(),
        repository.observeSuras(),
        adGateChecker.shouldShowAds,
    ) { bookmarks, suras, shouldShowAds ->
        val suraMap = suras.associateBy { it.number }
        QuranBookmarksUiState(
            items = bookmarks.map { bookmark ->
                QuranBookmarkItem(
                    bookmark = bookmark,
                    suraName = suraMap[bookmark.suraNumber]?.nameTurkish
                        ?: suraMap[bookmark.suraNumber]?.nameLatin
                        ?: bookmark.suraNumber.toString(),
                )
            },
            shouldShowAds = shouldShowAds,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = QuranBookmarksUiState(),
    )

    fun removeBookmark(suraNumber: Int, ayahNumber: Int) {
        viewModelScope.launch {
            if (repository.isBookmarked(suraNumber, ayahNumber)) {
                repository.toggleBookmark(suraNumber, ayahNumber)
            }
        }
    }
}
