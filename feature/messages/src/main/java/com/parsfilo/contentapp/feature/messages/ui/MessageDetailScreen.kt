package com.parsfilo.contentapp.feature.messages.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.feature.messages.R

@Composable
fun MessageDetailRoute(
    messageId: String,
    onBackClick: () -> Unit
) {
    MessageDetailScreen(
        messageId = messageId,
        onBackClick = onBackClick
    )
}

@Composable
private fun MessageDetailScreen(
    messageId: String,
    onBackClick: () -> Unit
) {
    val dimens = LocalDimens.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.space16)
    ) {
        Text(
            text = stringResource(R.string.messages_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = dimens.space16)
        )
        Text(
            text = "Mesaj ID: $messageId",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
