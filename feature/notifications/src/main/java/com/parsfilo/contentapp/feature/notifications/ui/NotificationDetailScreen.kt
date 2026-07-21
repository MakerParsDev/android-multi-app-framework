package com.parsfilo.contentapp.feature.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.feature.notifications.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationDetailRoute(
    notificationId: Long,
    onBackClick: () -> Unit,
    viewModel: NotificationDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NotificationDetailScreen(
        notificationId = notificationId,
        onBackClick = onBackClick,
        uiState = uiState,
    )
}

@Composable
private fun NotificationDetailScreen(
    notificationId: Long,
    onBackClick: () -> Unit,
    uiState: NotificationDetailUiState,
) {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(dimens.space16)
    ) {
        TextButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.Start),
        ) {
            Text(text = stringResource(R.string.notifications_dialog_close))
        }
        Spacer(modifier = Modifier.height(dimens.space8))
        Text(
            text = stringResource(R.string.notifications_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = dimens.space16)
        )

        when (uiState) {
            NotificationDetailUiState.Loading -> {
                Text(
                    text = "Yükleniyor...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            NotificationDetailUiState.NotFound -> {
                Text(
                    text = "Bildirim bulunamadı (id=$notificationId).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.error,
                )
            }

            is NotificationDetailUiState.Success -> {
                val notification = uiState.notification
                Text(
                    text = notification.title.ifBlank { stringResource(R.string.notifications_title) },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(dimens.space8))
                Text(
                    text = notification.body.ifBlank { "-" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(dimens.space12))
                Text(
                    text = "notificationId=${notification.notificationId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(dimens.space4))
                Text(
                    text = "timestamp=${formatTimestamp(notification.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatTimestamp(value: Long): String {
    if (value <= 0L) return "-"
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))
    }.getOrElse { "-" }
}
