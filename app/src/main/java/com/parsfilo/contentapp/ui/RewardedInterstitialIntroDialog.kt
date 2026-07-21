package com.parsfilo.contentapp.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.parsfilo.contentapp.feature.ads.RewardedInterstitialIntroSpec

@Composable
fun RewardedInterstitialIntroDialog(
    spec: RewardedInterstitialIntroSpec,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = spec.body,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(spec.confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(spec.skipLabel)
            }
        },
    )
}
