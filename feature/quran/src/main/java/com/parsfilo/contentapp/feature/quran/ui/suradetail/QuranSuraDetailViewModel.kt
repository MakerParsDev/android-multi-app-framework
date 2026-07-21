package com.parsfilo.contentapp.feature.quran.ui.suradetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.common.result.Result
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.model.QuranAyah
import com.parsfilo.contentapp.core.model.QuranDisplayMode
import com.parsfilo.contentapp.core.model.QuranSura
import com.parsfilo.contentapp.feature.ads.AdGateChecker
import com.parsfilo.contentapp.feature.quran.config.QuranApiConfig
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
import javax.inject.Named

data class AyahDisplayState(
    val ayah: QuranAyah,
    val isBookmarked: Boolean,
    val isAudioDownloaded: Boolean,
    val isAudioDownloading: Boolean,
    val audioDownloadProgress: Float,
    val isCurrentlyPlaying: Boolean,
)

data class QuranSuraDetailUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val sura: QuranSura? = null,
    val ayahs: List<AyahDisplayState> = emptyList(),
    val displayMode: QuranDisplayMode = QuranDisplayMode.TRANSLATION,
    val fontSize: Int = 28,
    val currentReciter: QuranApiConfig.Reciters.ReciterInfo = QuranApiConfig.Reciters.DEFAULT,
    val availableReciters: List<QuranApiConfig.Reciters.ReciterInfo> = QuranApiConfig.Reciters.ALL,
    val currentPlayingAyah: Int? = null,
    val isPlaying: Boolean = false,
    val shouldShowAds: Boolean = false,
)

private data class DetailContent(
    val sura: QuranSura?,
    val ayahs: List<QuranAyah>,
    val bookmarkedAyahs: Set<Int>,
)

private data class DetailPrefs(
    val mode: QuranDisplayMode,
    val fontSize: Int,
)

private data class DetailPlayback(
    val downloading: Map<Int, Float>,
    val downloaded: Set<Int>,
    val currentAyah: Int?,
    val isPlaying: Boolean,
)

@HiltViewModel
class QuranSuraDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: QuranRepository,
    private val preferencesDataSource: PreferencesDataSource,
    private val adGateChecker: AdGateChecker,
    private val allReciters: List<QuranApiConfig.Reciters.ReciterInfo>,
    @Named("defaultReciterId") defaultReciterId: String,
) : ViewModel() {

    private val suraNumber: Int = checkNotNull(savedStateHandle["suraNumber"])

    private val localState = MutableStateFlow(
        QuranSuraDetailUiState(
            availableReciters = allReciters,
            currentReciter = allReciters.firstOrNull { it.id == defaultReciterId } ?: QuranApiConfig.Reciters.DEFAULT,
        )
    )
    private val downloadingAyahs = MutableStateFlow<Map<Int, Float>>(emptyMap())
    private val downloadedAyahs = MutableStateFlow<Set<Int>>(emptySet())
    private val playingAyah = MutableStateFlow<Int?>(null)
    private val isPlaying = MutableStateFlow(false)

    private val contentFlow = combine(
        repository.observeSura(suraNumber),
        repository.observeAyahsForSura(suraNumber),
        repository.observeBookmarks(),
    ) { sura, ayahs, bookmarks ->
        DetailContent(
            sura = sura,
            ayahs = ayahs,
            bookmarkedAyahs = bookmarks
                .filter { it.suraNumber == suraNumber }
                .map { it.ayahNumber }
                .toSet(),
        )
    }

    private val prefsFlow = combine(
        preferencesDataSource.quranDisplayMode,
        preferencesDataSource.quranFontSize,
    ) { modeRaw, fontSize ->
        DetailPrefs(
            mode = runCatching { QuranDisplayMode.valueOf(modeRaw) }
                .getOrDefault(QuranDisplayMode.TRANSLATION),
            fontSize = fontSize,
        )
    }

    private val playbackFlow = combine(
        downloadingAyahs,
        downloadedAyahs,
        playingAyah,
        isPlaying,
    ) { downloading, downloaded, currentAyah, currentlyPlaying ->
        DetailPlayback(
            downloading = downloading,
            downloaded = downloaded,
            currentAyah = currentAyah,
            isPlaying = currentlyPlaying,
        )
    }

    val uiState: StateFlow<QuranSuraDetailUiState> = combine(
        localState,
        contentFlow,
        prefsFlow,
        playbackFlow,
        adGateChecker.shouldShowAds,
    ) { base, content, prefs, playback, shouldShowAds ->
        base.copy(
            isLoading = false,
            sura = content.sura,
            ayahs = content.ayahs.map { ayah ->
                AyahDisplayState(
                    ayah = ayah,
                    isBookmarked = ayah.ayahNumber in content.bookmarkedAyahs,
                    isAudioDownloaded = ayah.ayahNumber in playback.downloaded,
                    isAudioDownloading = ayah.ayahNumber in playback.downloading,
                    audioDownloadProgress = playback.downloading[ayah.ayahNumber] ?: 0f,
                    isCurrentlyPlaying = playback.isPlaying && playback.currentAyah == ayah.ayahNumber,
                )
            },
            displayMode = prefs.mode,
            fontSize = prefs.fontSize,
            currentPlayingAyah = playback.currentAyah,
            isPlaying = playback.isPlaying,
            shouldShowAds = shouldShowAds,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = localState.value,
    )

    init {
        syncIfNeeded()
        observePersistedReciter()
    }

    fun retrySync() {
        syncIfNeeded(force = true)
    }

    fun onDisplayModeSelected(mode: QuranDisplayMode) {
        viewModelScope.launch {
            preferencesDataSource.setQuranDisplayMode(mode.name)
        }
    }

    fun onFontSizeChange(size: Int) {
        viewModelScope.launch {
            preferencesDataSource.setQuranFontSize(size.coerceIn(22, 42))
        }
    }

    fun onReciterSelected(reciterId: String) {
        val reciter = allReciters.firstOrNull { it.id == reciterId } ?: return
        localState.update { it.copy(currentReciter = reciter) }
        viewModelScope.launch {
            preferencesDataSource.setQuranReciter(reciterId)
            refreshDownloadedAyahs(reciterId)
        }
    }

    fun playAyah(ayahNumber: Int, onPlayUrlReady: (String) -> Unit) {
        val reciter = localState.value.currentReciter
        viewModelScope.launch {
            val path = repository.getAyahAudioPath(
                reciterId = reciter.id,
                reciterFolder = reciter.folderName,
                suraNumber = suraNumber,
                ayahNumber = ayahNumber,
            )
            playingAyah.value = ayahNumber
            isPlaying.value = true
            onPlayUrlReady(path)
        }
    }

    fun pauseAudio() {
        isPlaying.value = false
    }

    fun onPlaybackCompleted() {
        isPlaying.value = false
        playingAyah.value = null
    }

    fun downloadAyah(ayahNumber: Int) {
        val reciter = localState.value.currentReciter
        if (downloadingAyahs.value.containsKey(ayahNumber)) return

        viewModelScope.launch {
            downloadingAyahs.update { it + (ayahNumber to 0f) }
            val result = repository.downloadAyahAudio(
                reciterId = reciter.id,
                reciterFolder = reciter.folderName,
                suraNumber = suraNumber,
                ayahNumber = ayahNumber,
                onProgress = { progress ->
                    downloadingAyahs.update { it + (ayahNumber to progress) }
                },
            )
            downloadingAyahs.update { it - ayahNumber }
            if (result is Result.Error) {
                localState.update { it.copy(errorMessage = result.exception.message) }
            }
            refreshDownloadedAyahs(reciter.id)
        }
    }

    fun toggleBookmark(ayahNumber: Int) {
        viewModelScope.launch {
            repository.toggleBookmark(suraNumber = suraNumber, ayahNumber = ayahNumber)
        }
    }

    fun onAyahVisible(ayahNumber: Int) {
        viewModelScope.launch {
            repository.saveLastRead(suraNumber = suraNumber, ayahNumber = ayahNumber)
        }
    }

    private fun syncIfNeeded(force: Boolean = false) {
        viewModelScope.launch {
            localState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = repository.syncSurasIfNeeded()) {
                is Result.Error -> {
                    localState.update { it.copy(isLoading = false, errorMessage = result.exception.message) }
                    return@launch
                }
                else -> Unit
            }

            when (val result = repository.syncSuraContentIfNeeded(suraNumber = suraNumber, force = force)) {
                is Result.Success -> {
                    refreshDownloadedAyahs(localState.value.currentReciter.id)
                    localState.update { it.copy(isLoading = false, errorMessage = null) }
                }
                is Result.Error -> {
                    localState.update { it.copy(isLoading = false, errorMessage = result.exception.message) }
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun observePersistedReciter() {
        viewModelScope.launch {
            preferencesDataSource.quranSelectedReciter.collect { reciterId ->
                val reciter = allReciters.firstOrNull { it.id == reciterId } ?: return@collect
                localState.update { it.copy(currentReciter = reciter) }
                refreshDownloadedAyahs(reciter.id)
            }
        }
    }

    private suspend fun refreshDownloadedAyahs(reciterId: String) {
        downloadedAyahs.value = repository.getDownloadedAyahNumbers(reciterId, suraNumber)
    }
}
