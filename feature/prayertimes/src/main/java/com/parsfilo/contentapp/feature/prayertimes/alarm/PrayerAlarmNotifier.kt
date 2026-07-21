package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.feature.prayertimes.R
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrayerAlarmNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataSource: PreferencesDataSource,
    private val prayerAlarmSoundPlayer: PrayerAlarmSoundPlayer,
) {

    suspend fun show(
        payload: PrayerAlarmPayload,
        variant: PrayerAppVariant,
        soundUri: String? = null,
    ) {
        if (!shouldShowNotification()) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

        ensureChannel(notificationManager)

        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = openAppIntent?.let {
            PendingIntent.getActivity(
                context,
                REQUEST_CODE_OPEN_APP,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val (title, body) = notificationContent(payload, variant)
        val largeIcon = BitmapFactory.decodeResource(context.resources, largeIconRes(payload.prayerKey))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_prayer)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_GROUP)
            .addAction(
                R.drawable.ic_stat_prayer,
                context.getString(R.string.prayer_alarm_action_open),
                contentIntent,
            )
            .apply { contentIntent?.let { setContentIntent(it) } }
            .build()

        notificationManager.notify(notificationId(payload.prayerKey), notification)
        notificationManager.notify(SUMMARY_NOTIFICATION_ID, buildSummary(notificationManager, contentIntent))
        prayerAlarmSoundPlayer.play(soundUri)
    }

    private fun buildSummary(
        notificationManager: NotificationManager,
        contentIntent: PendingIntent?,
    ): android.app.Notification {
        ensureChannel(notificationManager)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_prayer)
            .setContentTitle(context.getString(R.string.prayer_alarm_summary_title))
            .setContentText(context.getString(R.string.prayer_alarm_summary_desc))
            .setGroup(NOTIFICATION_GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply { contentIntent?.let { setContentIntent(it) } }
            .build()
    }

    private suspend fun shouldShowNotification(): Boolean {
        if (!preferencesDataSource.userData.first().notificationsEnabled) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.prayer_alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.prayer_alarm_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun notificationContent(
        payload: PrayerAlarmPayload,
        variant: PrayerAppVariant,
    ): Pair<String, String> {
        val prayerLabel = prayerLabel(payload.prayerKey)
        val timeText = payload.timeHm.ifBlank { timeFromMillis(payload.triggerAtMillis) }

        return when (variant) {
            PrayerAppVariant.NAMAZ_VAKITLERI -> {
                context.getString(R.string.prayer_alarm_generic_title, prayerLabel) to
                    context.getString(R.string.prayer_alarm_generic_body, prayerLabel, timeText)
            }
        }
    }

    private fun prayerLabel(prayerKey: String): String {
        return when (prayerKey) {
            PrayerAlarmScheduler.PRAYER_IMSAK -> context.getString(R.string.prayertimes_imsak)
            PrayerAlarmScheduler.PRAYER_GUNES -> context.getString(R.string.prayertimes_sabah_gunes)
            PrayerAlarmScheduler.PRAYER_OGLE -> context.getString(R.string.prayertimes_ogle)
            PrayerAlarmScheduler.PRAYER_IKINDI -> context.getString(R.string.prayertimes_ikindi)
            PrayerAlarmScheduler.PRAYER_AKSAM -> context.getString(R.string.prayertimes_aksam)
            PrayerAlarmScheduler.PRAYER_YATSI -> context.getString(R.string.prayertimes_yatsi)
            else -> prayerKey
        }
    }

    private fun timeFromMillis(value: Long): String {
        return SimpleDateFormat("HH:mm", Locale.forLanguageTag("tr-TR")).format(value)
    }

    private fun notificationId(prayerKey: String): Int = when (prayerKey) {
        PrayerAlarmScheduler.PRAYER_IMSAK -> 41_100
        PrayerAlarmScheduler.PRAYER_GUNES -> 41_105
        PrayerAlarmScheduler.PRAYER_OGLE -> 41_101
        PrayerAlarmScheduler.PRAYER_IKINDI -> 41_102
        PrayerAlarmScheduler.PRAYER_AKSAM -> 41_103
        PrayerAlarmScheduler.PRAYER_YATSI -> 41_104
        else -> 41_106
    }

    private fun largeIconRes(prayerKey: String): Int = when (prayerKey) {
        PrayerAlarmScheduler.PRAYER_IMSAK -> R.drawable.ic_prayer_imsak
        PrayerAlarmScheduler.PRAYER_GUNES -> R.drawable.ic_prayer_gunes
        PrayerAlarmScheduler.PRAYER_OGLE -> R.drawable.ic_prayer_ogle
        PrayerAlarmScheduler.PRAYER_IKINDI -> R.drawable.ic_prayer_ikindi
        PrayerAlarmScheduler.PRAYER_AKSAM -> R.drawable.ic_prayer_aksam
        PrayerAlarmScheduler.PRAYER_YATSI -> R.drawable.ic_prayer_yatsi
        else -> R.drawable.ic_stat_prayer
    }

    private companion object {
        private const val CHANNEL_ID = "prayer_times_alerts"
        private const val REQUEST_CODE_OPEN_APP = 41_032
        private const val NOTIFICATION_GROUP = "prayer_alarms"
        private const val SUMMARY_NOTIFICATION_ID = 41_099
    }
}
