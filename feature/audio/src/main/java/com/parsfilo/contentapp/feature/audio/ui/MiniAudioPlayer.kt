package com.parsfilo.contentapp.feature.audio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val seekAccessibleName = stringResource(R.string.audio_seek)

    // Show loading state while asset pack is being prepared
    if (state.assetLoading) {
        AssetLoadingBar(
            message = state.assetError ?: stringResource(R.string.audio_asset_preparing),
            accent = accent,
            textColor = subdued,
            modifier = modifier
        )
        return
    }

    // Show error state if asset is not ready
    if (!state.assetReady) {
        AssetErrorBar(
            message = state.assetError ?: stringResource(R.string.audio_asset_missing),
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

    // A two-row player gives every transport control a full 48dp target and
    // separates the seek gesture from elapsed/duration text on narrow phones.
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.space8, vertical = dimens.space4)
            .testTag("audio_player"),
        shape = RoundedCornerShape(dimens.radiusLarge),
        color = colorScheme.surface,
        border = BorderStroke(dimens.stroke, colorScheme.outlineVariant),
        tonalElevation = dimens.elevationLow,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = dimens.space12,
                vertical = dimens.space4,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.space4),
            ) {
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .testTag("audio_play_pause")
                        .size(dimens.iconXl),
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) {
                            ImageVector.vectorResource(R.drawable.ic_pause)
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.audio_pause else R.string.audio_play
                        ),
                        tint = accent,
                        modifier = Modifier.size(dimens.iconMd + dimens.space2),
                    )
                }

                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .testTag("audio_stop")
                        .size(dimens.iconXl),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.audio_stop),
                        tint = accent,
                        modifier = Modifier.size(dimens.iconMd),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatTime(state.currentPosition) + "/" + formatTime(state.duration),
                    color = content,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.testTag("audio_elapsed_duration"),
                )
            }

            // Compose Slider exposes a 44dp inner semantics rectangle on API28
            // even when its outer measured row is taller. Merge its adjustable
            // action into a 64dp accessibility target without changing gestures.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.iconXl + dimens.space16)
                    .testTag("audio_seek")
                    .semantics(mergeDescendants = true) {
                        contentDescription = seekAccessibleName
                    },
                contentAlignment = Alignment.Center,
            ) {
                Slider(
                    value = (if (isSeeking) seekValue else progress).coerceIn(0f, 1f),
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
                    enabled = state.duration > 0,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = subdued.copy(alpha = 0.25f),
                    ),
                )
            }
        }
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
            val retryAccessibleName = stringResource(R.string.audio_retry)
            AppTextButton(
                text = "",
                onClick = onRetry,
                modifier = Modifier.semantics { contentDescription = retryAccessibleName },
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
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
