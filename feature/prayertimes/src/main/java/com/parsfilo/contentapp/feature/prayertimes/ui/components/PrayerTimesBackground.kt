package com.parsfilo.contentapp.feature.prayertimes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parsfilo.contentapp.feature.prayertimes.ui.isPrayerTimesDarkMode
import com.parsfilo.contentapp.feature.prayertimes.ui.prayerTimesBackgroundBrush

@Composable
internal fun PrayerTimesBackground(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(prayerTimesBackgroundBrush(isDark = isPrayerTimesDarkMode())),
    ) {
        content()
    }
}
