package com.parsfilo.contentapp.feature.prayertimes.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

internal object PrayerTimesColors {
    val Background: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.background
    val Surface: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface
    val Accent: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary
    val AccentSoft: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceVariant
    val NeutralButton: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceVariant
    val NeutralText: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    // Büyük kartlar flavor temasına bağlı olmalı.
    val ImsakCardBg: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primaryContainer
    val IftarCardBg: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.secondary
    val NextPrayerCardBg: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary
    val CardText = Color(0xFFFFFFFF)
    val CardTextSecondary = Color(0xD9FFFFFF)
}
