package com.parsfilo.contentapp.feature.prayertimes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerTimesDay
import com.parsfilo.contentapp.feature.prayertimes.ui.PrayerTimesDesignTokens
import com.parsfilo.contentapp.feature.prayertimes.ui.prayerBodyFontFamily

@Composable
internal fun PrayerDayCard(
    day: PrayerTimesDay,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val containerColor = if (selected) {
        PrayerTimesDesignTokens.AccentPrimary.copy(alpha = 0.85f)
    } else {
        PrayerTimesDesignTokens.GlassSurface.copy(alpha = 0.82f)
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = day.localDate,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = prayerBodyFontFamily()),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = day.gunes,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = prayerBodyFontFamily()),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
