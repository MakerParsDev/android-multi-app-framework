package com.parsfilo.contentapp.feature.audio.service

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColdMediaButtonSessionPolicyTest {
    @Test
    fun coldMediaButtonWithoutQueuedAudioMustNotStartAnUnplayableSession() {
        assertFalse(shouldExposeSessionForMediaButton(Intent.ACTION_MEDIA_BUTTON, mediaItemCount = 0))
    }

    @Test
    fun normalMediaControllerCanConnectBeforeAudioIsLoaded() {
        assertTrue(shouldExposeSessionForMediaButton(null, mediaItemCount = 0))
    }

    @Test
    fun mediaButtonCanUseAnExistingPlayableSession() {
        assertTrue(shouldExposeSessionForMediaButton(Intent.ACTION_MEDIA_BUTTON, mediaItemCount = 1))
    }

    @Test
    fun coldMediaButtonMayConnectWhenLocalResumptionIsAvailable() {
        assertTrue(
            shouldExposeSessionForMediaButton(
                Intent.ACTION_MEDIA_BUTTON,
                mediaItemCount = 0,
                hasResumableMedia = true,
            ),
        )
    }

    @Test
    fun coldMediaButtonWithoutLocalResumptionStillStopsCleanly() {
        assertFalse(
            shouldExposeSessionForMediaButton(
                Intent.ACTION_MEDIA_BUTTON,
                mediaItemCount = 0,
                hasResumableMedia = false,
            ),
        )
    }

    @Test
    fun otherConnectionHintTypesKeepTheirExistingBehavior() {
        assertTrue(shouldExposeSessionForMediaButton("android.media.browse.MediaBrowserService", 0))
    }
}
