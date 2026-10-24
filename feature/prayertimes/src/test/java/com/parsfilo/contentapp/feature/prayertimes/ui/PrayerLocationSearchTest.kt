package com.parsfilo.contentapp.feature.prayertimes.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PrayerLocationSearchTest {
    @Test fun `turkiye matches canonical country without changing its name`() {
        val canonical = listOf("TÜRKİYE", "ALMANYA")
        assertThat(PrayerLocationSearch.filter(canonical, "turkiye") { it }).containsExactly("TÜRKİYE")
        assertThat(canonical[0]).isEqualTo("TÜRKİYE")
        assertThat(PrayerLocationSearch.filter(canonical, "TÜRKİYE") { it }).containsExactly("TÜRKİYE")
        assertThat(PrayerLocationSearch.filter(canonical, "  TURKIYE  ") { it }).containsExactly("TÜRKİYE")
    }

    @Test fun `all Turkish diacritics and both dotted i variants fold predictably`() {
        assertThat(PrayerLocationSearch.normalize("ÇANKIRI")).isEqualTo("cankiri")
        assertThat(PrayerLocationSearch.normalize("ŞİŞLİ")).isEqualTo("sisli")
        assertThat(PrayerLocationSearch.normalize("GÖLCÜK")).isEqualTo("golcuk")
        assertThat(PrayerLocationSearch.normalize("IĞDIR")).isEqualTo("igdir")
        assertThat(PrayerLocationSearch.normalize("ısparta")).isEqualTo("isparta")
        assertThat(PrayerLocationSearch.normalize("ÜSKÜDAR")).isEqualTo("uskudar")
        assertThat(PrayerLocationSearch.normalize("İZMİR")).isEqualTo("izmir")
    }

    @Test fun `decomposed Unicode and ASCII keyboards produce the same query`() {
        assertThat(PrayerLocationSearch.normalize("I\u0307zmir")).isEqualTo("izmir")
        assertThat(PrayerLocationSearch.normalize("C\u0327ANKIRI")).isEqualTo("cankiri")
        assertThat(PrayerLocationSearch.normalize("şişli")).isEqualTo("sisli")
        assertThat(PrayerLocationSearch.filter(listOf("ÇANKIRI", "ÇORUM"), "cankiri") { it })
            .containsExactly("ÇANKIRI")
    }

    @Test fun `no matches and blank query preserve source ordering`() {
        val canonical = listOf("TÜRKİYE", "ÇANKIRI", "IĞDIR")
        assertThat(PrayerLocationSearch.filter(canonical, "missing") { it }).isEmpty()
        assertThat(PrayerLocationSearch.filter(canonical, "  ") { it }).containsExactlyElementsIn(canonical).inOrder()
        assertThat(PrayerLocationSearch.filter(canonical, "i") { it }).containsExactlyElementsIn(canonical).inOrder()
    }
}
