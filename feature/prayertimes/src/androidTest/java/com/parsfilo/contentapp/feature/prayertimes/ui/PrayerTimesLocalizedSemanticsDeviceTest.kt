package com.parsfilo.contentapp.feature.prayertimes.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerTimesDay
import com.parsfilo.contentapp.feature.prayertimes.ui.components.HeroHeaderCard
import com.parsfilo.contentapp.feature.prayertimes.ui.components.NextPrayerCountdownCard
import com.parsfilo.contentapp.feature.prayertimes.ui.components.PrayerTimesListSection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Locale

/** Exercises actual Compose semantics, without changing device locale or scheduling alarms. */
@RunWith(AndroidJUnit4::class)
class PrayerTimesLocalizedSemanticsDeviceTest {
    @get:Rule val rule = createComposeRule()

    private fun render(language: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.parsfilo.contentapp.feature.prayertimes.test", base.packageName)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        val localizedContext = base.createConfigurationContext(configuration)
        rule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
            ) {
                AppTheme(flavorName = "namazvakitleri") { content() }
            }
        }
    }

    private fun header(language: String, clockLabel: String, locationLabel: String) {
        val time = SimpleDateFormat("HH:mm", Locale.US).format(0L)
        render(language) {
            HeroHeaderCard(
                currentTimeMillis = 0L,
                hijriDateText = "1 Muharram",
                gregorianDateText = "1 Jan",
                locationName = "Test City",
                onLocationClick = {},
                onOpenAlarmSettings = {},
                onOpenQibla = {},
            )
        }
        rule.onNodeWithContentDescription("$clockLabel $time").assertExists()
        rule.onNodeWithContentDescription("$locationLabel Test City").assertHasClickAction()
    }

    @Test fun englishHeaderHasLocalizedSpeech() = header("en-US", "Time", "Location:")

    @Test fun germanHeaderHasLocalizedSpeech() = header("de-DE", "Uhrzeit", "Standort:")

    private fun countdown(language: String, expected: String) {
        render(language) {
            NextPrayerCountdownCard(
                nextPrayerLabel = "Dhuhr",
                nextPrayerTime = "12:30",
                countdown = "01:23",
            )
        }
        rule.onNodeWithContentDescription(expected).assertExists()
    }

    @Test fun englishCountdownHasLocalizedSpeech() = countdown("en-US", "Remaining: 01:23")

    @Test fun germanCountdownHasLocalizedSpeech() = countdown("de-DE", "Verbleibend: 01:23")

    private fun rows(language: String, expected: String) {
        render(language) {
            PrayerTimesListSection(
                variant = PrayerAppVariant.NAMAZ_VAKITLERI,
                upcomingDays = listOf(
                    PrayerTimesDay(
                        localDate = "2026-10-31",
                        imsak = "05:30",
                        gunes = "07:05",
                        ogle = "12:30",
                        ikindi = "15:35",
                        aksam = "18:00",
                        yatsi = "19:30",
                    ),
                ),
                selectedAlarmPrayerKeys = emptySet(),
                onToggleAlarm = {},
            )
        }
        rule.onNodeWithContentDescription(expected).assertExists()
        rule.onNodeWithContentDescription("ogle namazı vakti 12 30").assertDoesNotExist()
    }

    @Test fun englishPrayerRowsUseVisibleNames() = rows("en-US", "Time for Dhuhr: 12:30")

    @Test fun germanPrayerRowsUseVisibleNames() = rows("de-DE", "Zeit für Dhuhr: 12:30")

    private fun alarm(language: String, enabled: Boolean, expected: String) {
        var clickedPrayer: String? = null
        render(language) {
            Column {
                PrayerTimesListSection(
                    variant = PrayerAppVariant.NAMAZ_VAKITLERI,
                    upcomingDays = listOf(
                        PrayerTimesDay(
                            localDate = "2026-10-31",
                            imsak = "05:30",
                            gunes = "07:05",
                            ogle = "12:30",
                            ikindi = "15:35",
                            aksam = "18:00",
                            yatsi = "19:30",
                        ),
                    ),
                    // Empty means all selected; a set of Imsak alone leaves Dhuhr off.
                    selectedAlarmPrayerKeys = if (enabled) emptySet() else setOf("imsak"),
                    onToggleAlarm = { clickedPrayer = it },
                )
            }
        }
        rule.onNodeWithContentDescription(expected).assertHasClickAction().performClick()
        rule.runOnIdle { assertEquals("ogle", clickedPrayer) }
    }

    @Test fun englishEnabledAlarmIdentifiesPrayer() =
        alarm("en-US", true, "Dhuhr reminder on")

    @Test fun englishDisabledAlarmIdentifiesPrayer() =
        alarm("en-US", false, "Dhuhr reminder off")

    @Test fun germanEnabledAlarmIdentifiesPrayer() =
        alarm("de-DE", true, "Erinnerung für Dhuhr an")

    @Test fun germanDisabledAlarmIdentifiesPrayer() =
        alarm("de-DE", false, "Erinnerung für Dhuhr aus")
}
