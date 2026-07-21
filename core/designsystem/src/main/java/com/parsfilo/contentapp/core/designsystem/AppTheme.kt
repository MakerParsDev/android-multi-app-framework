package com.parsfilo.contentapp.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.parsfilo.contentapp.core.designsystem.tokens.AppDimens
import com.parsfilo.contentapp.core.designsystem.tokens.AppMotion
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.core.designsystem.tokens.LocalMotion
import com.parsfilo.contentapp.core.designsystem.theme.AppTheme as MaterialAppTheme

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    flavorName: String = "",
    content: @Composable () -> Unit,
) {
    MaterialAppTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        flavorName = flavorName,
    ) {
        CompositionLocalProvider(
            LocalDimens provides AppDimens(),
            LocalMotion provides AppMotion(),
            content = content,
        )
    }
}
