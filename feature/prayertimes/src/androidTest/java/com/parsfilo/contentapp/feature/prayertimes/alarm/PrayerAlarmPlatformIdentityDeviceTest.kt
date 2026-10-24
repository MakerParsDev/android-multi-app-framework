package com.parsfilo.contentapp.feature.prayertimes.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Only a disposable module test package's alarm token is used and cancelled in finally. */
@RunWith(AndroidJUnit4::class)
class PrayerAlarmPlatformIdentityDeviceTest {
    @Test
    fun repeatedAlarmIdentityRemainsStableOnRealAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assertFalse(context.packageName == "com.parsfilo.namazvakitleri")
        val manager = context.getSystemService(AlarmManager::class.java)
        val alarmIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = PrayerAlarmScheduler.ACTION_FIRE_ALARM
        }
        val requestCode = PrayerAlarmScheduler.REQUEST_CODE_YATSI
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val token = PendingIntent.getBroadcast(context, requestCode, alarmIntent, flags)
        val firstTrigger = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(2)
        try {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, firstTrigger, token)
            assertNotNull(
                PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    alarmIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
                ),
            )
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, firstTrigger + TimeUnit.MINUTES.toMillis(5), token)
            assertEquals(
                token,
                PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    alarmIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
                ),
            )
        } finally {
            manager.cancel(token)
            token.cancel()
        }
    }
}
