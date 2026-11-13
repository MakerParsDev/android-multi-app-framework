package com.parsfilo.contentapp.core.designsystem.component

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Preserve Android's own selection toolbar. A second app-supplied Copy popup
 * duplicated the native action on genuine Android 9 Play-installed readers.
 */
@Composable
fun ReaderSelectionContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SelectionContainer(modifier = modifier) { content() }
}
