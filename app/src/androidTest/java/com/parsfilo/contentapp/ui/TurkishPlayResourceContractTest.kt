package com.parsfilo.contentapp.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import com.parsfilo.contentapp.feature.audio.R as AudioR
import com.parsfilo.contentapp.feature.billing.R as BillingR
import com.parsfilo.contentapp.feature.content.R as ContentR
import com.parsfilo.contentapp.feature.counter.R as CounterR
import com.parsfilo.contentapp.feature.notifications.R as NotificationsR
import com.parsfilo.contentapp.feature.prayertimes.R as PrayerTimesR
import com.parsfilo.contentapp.feature.qibla.R as QiblaR

/**
 * Verify the actual merged app resources, including localized release overrides.
 *
 * A prior Play build exposed English values under its Turkish configuration,
 * even though the shared feature modules' default string files were Turkish.
 * Do not alter the device or the app's saved language preference to test this.
 */
@RunWith(AndroidJUnit4::class)
class TurkishPlayResourceContractTest {
    @Test
    fun turkishResourcesPreserveReaderAndSafetyCopy() {
        val context = contextForLocale("tr-TR")

        assertEquals("Okuma araçları ve ses", context.getString(ContentR.string.content_reader_tools))
        assertEquals("Durdur", context.getString(AudioR.string.audio_stop))
        assertEquals("Ses kaydında ilerleme", context.getString(AudioR.string.audio_seek))
        assertEquals("Otomatik yenilenmez", context.getString(BillingR.string.billing_feature_cancel_anytime))
        assertEquals("Google Play ile satın al", context.getString(BillingR.string.billing_subscribe))
        assertEquals(
            "Konum doğrulanmadı; varsayılan bölge yönlendirme için kullanılmıyor.",
            context.getString(QiblaR.string.qibla_location_fallback),
        )
        assertEquals(
            "Tüm bildirimler silinsin mi?",
            context.getString(NotificationsR.string.notifications_delete_all_confirm_title),
        )
        assertEquals(
            "Namaz Vakti Hatırlatmaları",
            context.getString(PrayerTimesR.string.prayer_alarm_channel_name),
        )
        assertEquals(
            "Kaydedilemedi! Son güvenli sayım: 5",
            context.getString(CounterR.string.counter_checkpoint_failed, 5),
        )
    }

    @Test
    fun englishReaderResourcesRemainEnglish() {
        val context = contextForLocale("en-US")
        assertEquals("Reading tools and audio", context.getString(ContentR.string.content_reader_tools))
        assertEquals("Stop", context.getString(AudioR.string.audio_stop))
        assertEquals("Audio playback position", context.getString(AudioR.string.audio_seek))
    }

    private fun contextForLocale(tag: String): Context {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(targetContext.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(tag)))
        }
        return targetContext.createConfigurationContext(configuration)
    }
}
