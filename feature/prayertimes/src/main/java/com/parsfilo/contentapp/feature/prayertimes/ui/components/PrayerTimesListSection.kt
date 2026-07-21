package com.parsfilo.contentapp.feature.prayertimes.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parsfilo.contentapp.feature.prayertimes.R
import com.parsfilo.contentapp.feature.prayertimes.data.PrayerDateTime
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerTimesDay
import com.parsfilo.contentapp.feature.prayertimes.ui.PrayerTimesDesignTokens
import com.parsfilo.contentapp.feature.prayertimes.ui.prayerBodyFontFamily

@Composable
internal fun PrayerTimesListSection(
    variant: PrayerAppVariant,
    upcomingDays: List<PrayerTimesDay>,
    selectedAlarmPrayerKeys: Set<String>,
    onToggleAlarm: (String) -> Unit,
) {
    if (upcomingDays.isEmpty()) return

    var selectedDayIndex by remember(upcomingDays) { mutableIntStateOf(0) }
    val dayIndex = selectedDayIndex.coerceIn(0, upcomingDays.lastIndex)
    val selectedDay = upcomingDays[dayIndex]

    val prayers = prayerItemsForVariant(variant).map { item ->
        val hm = when (item.key) {
            "imsak" -> selectedDay.imsak
            "gunes" -> selectedDay.gunes
            "ogle" -> selectedDay.ogle
            "ikindi" -> selectedDay.ikindi
            "aksam" -> selectedDay.aksam
            "yatsi" -> selectedDay.yatsi
            else -> "--:--"
        }
        PrayerRow(item = item, hm = hm)
    }

    val currentHm = PrayerDateTime.currentTimeHm()
    val useRelativeState = dayIndex == 0
    val nextIndex = if (useRelativeState) {
        prayers.indexOfFirst { it.hm > currentHm }.let { if (it < 0) prayers.lastIndex else it }
    } else {
        -1
    }
    val activeIndex = if (useRelativeState) (nextIndex - 1).coerceAtLeast(0) else -1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                PrayerTimesDesignTokens.GlassSurface.copy(alpha = 0.88f),
                RoundedCornerShape(16.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            enabled = dayIndex > 0,
            onClick = {
                selectedDayIndex = (selectedDayIndex - 1).coerceAtLeast(0)
            },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.prayertimes_previous_day),
            )
        }

        Text(
            text = selectedDay.localDate,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = prayerBodyFontFamily()),
            fontWeight = FontWeight.Bold,
        )

        IconButton(
            enabled = dayIndex < upcomingDays.lastIndex,
            onClick = {
                selectedDayIndex = (selectedDayIndex + 1).coerceAtMost(upcomingDays.lastIndex)
            },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.prayertimes_next_day),
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    prayers.forEachIndexed { index, row ->
        val rowState = when {
            useRelativeState && index == activeIndex -> PrayerRowState.ACTIVE
            useRelativeState && index == nextIndex -> PrayerRowState.NEXT
            useRelativeState && row.hm < currentHm -> PrayerRowState.PAST
            else -> PrayerRowState.NORMAL
        }

        PrayerTimeRow(
            row = row,
            rowState = rowState,
            isAlarmEnabled = selectedAlarmPrayerKeys.isEmpty() ||
                selectedAlarmPrayerKeys.contains(
                    row.item.key,
                ),
            onToggleAlarm = { onToggleAlarm(row.item.key) },
        )

        if (index < prayers.lastIndex) {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private enum class PrayerRowState {
    ACTIVE,
    NEXT,
    PAST,
    NORMAL,
}

private data class PrayerRow(
    val item: PrayerUiItem,
    val hm: String,
)

@Composable
private fun PrayerTimeRow(
    row: PrayerRow,
    rowState: PrayerRowState,
    isAlarmEnabled: Boolean,
    onToggleAlarm: () -> Unit,
) {
    val pulseTransition = rememberInfiniteTransition(label = "pulse")

    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (rowState == PrayerRowState.ACTIVE) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    val shimmerAlpha by pulseTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer_alpha",
    )

    val backgroundColor = when (rowState) {
        PrayerRowState.ACTIVE -> PrayerTimesDesignTokens.ActionPrimary.copy(alpha = 0.97f)
        PrayerRowState.NEXT -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f)
        PrayerRowState.PAST -> PrayerTimesDesignTokens.ListItemPast
        PrayerRowState.NORMAL -> PrayerTimesDesignTokens.GlassSurface.copy(alpha = 0.88f)
    }

    val alpha = when (rowState) {
        PrayerRowState.PAST -> 0.72f
        PrayerRowState.NEXT -> shimmerAlpha
        else -> 1f
    }

    val contentColor =
        if (rowState == PrayerRowState.ACTIVE) PrayerTimesDesignTokens.HeaderText else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
                shadowElevation = 6f
            }
            .alpha(alpha)
            .background(backgroundColor, RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 14.dp)
            .semantics {
                contentDescription = "${row.item.key} namazı vakti ${row.hm.replace(':', ' ')}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = row.item.iconRes),
            contentDescription = null,
            tint = if (rowState == PrayerRowState.ACTIVE) {
                PrayerTimesDesignTokens.HeaderText
            } else {
                PrayerTimesDesignTokens.AccentPrimary
            },
        )

        Text(
            text = stringResource(row.item.labelRes),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = prayerBodyFontFamily()),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = row.hm,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = prayerBodyFontFamily()),
            color = contentColor,
            fontWeight = FontWeight.Bold,
        )

        IconButton(
            onClick = onToggleAlarm,
            modifier = Modifier
                .size(42.dp)
                .semantics {
                    contentDescription = if (isAlarmEnabled) "Alarm açık" else "Alarm kapalı"
                },
        ) {
            Icon(
                imageVector = if (isAlarmEnabled) {
                    Icons.Filled.Notifications
                } else {
                    Icons.Outlined.NotificationsNone
                },
                contentDescription = null,
                tint = contentColor,
            )
        }
    }
}
