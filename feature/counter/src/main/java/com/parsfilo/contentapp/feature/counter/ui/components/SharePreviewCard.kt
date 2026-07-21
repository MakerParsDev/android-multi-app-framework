package com.parsfilo.contentapp.feature.counter.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.parsfilo.contentapp.feature.counter.R

@Composable
fun SharePreviewCard(
    text: String,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.counter_share_preview_title)) },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onShare) {
                Text(text = stringResource(R.string.counter_share))
            }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = onCopy) {
                    Text(text = stringResource(R.string.counter_copy))
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        },
    )
}
