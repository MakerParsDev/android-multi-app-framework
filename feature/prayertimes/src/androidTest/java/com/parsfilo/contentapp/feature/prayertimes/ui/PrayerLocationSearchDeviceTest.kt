package com.parsfilo.contentapp.feature.prayertimes.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated device contract: no location permissions, saved selections or alarm APIs invoked. */
@RunWith(AndroidJUnit4::class)
class PrayerLocationSearchDeviceTest {
    @Test fun turkishUnicodeMatchesAsciiOnDevice() {
        assertEquals(
            listOf("TÜRKİYE"),
            PrayerLocationSearch.filter(listOf("TÜRKİYE", "ALMANYA"), "turkiye") { it },
        )
        assertEquals(
            listOf("ÇANKIRI"),
            PrayerLocationSearch.filter(listOf("ÇANKIRI", "ANKARA"), "cankiri") { it },
        )
        assertEquals("isparta", PrayerLocationSearch.normalize("ISPARTA"))
        assertEquals("izmir", PrayerLocationSearch.normalize("I\u0307zmir"))
    }
}
