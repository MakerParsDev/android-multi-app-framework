package com.parsfilo.contentapp.feature.prayertimes.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.parsfilo.contentapp.core.designsystem.theme.ArabicFontFamily

internal object PrayerTimesDesignTokens {
    const val GLASS_ALPHA = 0.82f

    val DarkBackgroundStart = Color(0xFF07160E)
    val DarkBackgroundEnd = Color(0xFF0D2117)

    val AccentPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = PrayerTimesColors.Accent

    val AccentSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.secondary

    val ActionPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = PrayerTimesColors.Accent

    val ActionPrimarySoft: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primaryContainer

    val GlassSurface: Color
        @Composable
        @ReadOnlyComposable
        get() = PrayerTimesColors.Surface

    val LightBackgroundStart: Color
        @Composable
        @ReadOnlyComposable
        get() = PrayerTimesColors.Background

    val LightBackgroundEnd: Color
        @Composable
        @ReadOnlyComposable
        get() = PrayerTimesColors.AccentSoft

    val HeaderGradientStart: Color
        @Composable
        @ReadOnlyComposable
        get() = PrayerTimesColors.NextPrayerCardBg

    val HeaderGradientEnd: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primaryContainer

    val ImsakGradientStart: Color
        @Composable
        @ReadOnlyComposable
        get() = PrayerTimesColors.ImsakCardBg

    val ImsakGradientEnd: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    val IftarGradientStart: Color
        @Composable
        @ReadOnlyComposable
        get() = PrayerTimesColors.IftarCardBg

    val IftarGradientEnd: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.secondaryContainer

    val ListItemPast: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)

    val GoldText: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.tertiary

    val HeaderText: Color
        @Composable
        @ReadOnlyComposable
        get() = PrayerTimesColors.CardText

    val DimText: Color
        @Composable
        @ReadOnlyComposable
        get() = PrayerTimesColors.CardTextSecondary
}

@Composable
internal fun prayerTimesBackgroundBrush(isDark: Boolean): Brush {
    return Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                PrayerTimesDesignTokens.DarkBackgroundStart,
                PrayerTimesDesignTokens.DarkBackgroundEnd,
            )
        } else {
            listOf(
                PrayerTimesDesignTokens.LightBackgroundStart,
                PrayerTimesDesignTokens.LightBackgroundEnd,
            )
        }
    )
}

internal fun prayerHeadlineFontFamily(): FontFamily {
    return ArabicFontFamily
}

internal fun prayerBodyFontFamily(): FontFamily {
    // Falls back to sans serif until custom font resources are added.
    return FontFamily.SansSerif
}

@Composable
internal fun isPrayerTimesDarkMode(): Boolean = isSystemInDarkTheme()
