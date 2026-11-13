package com.parsfilo.contentapp.feature.content.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.parsfilo.contentapp.core.designsystem.component.ReaderSelectionContainer
import com.parsfilo.contentapp.feature.content.R

/**
 * Copy without a permanently visible icon. Some genuine Android 9 readers do
 * not expose a native selection toolbar: offer one transient Copy action there.
 * On newer Android versions keep the platform's own text-selection toolbar.
 * A named full-text action stays available to TalkBack for both paths.
 */
@Composable
internal fun ReaderCopyContent(
    copyText: String,
    copyLabel: String,
    copiedMessage: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var copyMenuVisible by remember { mutableStateOf(false) }

    fun copySelectedVerse(): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("reader_text", copyText))
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        view.announceForAccessibility(copiedMessage)
        return true
    }

    val accessibleModifier = modifier.semantics {
        customActions = listOf(
            CustomAccessibilityAction(
                label = copyLabel,
                action = { copySelectedVerse() },
            ),
        )
    }

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        Box(
            modifier = accessibleModifier.pointerInput(copyText) {
                detectTapGestures(onLongPress = { copyMenuVisible = true })
            },
        ) {
            content()
            DropdownMenu(
                expanded = copyMenuVisible,
                onDismissRequest = { copyMenuVisible = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.content_copy_menu)) },
                    onClick = {
                        copyMenuVisible = false
                        copySelectedVerse()
                    },
                )
            }
        }
    } else {
        ReaderSelectionContainer(modifier = accessibleModifier) { content() }
    }
}
