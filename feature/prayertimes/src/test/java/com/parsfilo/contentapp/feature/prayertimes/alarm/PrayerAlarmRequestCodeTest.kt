package com.parsfilo.contentapp.feature.prayertimes.alarm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PrayerAlarmRequestCodeTest {

    @Test
    fun known_prayer_keys_map_to_distinct_request_codes() {
        val mappings = listOf(
            PrayerAlarmScheduler.PRAYER_IMSAK to prayerAlarmRequestCodeFor(PrayerAlarmScheduler.PRAYER_IMSAK),
            PrayerAlarmScheduler.PRAYER_GUNES to prayerAlarmRequestCodeFor(PrayerAlarmScheduler.PRAYER_GUNES),
            PrayerAlarmScheduler.PRAYER_OGLE to prayerAlarmRequestCodeFor(PrayerAlarmScheduler.PRAYER_OGLE),
            PrayerAlarmScheduler.PRAYER_IKINDI to prayerAlarmRequestCodeFor(PrayerAlarmScheduler.PRAYER_IKINDI),
            PrayerAlarmScheduler.PRAYER_AKSAM to prayerAlarmRequestCodeFor(PrayerAlarmScheduler.PRAYER_AKSAM),
            PrayerAlarmScheduler.PRAYER_YATSI to prayerAlarmRequestCodeFor(PrayerAlarmScheduler.PRAYER_YATSI),
        )

        val codes = mappings.map { it.second }
        assertThat(codes.distinct().size).isEqualTo(codes.size)
        assertThat(codes).containsExactly(
            PrayerAlarmScheduler.REQUEST_CODE_IMSAK,
            PrayerAlarmScheduler.REQUEST_CODE_GUNES,
            PrayerAlarmScheduler.REQUEST_CODE_OGLE,
            PrayerAlarmScheduler.REQUEST_CODE_IKINDI,
            PrayerAlarmScheduler.REQUEST_CODE_AKSAM,
            PrayerAlarmScheduler.REQUEST_CODE_YATSI,
        )
    }

    @Test
    fun unknown_or_empty_key_falls_back_to_legacy_request_code() {
        assertThat(prayerAlarmRequestCodeFor(null)).isEqualTo(PrayerAlarmScheduler.LEGACY_REQUEST_CODE_ALARM)
        assertThat(prayerAlarmRequestCodeFor("")).isEqualTo(PrayerAlarmScheduler.LEGACY_REQUEST_CODE_ALARM)
        assertThat(prayerAlarmRequestCodeFor("unknown")).isEqualTo(PrayerAlarmScheduler.LEGACY_REQUEST_CODE_ALARM)
    }
}
