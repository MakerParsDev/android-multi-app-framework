package com.parsfilo.contentapp.feature.prayertimes.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.feature.prayertimes.R
import com.parsfilo.contentapp.feature.prayertimes.data.PrayerDateTime
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerTimesDay
import com.parsfilo.contentapp.feature.prayertimes.ui.components.PrayerTimesListSection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regression for cached days preceding today on a fresh Compose screen. */
@RunWith(AndroidJUnit4::class)
class PrayerTimesInitialDayDeviceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun startupShowsTodayWhilePreviousDaysRemainNavigable() {
        val today = PrayerDateTime.todayIso()
        val yesterday = PrayerDateTime.shiftIsoDate(today, -1)
        val older = PrayerDateTime.shiftIsoDate(today, -2)
        val next = PrayerDateTime.shiftIsoDate(today, 1)
        val dates = listOf(older, yesterday, today, next)
        val days = dates.map { date ->
            PrayerTimesDay(
                localDate = date,
                imsak = "05:30",
                gunes = "06:45",
                ogle = "12:30",
                ikindi = "15:30",
                aksam = "18:00",
                yatsi = "19:20",
            )
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousLabel = context.getString(R.string.prayertimes_previous_day)
        val nextLabel = context.getString(R.string.prayertimes_next_day)

        rule.setContent {
            AppTheme(flavorName = "namazvakitleri") {
                Column {
                    PrayerTimesListSection(
                        variant = PrayerAppVariant.NAMAZ_VAKITLERI,
                        upcomingDays = days,
                        selectedAlarmPrayerKeys = emptySet(),
                        onToggleAlarm = {},
                    )
                }
            }
        }

        rule.onNodeWithText(today).assertExists()
        rule.onNodeWithText(older).assertDoesNotExist()
        rule.onNodeWithContentDescription(previousLabel).performClick()
        rule.onNodeWithText(yesterday).assertExists()
        rule.onNodeWithContentDescription(nextLabel).performClick()
        rule.onNodeWithText(today).assertExists()
    }
}
