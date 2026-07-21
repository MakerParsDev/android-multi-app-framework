package com.parsfilo.contentapp.feature.quran.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parsfilo.contentapp.core.model.QuranAyah
import com.parsfilo.contentapp.core.model.QuranDisplayMode
import com.parsfilo.contentapp.feature.quran.ui.suradetail.AyahDisplayState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AyahItem(
    state: AyahDisplayState,
    displayMode: QuranDisplayMode,
    translatedText: String,
    fontSize: Int,
    onPlayPauseClick: (QuranAyah) -> Unit,
    onDownloadClick: (QuranAyah) -> Unit,
    onBookmarkClick: (QuranAyah) -> Unit,
    onLongClick: (QuranAyah) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = { onLongClick(state.ayah) },
            ),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.ayah.ayahNumber.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                IconButton(onClick = { onBookmarkClick(state.ayah) }) {
                    Icon(
                        imageVector = if (state.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                    )
                }
            }

            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    text = state.ayah.arabic,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize + 8).sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (displayMode != QuranDisplayMode.ARABIC) {
                Text(
                    text = translatedText,
                    fontSize = (fontSize - 8).coerceAtLeast(14).sp,
                    lineHeight = (fontSize - 2).coerceAtLeast(18).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onPlayPauseClick(state.ayah) }) {
                    Icon(
                        imageVector = if (state.isCurrentlyPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = { onDownloadClick(state.ayah) }) {
                    Icon(
                        imageVector = when {
                            state.isAudioDownloaded -> Icons.Default.TaskAlt
                            else -> Icons.Default.Download
                        },
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (state.isAudioDownloaded) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
