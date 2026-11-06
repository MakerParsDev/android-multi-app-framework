package com.parsfilo.contentapp.di

import com.parsfilo.contentapp.product.ContentFamily
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioStartupPolicyTest {
    private val familiesWithoutAudioService =
        setOf(
            ContentFamily.MIRACLES,
            ContentFamily.QIBLA,
            ContentFamily.PRAYER_TIMES,
            ContentFamily.ZIKIR_COUNTER,
        )

    @Test
    fun normalBuildsPreserveAudioStartupOnlyWhereMediaServiceExists() {
        ContentFamily.entries.forEach { family ->
            val enabled = shouldStartAudio(ciSmoke = false, buildType = "release", family = family)
            if (family in familiesWithoutAudioService) assertFalse(enabled) else assertTrue(enabled)
        }
    }

    @Test
    fun utilityFlavorsNeverStartTheMissingMediaService() {
        for (family in familiesWithoutAudioService) {
            for (smoke in listOf(false, true)) {
                for (buildType in listOf("debug", "release", "nonMinifiedRelease")) {
                    assertFalse(
                        "AudioService was removed from the $family manifest",
                        shouldStartAudio(ciSmoke = smoke, buildType = buildType, family = family),
                    )
                }
            }
        }
    }

    @Test
    fun quranStreamingRetainsAudioStartupWithoutBundledMp3() {
        for (buildType in listOf("debug", "release", "nonMinifiedRelease")) {
            assertTrue(shouldStartAudio(ciSmoke = false, buildType = buildType, family = ContentFamily.QURAN))
        }
    }

    @Test
    fun smokeBuildsKeepAudioDisabledExceptForAudioBenchmarks() {
        ContentFamily.entries.forEach { family ->
            assertFalse(shouldStartAudio(ciSmoke = true, buildType = "debug", family = family))
            assertFalse(shouldStartAudio(ciSmoke = true, buildType = "benchmarkRelease", family = family))
        }
        val enabled = setOf(ContentFamily.CONTENT, ContentFamily.PRAYER_LIBRARY, ContentFamily.ESMA)
        ContentFamily.entries.forEach { family ->
            val actual = shouldStartAudio(ciSmoke = true, buildType = "nonMinifiedRelease", family = family)
            if (family in enabled) assertTrue(actual) else assertFalse(actual)
        }
    }
}
