package com.parsfilo.contentapp.feature.counter.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.feature.counter.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZikirReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataSource: PreferencesDataSource,
) {

    suspend fun showDailyReminder(todayTotalCount: Int) {
        val body = if (todayTotalCount <= 0) {
            context.getString(R.string.notif_reminder_body_no_activity)
        } else {
            context.resources.getQuantityString(
                R.plurals.notif_reminder_body_with_activity,
                todayTotalCount,
                todayTotalCount,
            )
        }
        showNotification(
            notificationId = 94001,
            title = context.getString(R.string.notif_reminder_title),
            body = body,
        )
    }

    suspend fun showStreakReminder(streak: Int) {
        showNotification(
            notificationId = 94002,
            title = context.getString(R.string.notif_streak_title),
            body = context.resources.getQuantityString(
                R.plurals.notif_streak_body,
                streak,
                streak,
            ),
        )
    }

    suspend fun showGoalCompleted(goal: Int) {
        showNotification(
            notificationId = 94003,
            title = context.getString(R.string.notif_goal_done_title),
            body = context.resources.getQuantityString(
                R.plurals.notif_goal_done_body,
                goal,
                goal,
            ),
        )
    }

    private suspend fun showNotification(
        notificationId: Int,
        title: String,
        body: String,
    ) {
        if (!shouldShowNotification()) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(manager)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                94010,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .apply {
                contentIntent?.let { setContentIntent(it) }
            }
            .build()

        manager.notify(notificationId, notification)
    }

    private suspend fun shouldShowNotification(): Boolean {
        if (!preferencesDataSource.userData.first().notificationsEnabled) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_reminder_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "zikir_reminders"
    }
}
