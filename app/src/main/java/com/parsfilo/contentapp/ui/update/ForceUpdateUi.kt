package com.parsfilo.contentapp.ui.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.update.UpdatePolicy

@Composable
fun HardUpdateRequiredOverlay(
    policy: UpdatePolicy.Hard,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    BackHandler(enabled = true) {}

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = dimens.space6,
            modifier =
                Modifier
                    .padding(dimens.space16)
                    .fillMaxWidth(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(dimens.space20),
                verticalArrangement = Arrangement.spacedBy(dimens.space12),
            ) {
                Text(
                    text = policy.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = policy.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onUpdateClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(policy.updateButton)
                }
            }
        }
    }
}

@Composable
fun SoftUpdateDialog(
    policy: UpdatePolicy.Soft,
    onUpdateClick: () -> Unit,
    onLaterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onLaterClick,
        title = { Text(text = policy.title) },
        text = { Text(text = policy.message) },
        confirmButton = {
            Button(onClick = onUpdateClick) {
                Text(text = policy.updateButton)
            }
        },
        dismissButton = {
            TextButton(onClick = onLaterClick) {
                Text(text = policy.laterButton)
            }
        },
    )
}
