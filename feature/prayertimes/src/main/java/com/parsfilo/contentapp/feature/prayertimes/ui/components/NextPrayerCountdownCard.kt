package com.parsfilo.contentapp.feature.prayertimes.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parsfilo.contentapp.feature.prayertimes.R
import com.parsfilo.contentapp.feature.prayertimes.ui.PrayerTimesDesignTokens
import com.parsfilo.contentapp.feature.prayertimes.ui.prayerBodyFontFamily

@Composable
internal fun NextPrayerCountdownCard(
    nextPrayerLabel: String,
    nextPrayerTime: String,
    countdown: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        PrayerTimesDesignTokens.HeaderGradientStart,
                        PrayerTimesDesignTokens.HeaderGradientEnd,
                    )
                )
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.prayertimes_next_prayer_title),
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = prayerBodyFontFamily()),
            color = PrayerTimesDesignTokens.DimText,
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = nextPrayerLabel,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = prayerBodyFontFamily()),
                color = PrayerTimesDesignTokens.HeaderText,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = nextPrayerTime,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = prayerBodyFontFamily()),
                color = PrayerTimesDesignTokens.HeaderText,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.prayertimes_countdown_label),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = prayerBodyFontFamily()),
                color = PrayerTimesDesignTokens.DimText,
            )
            AnimatedContent(
                targetState = countdown,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "countdown_animation",
            ) { animated ->
                Text(
                    text = animated,
                    color = PrayerTimesDesignTokens.HeaderText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = prayerBodyFontFamily()),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics {
                        contentDescription = "Kalan süre $animated"
                    },
                )
            }
        }
    }
}
