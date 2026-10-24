package com.parsfilo.contentapp.feature.prayertimes.alarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Run arm and cleanup in separate instrumented invocations around an isolated AVD reboot. */
@RunWith(AndroidJUnit4::class)
class PrayerAlarmLifecycleProbeDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().context

    // Manual orchestration hooks, not standalone acceptance claims. The default
    // instrumentation suite runs no alarm, reboot or timezone operation at all.
    private fun manualQaEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("amf497LifecycleManual") == "true"

    @Test
    fun armForIsolatedSystemLifecycleCheck() {
        if (!manualQaEnabled()) return
        assertFalse(context.packageName == "com.parsfilo.namazvakitleri")
        val prefs = context.getSharedPreferences(
            PrayerAlarmLifecycleProbeReceiver.PREFS_NAME,
            android.content.Context.MODE_PRIVATE,
        )
        prefs.edit().putBoolean(PrayerAlarmLifecycleProbeReceiver.KEY_ARMED, true).commit()
        PrayerAlarmLifecycleProbeReceiver.arm(context, TimeUnit.MINUTES.toMillis(10))
    }

    @Test
    fun armForActualBroadcastDeliveryCheck() {
        if (!manualQaEnabled()) return
        assertFalse(context.packageName == "com.parsfilo.namazvakitleri")
        val prefs = context.getSharedPreferences(
            PrayerAlarmLifecycleProbeReceiver.PREFS_NAME,
            android.content.Context.MODE_PRIVATE,
        )
        prefs.edit().putBoolean(PrayerAlarmLifecycleProbeReceiver.KEY_FIRED, false).commit()
        PrayerAlarmLifecycleProbeReceiver.arm(context, TimeUnit.SECONDS.toMillis(8))
    }

    @Test
    fun cleanupIsolatedSystemLifecycleCheck() {
        if (!manualQaEnabled()) return
        assertFalse(context.packageName == "com.parsfilo.namazvakitleri")
        PrayerAlarmLifecycleProbeReceiver.cancel(context)
    }
}
