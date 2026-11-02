package com.parsfilo.contentapp.di

import com.parsfilo.contentapp.product.ContentFamily
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioStartupPolicyTest {
    @Test
    fun normalBuildsPreserveAudioStartup() {
        ContentFamily.entries.forEach { family ->
            assertTrue(shouldStartAudio(ciSmoke = false, buildType = "release", family = family))
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
