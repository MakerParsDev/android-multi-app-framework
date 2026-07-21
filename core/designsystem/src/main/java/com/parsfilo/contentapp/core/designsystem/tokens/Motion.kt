package com.parsfilo.contentapp.core.designsystem.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

// Motion tokens for animation durations/easing.
@Immutable
data class AppMotion(
    val durationFast: Int = 150,
    val durationMedium: Int = 250,
    val durationSlow: Int = 400,
    val emphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
)

val LocalMotion = staticCompositionLocalOf { AppMotion() }
