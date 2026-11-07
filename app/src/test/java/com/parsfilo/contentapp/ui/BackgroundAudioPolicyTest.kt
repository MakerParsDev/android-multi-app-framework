package com.parsfilo.contentapp.ui

import com.parsfilo.contentapp.product.AppProductDefinition
import com.parsfilo.contentapp.product.ContentFamily
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundAudioPolicyTest {
    @Test
    fun contentAudioAppsPreserveUserInitiatedPlaybackWhenBackgrounded() {
        listOf(
            ContentFamily.CONTENT,
            ContentFamily.PRAYER_LIBRARY,
            ContentFamily.ESMA,
        ).forEach { family ->
            assertFalse(
                "Active mediaPlayback in $family must survive ON_STOP",
                shouldStopPlaybackOnAppBackground(product(family, "recitation.mp3"), false),
            )
        }
    }

    @Test
    fun quranRecitationPreservesBackgroundPlaybackWithoutBundledAudioFile() {
        assertFalse(
            shouldStopPlaybackOnAppBackground(product(ContentFamily.QURAN, null), false),
        )
    }

    @Test
    fun utilityFlavorsStillStopThePlayerWhenBackgrounded() {
        listOf(
            ContentFamily.MIRACLES,
            ContentFamily.QIBLA,
            ContentFamily.PRAYER_TIMES,
            ContentFamily.ZIKIR_COUNTER,
            ContentFamily.UNKNOWN,
        ).forEach { family ->
            assertTrue(
                "Non-audio $family should retain ON_STOP cleanup",
                shouldStopPlaybackOnAppBackground(product(family, null), false),
            )
        }
    }

    @Test
    fun configurationChangesNeverStopPlaybackForAnyFlavor() {
        ContentFamily.entries.forEach { family ->
            assertFalse(
                "Configuration change must not reset $family",
                shouldStopPlaybackOnAppBackground(product(family, null), true),
            )
        }
    }

    private fun product(family: ContentFamily, audioFileName: String?): AppProductDefinition =
        AppProductDefinition(
            flavorId = "test",
            packageName = "com.parsfilo.test",
            displayName = "Test",
            contentFamily = family,
            monetizationProfile = "test",
            notificationProfile = "test",
            billingProfile = "test",
            themeTokenKey = "test",
            audioFileName = audioFileName,
            useAssetPackAudio = false,
            capabilityFlags = emptySet(),
        )
}
