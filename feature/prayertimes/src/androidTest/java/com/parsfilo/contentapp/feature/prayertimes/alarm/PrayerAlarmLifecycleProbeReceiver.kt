package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Isolated system-lifecycle probe installed ONLY in the Android instrumentation APK.
 * Does not request user permissions or access any prayer, location or preference data.
 */
class PrayerAlarmLifecycleProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            ACTION_FIRE -> {
                Log.i(LOG_TAG, "FIRE_RECEIVED testPackage=${context.packageName}")
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_FIRED, true).apply()
            }

            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED -> {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                if (!prefs.getBoolean(KEY_ARMED, false)) return
                Log.i(LOG_TAG, "LIFECYCLE_RECEIVED action=$action package=${context.packageName}")
                arm(context, TimeUnit.MINUTES.toMillis(10))
            }
        }
    }

    companion object {
        internal const val LOG_TAG = "AMF497_LIFECYCLE"
        internal const val ACTION_FIRE = "com.parsfilo.amf497.qa.LIFECYCLE_FIRE"
        internal const val PREFS_NAME = "amf497_isolated_alarm_probe"
        internal const val KEY_ARMED = "armed"
        internal const val KEY_FIRED = "fired"
        private const val REQUEST_CODE = 497001

        private fun token(context: Context, flags: Int): PendingIntent? =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, PrayerAlarmLifecycleProbeReceiver::class.java).apply {
                    action = ACTION_FIRE
                },
                PendingIntent.FLAG_IMMUTABLE or flags,
            )

        internal fun arm(context: Context, delayMillis: Long) {
            val operation = requireNotNull(token(context, PendingIntent.FLAG_UPDATE_CURRENT))
            context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + delayMillis,
                operation,
            )
            Log.i(LOG_TAG, "ALARM_ARMED delayMs=$delayMillis testPackage=${context.packageName}")
        }

        internal fun cancel(context: Context) {
            token(context, PendingIntent.FLAG_NO_CREATE)?.let {
                context.getSystemService(AlarmManager::class.java).cancel(it)
                it.cancel()
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
            Log.i(LOG_TAG, "ALARM_CLEANED testPackage=${context.packageName}")
        }
    }
}
