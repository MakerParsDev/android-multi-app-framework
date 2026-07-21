package com.parsfilo.contentapp.feature.notifications.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.core.designsystem.component.AppCard
import com.parsfilo.contentapp.core.designsystem.theme.app_transparent
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.core.model.Notification
import com.parsfilo.contentapp.feature.notifications.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Use a high-contrast color; app background is green so "success" dot becomes invisible.
@Composable
private fun unreadIndicatorColor(): Color = MaterialTheme.colorScheme.secondary

@Composable
fun NotificationsRoute(
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationManager = remember(context) { NotificationManagerCompat.from(context) }

    NotificationsScreen(
        uiState = uiState,
        onNotificationOpen = { id, notificationId ->
            viewModel.logNotificationOpen()
            viewModel.markAsRead(id)
            // Clears system tray item + app icon badge (badge derives from active notifications).
            notificationManager.cancel(notificationId.hashCode())
        },
        onNotificationMarkUnread = { id ->
            viewModel.markAsUnread(id)
        },
        onNotificationDelete = { id, notificationId ->
            viewModel.deleteNotification(id)
            // If it exists in system tray, remove it as well.
            notificationManager.cancel(notificationId.hashCode())
        },
        onMarkAllRead = {
            viewModel.markAllAsRead()
            notificationManager.cancelAll()
        },
        onDeleteAll = {
            viewModel.deleteAllNotifications()
            notificationManager.cancelAll()
        },
    )
}

@Composable
fun NotificationsScreen(
    uiState: NotificationsUiState,
    onNotificationOpen: (id: Long, notificationId: String) -> Unit,
    onNotificationMarkUnread: (id: Long) -> Unit,
    onNotificationDelete: (id: Long, notificationId: String) -> Unit,
    onMarkAllRead: () -> Unit = {},
    onDeleteAll: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colorScheme = MaterialTheme.colorScheme
    val dimens = LocalDimens.current
    var openedNotification by remember { mutableStateOf<Notification?>(null) }
    var notificationsPermissionEnabled by remember {
        mutableStateOf(isNotificationPermissionEnabled(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsPermissionEnabled = isNotificationPermissionEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .background(colorScheme.background)
    ) {
        NotificationsTopBar(
            hasItems = uiState is NotificationsUiState.Success && uiState.notifications.isNotEmpty(),
            onMarkAllRead = onMarkAllRead,
            onDeleteAll = onDeleteAll
        )

        when (uiState) {
            NotificationsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colorScheme.secondary)
                }
            }
            is NotificationsUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.notifications_error, uiState.throwable.message ?: ""),
                        color = colorScheme.error
                    )
                }
            }
            is NotificationsUiState.Success -> {
                if (uiState.notifications.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsNone,
                                contentDescription = null,
                                tint = colorScheme.secondary.copy(alpha = 0.9f),
                                modifier = Modifier.size(dimens.space32 + dimens.space4)
                            )
                            Spacer(modifier = Modifier.height(dimens.space10))
                            Text(
                                text = if (notificationsPermissionEnabled) {
                                    stringResource(R.string.notifications_empty)
                                } else {
                                    stringResource(R.string.notifications_permission_disabled_title)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(dimens.space4))
                            Text(
                                text = if (notificationsPermissionEnabled) {
                                    stringResource(R.string.notifications_empty_hint)
                                } else {
                                    stringResource(R.string.notifications_permission_disabled_hint)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                            if (!notificationsPermissionEnabled) {
                                Spacer(modifier = Modifier.height(dimens.space16))
                                AppButton(
                                    text = stringResource(R.string.notifications_empty_cta),
                                    onClick = {
                                        buildNotificationSettingsIntent(context)?.let { intent ->
                                            context.startActivity(intent)
                                        }
                                    },
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = dimens.space12,
                            vertical = dimens.space4,
                        )
                    ) {
                        items(
                            items = uiState.notifications,
                            key = { it.id }
                        ) { notification ->
                            NotificationItem(
                                notification = notification,
                                onOpen = {
                                    onNotificationOpen(notification.id, notification.notificationId)
                                    // Reflect the action immediately inside the dialog.
                                    openedNotification = notification.copy(isRead = true)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    openedNotification?.let { selected ->
        NotificationDialog(
            notification = selected,
            onDismiss = { openedNotification = null },
            onMarkUnread = {
                onNotificationMarkUnread(selected.id)
                openedNotification = null
            },
            onDelete = {
                onNotificationDelete(selected.id, selected.notificationId)
                openedNotification = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsTopBar(
    hasItems: Boolean,
    onMarkAllRead: () -> Unit,
    onDeleteAll: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = stringResource(R.string.notifications_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onBackground
            )
        },
        actions = {
            if (hasItems) {
                IconButton(onClick = onMarkAllRead) {
                    Icon(
                        imageVector = Icons.Filled.DoneAll,
                        contentDescription = stringResource(R.string.notifications_action_mark_all_read),
                        tint = colorScheme.secondary,
                    )
                }
                IconButton(onClick = onDeleteAll) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.notifications_action_delete_all),
                        tint = colorScheme.error,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = app_transparent,
            titleContentColor = colorScheme.onBackground,
            actionIconContentColor = colorScheme.onBackground
        ),
    )
}

private fun buildNotificationSettingsIntent(context: Context): Intent? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
        }
    }
}

private fun isNotificationPermissionEnabled(context: android.content.Context): Boolean {
    val managerEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    return managerEnabled && runtimeGranted
}

@Composable
private fun NotificationItem(
    notification: Notification,
    onOpen: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.forLanguageTag("tr"))
    val formattedDate = dateFormat.format(Date(notification.timestamp))
    val dimens = LocalDimens.current
    val stateLabel = stringResource(
        if (!notification.isRead) R.string.notifications_state_unread else R.string.notifications_state_read
    )

    val shape = RoundedCornerShape(dimens.radiusMedium)
    val unread = !notification.isRead
    val indicatorColor = unreadIndicatorColor()
    // Make the difference obvious on the lime background:
    // - Unread: clean surface (almost white)
    // - Read: tinted surfaceVariant (green-ish)
    val containerColor = if (unread) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
    val borderColor = if (unread) {
        indicatorColor.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.space2 + dimens.stroke)
            .clip(shape)
            .border(BorderStroke(dimens.stroke, borderColor), shape)
            .semantics {
                contentDescription = "${notification.title}, $stateLabel, $formattedDate"
            },
        onClick = onOpen,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = shape,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (unread) dimens.elevationMedium else dimens.elevationNone
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.space12),
            verticalAlignment = Alignment.Top
        ) {
            // Unread indicator: a left bar + a dot (high contrast).
            val railColor = if (unread) indicatorColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(dimens.radiusSmall))
                    .background(railColor)
            )
            Spacer(modifier = Modifier.size(dimens.space10))

            if (unread) {
                Box(
                    modifier = Modifier
                        .size(dimens.space8)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
            } else {
                // Keep alignment stable without visually suggesting "unread".
                Spacer(modifier = Modifier.size(dimens.space8))
            }
            Spacer(modifier = Modifier.size(dimens.space8))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (unread) 1f else 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(dimens.space2))
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = if (unread) 1f else 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = formattedDate,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
                    .copy(alpha = if (unread) 1f else 0.70f)
            )
        }
    }
}

@Composable
private fun NotificationDialog(
    notification: Notification,
    onDismiss: () -> Unit,
    onMarkUnread: () -> Unit,
    onDelete: () -> Unit
) {
    val dimens = LocalDimens.current
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.forLanguageTag("tr"))
    val formattedDate = dateFormat.format(Date(notification.timestamp))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(dimens.space10))
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.notifications_dialog_close))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.space8)) {
                if (notification.isRead) {
                    TextButton(onClick = onMarkUnread) {
                        Text(text = stringResource(R.string.notifications_dialog_mark_unread))
                    }
                }
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.notifications_dialog_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}
