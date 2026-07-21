package com.parsfilo.contentapp.feature.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.core.designsystem.component.AppTextButton
import com.parsfilo.contentapp.core.designsystem.component.AppTextField
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.core.model.Message
import com.parsfilo.contentapp.feature.messages.R
import java.util.Locale

@Composable
fun MessagesRoute(
    viewModel: MessagesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MessagesScreen(
        uiState = uiState, onSendMessage = viewModel::sendMessage
    )
}

@Composable
fun MessagesScreen(
    uiState: MessagesUiState,
    onSendMessage: (String, String, String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val sendLabel = stringResource(R.string.messages_send)
    val dimens = LocalDimens.current

    Scaffold(
        containerColor = Color.Transparent, floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(
                        end = dimens.space16,
                        // Option 2: don't overlap the bottom bar; keep a clean margin from the screen edge.
                        bottom = dimens.space16,
                    ),
                shape = RoundedCornerShape(dimens.radiusLarge),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = dimens.elevationHigh,
                    pressedElevation = dimens.elevationHigh,
                    focusedElevation = dimens.elevationHigh,
                    hoveredElevation = dimens.elevationHigh,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Forum,
                    contentDescription = sendLabel,
                    modifier = Modifier
                        .width(dimens.iconMd)
                        .height(dimens.iconMd),
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(messagesBackgroundBrush())
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            when (uiState) {
                MessagesUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    }
                }

                is MessagesUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = dimens.space16),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(dimens.radiusLarge),
                            elevation = CardDefaults.cardElevation(defaultElevation = dimens.elevationLow),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.messages_error, uiState.throwable.message ?: ""
                                ),
                                modifier = Modifier.padding(dimens.space12),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                is MessagesUiState.Success -> {
                    if (uiState.messages.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = dimens.space24),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(dimens.space40))
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                shape = RoundedCornerShape(dimens.radiusLarge),
                                tonalElevation = dimens.elevationLow,
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = dimens.space16, vertical = dimens.space12
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Forum,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(dimens.space10))
                                    Column {
                                        Text(
                                            text = stringResource(R.string.messages_empty),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(R.string.messages_empty_hint),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(dimens.space12))
                            Spacer(modifier = Modifier.height(dimens.space16))
                            AppButton(
                                text = stringResource(R.string.messages_empty_cta),
                                onClick = { showDialog = true },
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = dimens.space12,
                                top = dimens.space8,
                                end = dimens.space12,
                                bottom = dimens.bottomBarHeight + dimens.space40 + WindowInsets.ime.asPaddingValues()
                                    .calculateBottomPadding()
                            ),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                                dimens.space8
                            )
                        ) {
                            items(
                                items = uiState.messages, key = { message ->
                                    "${message.userId}_${message.createdAt}_${message.subject.hashCode()}"
                                }
                            ) { message ->
                                MessageItemCard(message = message)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        SendMessageDialog(onDismiss = { }, onSend = { subject, message ->
            onSendMessage(subject, message, "GENERAL")
        })
    }
}

@Composable
private fun MessageItemCard(message: Message) {
    val dimens = LocalDimens.current

    val status = message.status.ifBlank { "PENDING" }.trim().uppercase(Locale.ROOT)
    val category = message.category.ifBlank { "GENERAL" }.trim().uppercase(Locale.ROOT)

    val statusStyle = statusStyle(status)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(dimens.radiusLarge),
        elevation = CardDefaults.cardElevation(defaultElevation = dimens.elevationMedium),
    ) {
        Column(modifier = Modifier.padding(dimens.space14)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.subject.ifBlank { "-" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(dimens.space8))
                Text(
                    text = formatMessageDate(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(dimens.space10))
            Text(
                text = message.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(dimens.space12))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                MessageTag(
                    text = category,
                    container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.width(dimens.space8))
                MessageTag(
                    text = status,
                    container = statusStyle.container,
                    content = statusStyle.content,
                    iconTint = statusStyle.content,
                    icon = statusStyle.icon,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (message.hasReply || message.replyCount > 0) {
                    MessageTag(
                        text = if (message.replyCount > 0) "${message.replyCount} REPLY" else "REPLY",
                        container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageTag(
    text: String,
    container: Color,
    content: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconTint: Color = content,
    style: TextStyle = MaterialTheme.typography.labelSmall,
) {
    val dimens = LocalDimens.current
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(dimens.radiusPill),
        tonalElevation = dimens.elevationNone,
        modifier = Modifier.clip(RoundedCornerShape(dimens.radiusPill)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.space10, vertical = dimens.space6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .width(dimens.iconXs)
                        .height(dimens.iconXs)
                        .offset(y = (-0.5).dp),
                )
                Spacer(modifier = Modifier.width(dimens.space6))
            }
            Text(
                text = text,
                style = style,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private data class StatusStyle(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val container: Color,
    val content: Color,
)

@Composable
private fun statusStyle(statusUpper: String): StatusStyle {
    val cs = MaterialTheme.colorScheme
    return when (statusUpper) {
        "DONE", "RESOLVED", "SUCCESS" -> StatusStyle(
            icon = Icons.Outlined.TaskAlt,
            container = cs.tertiaryContainer.copy(alpha = 0.8f),
            content = cs.onTertiaryContainer,
        )

        "PENDING", "WAITING", "OPEN" -> StatusStyle(
            icon = Icons.Outlined.Schedule,
            container = cs.secondaryContainer.copy(alpha = 0.8f),
            content = cs.onSecondaryContainer,
        )

        "ERROR", "FAILED", "REJECTED" -> StatusStyle(
            icon = Icons.Outlined.Schedule,
            container = cs.errorContainer.copy(alpha = 0.85f),
            content = cs.onErrorContainer,
        )

        else -> StatusStyle(
            icon = Icons.Outlined.Schedule,
            container = cs.surfaceVariant.copy(alpha = 0.75f),
            content = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun messagesBackgroundBrush(): Brush {
    val cs = MaterialTheme.colorScheme
    val base = cs.background
    val top = lerp(base, cs.primary, 0.08f)
    val mid = lerp(base, cs.primary, 0.03f)
    return Brush.verticalGradient(listOf(top, mid, base))
}

private fun formatMessageDate(epochMillis: Long): String = runCatching {
    val df = java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM, java.text.DateFormat.MEDIUM, Locale.getDefault()
    )
    df.format(java.util.Date(epochMillis))
}.getOrDefault("")

@Composable
fun SendMessageDialog(
    onDismiss: () -> Unit,
    onSend: (String, String) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val dimens = LocalDimens.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.messages_send)) },
        text = {
            Column {
                AppTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = stringResource(R.string.messages_subject),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(dimens.space4))
                AppTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = stringResource(R.string.messages_body),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            AppTextButton(
                text = stringResource(R.string.send),
                onClick = { onSend(subject, message) },
                enabled = subject.isNotBlank() && message.isNotBlank(),
            )
        },
        dismissButton = {
            AppTextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
            )
        }
    )
}
