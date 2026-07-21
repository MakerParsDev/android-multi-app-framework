package com.parsfilo.contentapp.feature.quran.ui.suradetail

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.core.model.QuranAyah
import com.parsfilo.contentapp.core.model.QuranDisplayMode
import com.parsfilo.contentapp.feature.quran.R
import com.parsfilo.contentapp.feature.quran.ui.component.AyahItem
import com.parsfilo.contentapp.feature.quran.ui.component.DisplayModeSelector
import com.parsfilo.contentapp.feature.quran.ui.component.ReciterSelectionSheet
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun QuranSuraDetailRoute(
    onBack: () -> Unit,
    onBookmarksClick: () -> Unit = {},
    onPlayAudioUrl: (String) -> Unit,
    onPauseAudio: () -> Unit,
    onAyahVisibleExternal: ((QuranAyah) -> Unit)? = null,
    bannerAdContent: (@Composable () -> Unit)? = null,
    nativeAdContent: (@Composable () -> Unit)? = null,
    viewModel: QuranSuraDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    QuranSuraDetailScreen(
        state = uiState,
        onBack = onBack,
        onBookmarksClick = onBookmarksClick,
        onRetry = viewModel::retrySync,
        onDisplayModeSelected = viewModel::onDisplayModeSelected,
        onFontSizeChange = viewModel::onFontSizeChange,
        onReciterSelected = viewModel::onReciterSelected,
        onPlayPauseAyah = { ayah, isCurrentlyPlaying ->
            if (isCurrentlyPlaying) {
                viewModel.pauseAudio()
                onPauseAudio()
            } else {
                viewModel.playAyah(ayah.ayahNumber) { url ->
                    onPlayAudioUrl(url)
                }
            }
        },
        onDownloadAyah = { ayah -> viewModel.downloadAyah(ayah.ayahNumber) },
        onToggleBookmark = { ayah -> viewModel.toggleBookmark(ayah.ayahNumber) },
        onAyahVisible = { ayah ->
            viewModel.onAyahVisible(ayah.ayahNumber)
            onAyahVisibleExternal?.invoke(ayah)
        },
        bannerAdContent = bannerAdContent,
        nativeAdContent = nativeAdContent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranSuraDetailScreen(
    state: QuranSuraDetailUiState,
    onBack: () -> Unit,
    onBookmarksClick: () -> Unit = {},
    onRetry: () -> Unit,
    onDisplayModeSelected: (QuranDisplayMode) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onReciterSelected: (String) -> Unit,
    onPlayPauseAyah: (QuranAyah, Boolean) -> Unit,
    onDownloadAyah: (QuranAyah) -> Unit,
    onToggleBookmark: (QuranAyah) -> Unit,
    onAyahVisible: (QuranAyah) -> Unit,
    bannerAdContent: (@Composable () -> Unit)? = null,
    nativeAdContent: (@Composable () -> Unit)? = null,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showReciterSheet by remember { mutableStateOf(false) }
    var actionAyah by remember { mutableStateOf<QuranAyah?>(null) }
    var fontSliderValue by remember(state.fontSize) { mutableIntStateOf(state.fontSize) }
    val language = LocalLocale.current.platformLocale.language.lowercase(Locale.ROOT)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
        topBar = {
            QuranSuraDetailHeader(
                title = state.sura?.nameTurkish ?: stringResource(R.string.quran_title),
                onBack = onBack,
                onOpenReciter = { showReciterSheet = true },
                onOpenBookmarks = onBookmarksClick,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.shouldShowAds) {
                bannerAdContent?.invoke()
            }

            DisplayModeSelector(
                currentMode = state.displayMode,
                arabicLabel = stringResource(R.string.quran_mode_arabic),
                latinLabel = stringResource(R.string.quran_mode_latin),
                translationLabel = stringResource(R.string.quran_mode_translation),
                onModeSelected = onDisplayModeSelected,
            )

            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(
                    text = state.currentReciter.displayName,
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = fontSliderValue.toFloat(),
                    valueRange = 22f..42f,
                    onValueChange = { fontSliderValue = it.toInt() },
                    onValueChangeFinished = { onFontSizeChange(fontSliderValue) },
                )
            }

            if (state.isLoading && state.ayahs.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.quran_loading))
                }
                return@Column
            }

            if (state.errorMessage != null && state.ayahs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.quran_error_load_sura))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(state.errorMessage)
                    Spacer(modifier = Modifier.height(12.dp))
                    AppButton(text = stringResource(R.string.quran_retry), onClick = onRetry)
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 0.dp),
            ) {
                itemsIndexed(
                    items = state.ayahs,
                    key = { _, ayahState -> "${ayahState.ayah.suraNumber}_${ayahState.ayah.ayahNumber}" },
                ) { index, ayahState ->
                    LaunchedEffect(ayahState.ayah.ayahNumber) {
                        onAyahVisible(ayahState.ayah)
                    }

                    val translatedText = when (state.displayMode) {
                        QuranDisplayMode.ARABIC -> ""
                        QuranDisplayMode.LATIN -> ayahState.ayah.latin
                        QuranDisplayMode.TRANSLATION -> resolveTranslation(
                            language = language,
                            ayah = ayahState.ayah,
                        )
                    }

                    AyahItem(
                        state = ayahState,
                        displayMode = state.displayMode,
                        translatedText = translatedText,
                        fontSize = state.fontSize,
                        onPlayPauseClick = { ayah -> onPlayPauseAyah(ayah, ayahState.isCurrentlyPlaying) },
                        onDownloadClick = onDownloadAyah,
                        onBookmarkClick = onToggleBookmark,
                        onLongClick = { ayah -> actionAyah = ayah },
                    )

                    if (state.shouldShowAds && nativeAdContent != null && shouldInsertNativeAdAfterAyah(index, state.ayahs.size)) {
                        nativeAdContent()
                    }
                }
            }
        }
    }

    if (showReciterSheet) {
        ReciterSelectionSheet(
            reciters = state.availableReciters,
            selectedReciterId = state.currentReciter.id,
            onSelect = onReciterSelected,
            onDismiss = { showReciterSheet = false },
        )
    }

    actionAyah?.let { ayah ->
        val isBookmarked = state.ayahs.firstOrNull { it.ayah.ayahNumber == ayah.ayahNumber }?.isBookmarked == true
        ModalBottomSheet(onDismissRequest = { actionAyah = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppButton(
                    text = stringResource(R.string.quran_copy),
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("ayah", ayah.arabic)))
                        }
                        actionAyah = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButton(
                    text = if (isBookmarked) {
                        stringResource(R.string.quran_remove_bookmark)
                    } else {
                        stringResource(R.string.quran_add_bookmark)
                    },
                    onClick = {
                        onToggleBookmark(ayah)
                        actionAyah = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun QuranSuraDetailHeader(
    title: String,
    onBack: () -> Unit,
    onOpenReciter: () -> Unit,
    onOpenBookmarks: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 6.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onOpenReciter, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = stringResource(R.string.quran_select_reciter),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onOpenBookmarks, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = stringResource(R.string.quran_open_bookmarks),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun shouldInsertNativeAdAfterAyah(
    ayahIndex: Int,
    totalAyahCount: Int,
): Boolean {
    if (totalAyahCount <= 0) return false
    if (totalAyahCount == 1) return ayahIndex == 0
    if (ayahIndex >= totalAyahCount - 1) return false

    return if (totalAyahCount > 5) {
        (ayahIndex + 1) % 5 == 0
    } else {
        ayahIndex == 0
    }
}

private fun resolveTranslation(language: String, ayah: QuranAyah): String {
    return when {
        language.startsWith("de") && ayah.german.isNotBlank() -> ayah.german
        language.startsWith("en") && ayah.english.isNotBlank() -> ayah.english
        ayah.turkish.isNotBlank() -> ayah.turkish
        ayah.english.isNotBlank() -> ayah.english
        ayah.german.isNotBlank() -> ayah.german
        else -> ayah.latin
    }
}
