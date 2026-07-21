package com.parsfilo.contentapp.feature.prayertimes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parsfilo.contentapp.feature.prayertimes.R
import com.parsfilo.contentapp.feature.prayertimes.ui.PrayerTimesDesignTokens
import com.parsfilo.contentapp.feature.prayertimes.ui.prayerBodyFontFamily
import com.parsfilo.contentapp.feature.prayertimes.ui.prayerHeadlineFontFamily
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
internal fun HeroHeaderCard(
    currentTimeMillis: Long,
    hijriDateText: String,
    gregorianDateText: String,
    locationName: String,
    onLocationClick: () -> Unit,
    onOpenAlarmSettings: () -> Unit,
    onOpenQibla: () -> Unit,
) {
    val clock = SimpleDateFormat("HH:mm", Locale.US).format(currentTimeMillis)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        PrayerTimesDesignTokens.DarkBackgroundEnd,
                        PrayerTimesDesignTokens.DarkBackgroundStart,
                    )
                )
            )
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_stat_prayer),
                contentDescription = stringResource(R.string.prayertimes_app_logo_desc),
                tint = PrayerTimesDesignTokens.GoldText,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenQibla) {
                    Icon(
                        imageVector = Icons.Outlined.Explore,
                        contentDescription = stringResource(R.string.prayertimes_open_qibla),
                        tint = PrayerTimesDesignTokens.HeaderText,
                    )
                }
                IconButton(onClick = onOpenAlarmSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = stringResource(R.string.prayertimes_open_alarm_settings),
                        tint = PrayerTimesDesignTokens.HeaderText,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = clock,
                fontFamily = prayerHeadlineFontFamily(),
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = PrayerTimesDesignTokens.GoldText,
                modifier = Modifier.semantics {
                    contentDescription = "Saat $clock"
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = hijriDateText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = prayerBodyFontFamily()),
                    color = PrayerTimesDesignTokens.HeaderText,
                )
                Text(
                    text = gregorianDateText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = prayerBodyFontFamily()),
                    color = PrayerTimesDesignTokens.DimText,
                )
            }
        }

        Text(
            text = locationName,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(100.dp))
                .clickable(onClick = onLocationClick)
                .background(PrayerTimesDesignTokens.AccentSecondary.copy(alpha = 0.25f))
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .semantics {
                    contentDescription = "Konum $locationName"
                },
            color = PrayerTimesDesignTokens.HeaderText,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = prayerBodyFontFamily()),
        )
    }
}
