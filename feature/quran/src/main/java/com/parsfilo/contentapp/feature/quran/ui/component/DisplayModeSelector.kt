package com.parsfilo.contentapp.feature.quran.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModeButton(
            text = arabicLabel,
            selected = currentMode == QuranDisplayMode.ARABIC,
            onClick = { onModeSelected(QuranDisplayMode.ARABIC) },
            modifier = Modifier.weight(1f),
        )
        ModeButton(
            text = latinLabel,
            selected = currentMode == QuranDisplayMode.LATIN,
            onClick = { onModeSelected(QuranDisplayMode.LATIN) },
            modifier = Modifier.weight(1f),
        )
        ModeButton(
            text = translationLabel,
            selected = currentMode == QuranDisplayMode.TRANSLATION,
            onClick = { onModeSelected(QuranDisplayMode.TRANSLATION) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppButton(
        text = text,
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
        ),
    )
}
