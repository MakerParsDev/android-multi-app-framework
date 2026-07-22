package com.parsfilo.contentapp.feature.audio.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parsfilo.contentapp.core.designsystem.component.AppTextButton
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.feature.audio.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineAudioPlayer(
    state: AudioPlayerState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    val accent = colorScheme.secondary
    val content = colorScheme.onSurface
    val subdued = colorScheme.onSurfaceVariant

    // Show loading state while asset pack is being prepared
    if (state.assetLoading) {
        AssetLoadingBar(
            message = state.assetError ?: "Ses dosyası hazırlanıyor...",
            accent = accent,
            textColor = subdued,
            modifier = modifier
        )
        return
    }

    // Show error state if asset is not ready
    if (!state.assetReady) {
        AssetErrorBar(
            message = state.assetError ?: "Ses dosyası bulunamadı",
            onRetry = onRetry,
            accent = accent,
            textColor = subdued,
            modifier = modifier
        )
        return
    }

    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }

    val progress = if (state.duration > 0) {
        state.currentPosition.toFloat() / state.duration.toFloat()
    } else {
        0f
    }

    // Normal player UI
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.space8, vertical = dimens.space6)
            .border(
                width = dimens.stroke,
                color = accent.copy(alpha = 0.7f),
                shape = RoundedCornerShape(dimens.radiusPill)
            )
            .padding(horizontal = dimens.space4, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Play/Pause toggle
        IconButton(
            onClick = onPlayPause,
            modifier =
                Modifier
                    .testTag("audio_play_pause")
                    .size(dimens.iconXl),
        ) {
            Icon(
                imageVector = if (state.isPlaying) {
                    ImageVector.vectorResource(R.drawable.ic_pause)
                } else {
                    Icons.Filled.PlayArrow
                },
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                tint = accent,
                modifier = Modifier.size(dimens.iconMd + dimens.space2)
            )
        }

        // Stop button
        IconButton(
            onClick = onStop,
            modifier = Modifier.size(dimens.iconXl)
        ) {
            Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = "Stop",
                tint = accent,
                modifier = Modifier.size(dimens.iconSm + dimens.space2)
            )
        }

        // Seekbar (interactive Slider)
        Slider(
            value = if (isSeeking) seekValue else progress,
            onValueChange = { value ->
                isSeeking = true
                seekValue = value
            },
            onValueChangeFinished = {
                if (state.duration > 0) {
                    onSeek((seekValue * state.duration).toLong())
                }
                isSeeking = false
            },
            modifier = Modifier
                .weight(1f)
                .height(dimens.space32),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = subdued.copy(alpha = 0.2f)
            )
        )

        // Time display
        Text(
            text = "${formatTime(state.currentPosition)}/${formatTime(state.duration)}",
            color = content.copy(alpha = 0.9f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = dimens.space4, end = dimens.space8)
        )
    }
}

@Composable
private fun AssetLoadingBar(
    message: String,
    accent: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val dimens = LocalDimens.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.space16, vertical = dimens.space6)
            .border(
                width = dimens.stroke,
                color = accent.copy(alpha = 0.4f),
                shape = RoundedCornerShape(dimens.radiusPill)
            )
            .padding(horizontal = dimens.space16, vertical = dimens.space10),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space12)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(dimens.space20 - dimens.space2),
                color = accent,
                strokeWidth = dimens.space2
            )
            Text(
                text = message,
                color = textColor.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AssetErrorBar(
    message: String,
    onRetry: () -> Unit,
    accent: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val dimens = LocalDimens.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.space16, vertical = dimens.space6)
            .border(
                width = dimens.stroke,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                shape = RoundedCornerShape(dimens.radiusPill)
            )
            .padding(horizontal = dimens.space12, vertical = dimens.space4),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space8)
        ) {
            Text(
                text = message,
                color = textColor.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            AppTextButton(text = "", onClick = onRetry) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Tekrar Dene",
                    tint = accent,
                    modifier = Modifier.size(dimens.space20 - dimens.space2)
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
