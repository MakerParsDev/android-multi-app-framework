package com.parsfilo.contentapp.feature.notifications.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.parsfilo.contentapp.core.common.NotificationIntentKeys
import com.parsfilo.contentapp.core.database.dao.NotificationDao
import com.parsfilo.contentapp.core.database.model.NotificationEntity
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import com.parsfilo.contentapp.core.firebase.logPushReceived
import com.parsfilo.contentapp.core.firebase.push.PushRegistrationManager
import com.parsfilo.contentapp.feature.notifications.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AppFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationDao: NotificationDao

    @Inject
    lateinit var analytics: AppAnalytics

    @Inject
    lateinit var pushRegistrationManager: PushRegistrationManager

    @Inject
    lateinit var preferencesDataSource: PreferencesDataSource

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        private const val CHANNEL_ID = "app_notifications"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.d(
            "FCM message received id=%s from=%s hasNotification=%s dataKeys=%s",
            remoteMessage.messageId,
            remoteMessage.from,
            remoteMessage.notification != null,
            remoteMessage.data.keys.joinToString(","),
        )
        val notificationId = remoteMessage.messageId ?: System.currentTimeMillis().toString()
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: ""
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: ""
        val imageUrl = remoteMessage.notification?.imageUrl?.toString() ?: remoteMessage.data["image"]
        val type = remoteMessage.data["type"]
        val timestamp = remoteMessage.sentTime

        val entity = NotificationEntity(
            notificationId = notificationId,
            title = title,
            body = body,
            imageUrl = imageUrl,
            type = type,
            isRead = false,
            timestamp = if (timestamp > 0) timestamp else System.currentTimeMillis(),
            dataPayloadJson = remoteMessage.data.toString()
        )

        // Analytics: push_received
        analytics.logPushReceived(type)

        scope.launch {
            val rowId = persistNotification(entity)
            if (shouldShowSystemNotification()) {
                Timber.d(
                    "FCM will show system notification externalId=%s rowId=%s",
                    notificationId,
                    rowId,
                )
                showNotification(
                    title = title,
                    body = body,
                    id = notificationId.hashCode(),
                    notificationRowId = rowId,
                    notificationExternalId = notificationId,
                )
            } else {
                Timber.d("Skipping system notification (permission/settings disabled).")
            }
        }
    }

    private suspend fun persistNotification(entity: NotificationEntity): Long? {
        val inserted = notificationDao.insertNotification(entity)
        if (inserted > 0L) {
            Timber.d("Notification persisted insertId=%d externalId=%s", inserted, entity.notificationId)
            return inserted
        }
        val existingId = notificationDao.getByNotificationId(entity.notificationId)?.id
        Timber.d(
            "Notification upsert fallback externalId=%s resolvedRowId=%s",
            entity.notificationId,
            existingId,
        )
        return existingId
    }

    private suspend fun shouldShowSystemNotification(): Boolean {
        val notificationsEnabled = preferencesDataSource.userData.first().notificationsEnabled
        if (!notificationsEnabled) {
            Timber.d("System notification blocked: user preference disabled")
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Timber.d("System notification allowed: pre-Tiramisu device")
            return true
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        Timber.d("System notification permission granted=%s", hasPermission)
        return hasPermission
    }

    private fun showNotification(
        title: String,
        body: String,
        id: Int,
        notificationRowId: Long?,
        notificationExternalId: String,
    ) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notifications_title),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notifications_empty_hint)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent to open app
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            action = NotificationIntentKeys.ACTION_OPEN_NOTIFICATIONS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(NotificationIntentKeys.EXTRA_OPEN_NOTIFICATIONS, true)
            notificationRowId?.let {
                putExtra(NotificationIntentKeys.EXTRA_NOTIFICATION_ROW_ID, it)
            }
            putExtra(NotificationIntentKeys.EXTRA_NOTIFICATION_EXTERNAL_ID, notificationExternalId)
        }
        val pendingIntent = intent?.let {
            PendingIntent.getActivity(
                this, id, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        // IMPORTANT: Notification small icon must be a solid, monochrome icon.
        // Using the launcher icon may show a warning/exclamation on some OEM skins.
        val smallIconRes = R.drawable.ic_stat_notification

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setContentTitle(title.ifBlank { getString(R.string.notifications_title) })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply { pendingIntent?.let { setContentIntent(it) } }
            .build()

        Timber.d(
            "Showing system notification id=%d channel=%s hasPendingIntent=%s",
            id,
            CHANNEL_ID,
            pendingIntent != null,
        )
        notificationManager.notify(id, notification)
    }

    override fun onNewToken(token: String) {
        Timber.d("FCM token refreshed length=%d", token.length)
        scope.launch {
            pushRegistrationManager.syncRegistration(
                reason = "token_refresh",
                tokenOverride = token
            )
        }
    }

    override fun onDestroy() {
        Timber.d("AppFirebaseMessagingService destroyed")
        job.cancel()
        super.onDestroy()
    }
}
