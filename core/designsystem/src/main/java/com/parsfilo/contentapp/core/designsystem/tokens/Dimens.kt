package com.parsfilo.contentapp.core.designsystem.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// UI spacing/size/radius/elevation tokens used across screens.
// UI layer should prefer LocalDimens.current over hardcoded dp values.
@Immutable
data class AppDimens(
    val space2: Dp = 2.dp,
    val space4: Dp = 4.dp,
    val space6: Dp = 6.dp,
    val space8: Dp = 8.dp,
    val space10: Dp = 10.dp,
    val space12: Dp = 12.dp,
    val space14: Dp = 14.dp,
    val space16: Dp = 16.dp,
    val space20: Dp = 20.dp,
    val space24: Dp = 24.dp,
    val space28: Dp = 28.dp,
    val space32: Dp = 32.dp,
    val space40: Dp = 40.dp,
    val space48: Dp = 48.dp,
    val radiusSmall: Dp = 8.dp,
    val radiusMedium: Dp = 12.dp,
    val radiusLarge: Dp = 16.dp,
    val radiusXLarge: Dp = 20.dp,
    val radiusPill: Dp = 24.dp,
    val iconXs: Dp = 16.dp,
    val iconSm: Dp = 20.dp,
    val iconMd: Dp = 24.dp,
    val iconLg: Dp = 32.dp,
    val iconXl: Dp = 48.dp,
    val strokeThin: Dp = 0.8.dp,
    val stroke: Dp = 1.dp,
    val topBarHeight: Dp = 56.dp,
    val buttonHeight: Dp = 48.dp,
    val bottomBarHeight: Dp = 68.dp,
    val elevationNone: Dp = 0.dp,
    val elevationLow: Dp = 1.dp,
    val elevationMedium: Dp = 3.dp,
    val elevationHigh: Dp = 5.dp,
)

val LocalDimens = staticCompositionLocalOf { AppDimens() }

object TouchTarget {
    val Min = 48.dp
}

@Composable
fun dimens(): AppDimens = LocalDimens.current
