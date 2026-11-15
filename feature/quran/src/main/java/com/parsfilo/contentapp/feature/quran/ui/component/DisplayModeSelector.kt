package com.parsfilo.contentapp.feature.quran.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.core.model.QuranDisplayMode

@Composable
fun DisplayModeSelector(
    currentMode: QuranDisplayMode,
    arabicLabel: String,
    latinLabel: String,
    translationLabel: String,
    onModeSelected: (QuranDisplayMode) -> Unit,
    modifier: Modifier = Modifier,
    firstModeFocusRequester: FocusRequester? = null,
    previousFocusRequester: FocusRequester? = null,
) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val modeWidth = if (largeText) Modifier.fillMaxWidth() else Modifier
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModeButton(
            text = arabicLabel,
            selected = currentMode == QuranDisplayMode.ARABIC,
            largeText = largeText,
            onClick = { onModeSelected(QuranDisplayMode.ARABIC) },
            modifier = modeWidth.then(
                if (firstModeFocusRequester != null) {
                    Modifier
                        .focusRequester(firstModeFocusRequester)
                        .focusProperties {
                            if (previousFocusRequester != null) {
                                previous = previousFocusRequester
                            }
                        }
                } else {
                    Modifier
                },
            ),
        )
        ModeButton(
            text = latinLabel,
            selected = currentMode == QuranDisplayMode.LATIN,
            largeText = largeText,
            onClick = { onModeSelected(QuranDisplayMode.LATIN) },
            modifier = modeWidth,
        )
        ModeButton(
            text = translationLabel,
            selected = currentMode == QuranDisplayMode.TRANSLATION,
            largeText = largeText,
            onClick = { onModeSelected(QuranDisplayMode.TRANSLATION) },
            modifier = modeWidth,
        )
    }
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    largeText: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppButton(
        text = text,
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        content = if (largeText) {
            {
                Text(text = text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        } else {
            null
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
        ),
    )
}
