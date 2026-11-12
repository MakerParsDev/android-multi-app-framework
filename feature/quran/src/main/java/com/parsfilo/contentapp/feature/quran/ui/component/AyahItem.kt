package com.parsfilo.contentapp.feature.quran.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parsfilo.contentapp.core.designsystem.component.ReaderSelectionContainer
import com.parsfilo.contentapp.core.model.QuranAyah
import com.parsfilo.contentapp.core.model.QuranDisplayMode
import com.parsfilo.contentapp.feature.quran.R
import com.parsfilo.contentapp.feature.quran.ui.suradetail.AyahDisplayState

@Composable
fun AyahItem(
    state: AyahDisplayState,
    displayMode: QuranDisplayMode,
    translatedText: String,
    fontSize: Int,
    onPlayPauseClick: (QuranAyah) -> Unit,
    onBookmarkClick: (QuranAyah) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ayahNumber = state.ayah.ayahNumber
    val bookmarkLabel = stringResource(
        if (state.isBookmarked) R.string.quran_ayah_bookmark_remove else R.string.quran_ayah_bookmark_add,
        ayahNumber,
    )
    val playbackLabel = stringResource(
        if (state.isCurrentlyPlaying) R.string.quran_ayah_pause else R.string.quran_ayah_play,
        ayahNumber,
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("quran_ayah_" + ayahNumber + "_card"),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Physical left/right is intentionally stable even in Arabic text mode.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onBookmarkClick(state.ayah) },
                        modifier = Modifier.testTag("quran_ayah_" + ayahNumber + "_bookmark"),
                    ) {
                        Icon(
                            imageVector = if (state.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = bookmarkLabel,
                        )
                    }
                    Text(
                        text = ayahNumber.toString(),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    IconButton(
                        onClick = { onPlayPauseClick(state.ayah) },
                        modifier = Modifier.testTag("quran_ayah_" + ayahNumber + "_play"),
                    ) {
                        Icon(
                            imageVector = if (state.isCurrentlyPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = playbackLabel,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            // Native text handles and Android's Copy action replace a separate modal.
            ReaderSelectionContainer {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        text = state.ayah.arabic,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize + 8).sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (displayMode != QuranDisplayMode.ARABIC) {
                ReaderSelectionContainer {
                    Text(
                        text = translatedText,
                        fontSize = (fontSize - 8).coerceAtLeast(14).sp,
                        lineHeight = (fontSize - 2).coerceAtLeast(18).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
