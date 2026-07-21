package com.parsfilo.contentapp.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.parsfilo.contentapp.core.designsystem.theme.ShapeDefaults.AppShapes

// ─── Light scheme ────────────────────────────────────────────────────────────
private fun buildLightScheme(t: FlavorColorTokens) = lightColorScheme(
    primary = t.primary,
    onPrimary = t.onPrimary,
    primaryContainer = t.primaryDark,
    onPrimaryContainer = Color(0xFFFFF8E8),
    secondary = t.secondary,
    onSecondary = Color(0xFFFFFBF0),
    secondaryContainer = t.secondary.copy(alpha = 0.14f),
    onSecondaryContainer = t.secondary,
    tertiary = t.gold,
    onTertiary = Color(0xFF12121A),
    tertiaryContainer = t.gold.copy(alpha = 0.12f),
    onTertiaryContainer = t.gold,
    error = Color(0xFF9B1C1C),
    onError = Color(0xFFFFFBF0),
    errorContainer = Color(0xFFF5E0DE),
    onErrorContainer = Color(0xFF5C0A0A),
    background = t.background,
    onBackground = t.onBackground,
    surface = t.surface,
    onSurface = t.onSurface,
    surfaceVariant = t.surfaceVariant,
    onSurfaceVariant = t.onSurface.copy(alpha = 0.68f),
    outline = t.outline,
    outlineVariant = t.outline.copy(alpha = 0.50f),
    scrim = Color(0x80000000),
    inverseSurface = t.primaryDeep,
    inverseOnSurface = Color(0xFFF0EEE8),
    inversePrimary = t.secondary,
)

// ─── Dark scheme ─────────────────────────────────────────────────────────────
private fun buildDarkScheme(t: FlavorColorTokens) = darkColorScheme(
    primary = t.secondary,
    onPrimary = Color(0xFF08080F),
    primaryContainer = t.primary,
    onPrimaryContainer = Color(0xFFEEEAF8),
    secondary = t.secondary.copy(alpha = 0.85f),
    onSecondary = Color(0xFF08080F),
    secondaryContainer = t.primaryDark,
    onSecondaryContainer = t.secondary.copy(alpha = 0.90f),
    tertiary = t.gold.copy(alpha = 0.90f),
    onTertiary = Color(0xFF08080F),
    tertiaryContainer = t.primaryDeep,
    onTertiaryContainer = t.gold.copy(alpha = 0.80f),
    error = Color(0xFFEA8585),
    onError = Color(0xFF4A0808),
    errorContainer = Color(0xFF7A1515),
    onErrorContainer = Color(0xFFF5D5D5),
    // Gerçek OLED-dostu arka planlar
    background = Color(0xFF080A0F),
    onBackground = Color(0xFFEAE8F0),
    surface = Color(0xFF0E1018),
    onSurface = Color(0xFFE8E6F0),
    surfaceVariant = Color(0xFF1A1E2A),
    onSurfaceVariant = Color(0xFFB0AEC2),
    outline = Color(0xFF3A3E4E),
    outlineVariant = Color(0xFF262A38),
    scrim = Color(0xB0000000),
    inverseSurface = Color(0xFFE8E6F0),
    inverseOnSurface = Color(0xFF0E1018),
    inversePrimary = t.primary,
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    flavorName: String = "",
    content: @Composable () -> Unit,
) {
    val tokens = FlavorColors.forFlavor(flavorName)

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> buildDarkScheme(tokens)
        else -> buildLightScheme(tokens)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
