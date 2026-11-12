package com.parsfilo.contentapp.core.designsystem.component

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.PopupProperties
import com.parsfilo.contentapp.core.designsystem.R
import kotlinx.coroutines.launch

/** Native selection with a single Copy menu, including Android 9 fallback. */
@Composable
fun ReaderSelectionContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val selection = rememberSelectionState()
    val clipboard = LocalClipboard.current
    val toolbar = LocalTextToolbar.current
    val scope = rememberCoroutineScope()
    val platformProvider = LocalTextContextMenuToolbarProvider.current
    var isNativeMenuShowing by remember { mutableStateOf(false) }
    val observingProvider = remember(platformProvider) {
        platformProvider?.let { delegate ->
            object : TextContextMenuProvider {
                override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
                    isNativeMenuShowing = true
                    try {
                        delegate.showTextContextMenu(dataProvider)
                    } finally {
                        isNativeMenuShowing = false
                    }
                }
            }
        }
    }

    Box(modifier) {
        // Compose's new text context menu may be visible even when the legacy
        // LocalTextToolbar reports Hidden. Observe the actual provider lifecycle.
        CompositionLocalProvider(LocalTextContextMenuToolbarProvider provides observingProvider) {
            SelectionContainer(state = selection) { content() }
        }

        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            selection.selectedTexts.isNotEmpty() &&
            toolbar.status != TextToolbarStatus.Shown &&
            !isNativeMenuShowing
        ) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { selection.clear() },
                properties = PopupProperties(focusable = false),
                modifier = Modifier.testTag("reader_copy_menu"),
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_selection_copy)) },
                    modifier = Modifier.testTag("reader_copy_action"),
                    onClick = {
                        val selected = selection.selectedTexts.joinToString(separator = "") { it.text }
                        selection.clear()
                        if (selected.isNotEmpty()) {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", selected)))
                            }
                        }
                    },
                )
            }
        }
    }
}
