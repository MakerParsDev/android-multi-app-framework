package com.parsfilo.contentapp.feature.prayertimes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parsfilo.contentapp.feature.prayertimes.R
import com.parsfilo.contentapp.feature.prayertimes.ui.PrayerTimesDesignTokens
import com.parsfilo.contentapp.feature.prayertimes.ui.prayerHeadlineFontFamily

@Composable
internal fun PrayerTimesAppHeader(
    appName: String,
    onSettingsClick: () -> Unit,
    onRewardsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        PrayerTimesDesignTokens.ActionPrimary,
                        PrayerTimesDesignTokens.ActionPrimarySoft,
                    )
                )
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(44.dp)
                .background(PrayerTimesDesignTokens.HeaderText.copy(alpha = 0.14f), CircleShape)
                .border(1.dp, PrayerTimesDesignTokens.HeaderText.copy(alpha = 0.22f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.prayertimes_open_location_settings),
                tint = PrayerTimesDesignTokens.HeaderText,
            )
        }

        Text(
            text = appName,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = prayerHeadlineFontFamily()),
            fontWeight = FontWeight.Bold,
            color = PrayerTimesDesignTokens.HeaderText,
        )

        IconButton(
            onClick = onRewardsClick,
            modifier = Modifier
                .size(44.dp)
                .background(PrayerTimesDesignTokens.HeaderText.copy(alpha = 0.14f), CircleShape)
                .border(1.dp, PrayerTimesDesignTokens.HeaderText.copy(alpha = 0.22f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.CardGiftcard,
                contentDescription = stringResource(R.string.prayertimes_open_rewards),
                tint = PrayerTimesDesignTokens.HeaderText,
            )
        }
    }
}
