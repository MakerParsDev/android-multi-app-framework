package com.parsfilo.contentapp.feature.prayertimes.data

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerCity
import org.junit.Test

class PrayerLocationMatcherTest {

    @Test
    fun `normalize handles Turkish characters`() {
        val normalized = PrayerLocationMatcher.normalize("İNGİLTERE-Şehir")
        assertThat(normalized).isEqualTo("ingiltere sehir")
    }

    @Test
    fun `bestMatch matches english alias for UK sample`() {
        val cities = listOf(
            PrayerCity(id = 1, countryId = 15, nameTr = "INGILTERE", nameEn = "UNITED KINGDOM"),
            PrayerCity(id = 2, countryId = 15, nameTr = "ALMANYA", nameEn = "GERMANY"),
        )

        val match = PrayerLocationMatcher.bestMatch(
            input = "United Kingdom",
            items = cities,
            trName = { it.nameTr },
            enName = { it.nameEn },
        )

        assertThat(match?.id).isEqualTo(1)
    }
}
