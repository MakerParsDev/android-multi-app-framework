package com.parsfilo.contentapp.feature.audio.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.Player
import com.google.android.play.core.assetpacks.AssetPackManager
import com.parsfilo.contentapp.core.firebase.config.EndpointsProvider
import dagger.Lazy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlayerBackgroundTest {
    @Test
    fun playingSessionIsNeverPausedOrRewoundOnActivityStop() {
        val (viewModel, player) = viewModelWithPlayer(isPlaying = true, positionMs = 4_000L)

        viewModel.stopForAppBackground()

        verify(exactly = 0) { player.pause() }
        verify(exactly = 0) { player.seekTo(0L) }
        assertTrue(player.isPlaying)
    }

    @Test
    fun pausedSessionRetainsOldBackgroundReset() {
        val (viewModel, player) = viewModelWithPlayer(isPlaying = false, positionMs = 4_000L)

        viewModel.stopForAppBackground()

        verify(exactly = 1) { player.pause() }
        verify(exactly = 1) { player.seekTo(0L) }
        assertFalse(viewModel.playerState.value.isPlaying)
        assertEquals(4_000L, viewModel.playerState.value.currentPosition)
    }

    @Test
    fun alreadyStoppedSessionDoesNotSeekOrPauseAgain() {
        val (viewModel, player) = viewModelWithPlayer(isPlaying = false, positionMs = 0L)

        viewModel.stopForAppBackground()

        verify(exactly = 0) { player.pause() }
        verify(exactly = 0) { player.seekTo(0L) }
        assertFalse(viewModel.playerState.value.isPlaying)
    }

    @Test
    fun missingControllerIsSafeDuringUtilityStartup() {
        val viewModel = newViewModel()
        viewModel.stopForAppBackground()
        assertFalse(viewModel.playerState.value.isPlaying)
    }

    private fun viewModelWithPlayer(isPlaying: Boolean, positionMs: Long): Pair<AudioPlayerViewModel, Player> {
        val viewModel = newViewModel()
        val player = mockk<Player>(relaxed = true)
        every { player.isPlaying } returns isPlaying
        every { player.currentPosition } returns positionMs
        every { player.duration } returns 35_000L
        every { player.playbackState } returns Player.STATE_READY
        val field = AudioPlayerViewModel::class.java.getDeclaredField("player")
        field.isAccessible = true
        field.set(viewModel, player)
        return viewModel to player
    }

    private fun newViewModel(): AudioPlayerViewModel =
        AudioPlayerViewModel(
            context = mockk<Context>(),
            assetPackManagerProvider = Lazy { mockk<AssetPackManager>() },
            endpointsProvider = mockk<EndpointsProvider>(),
            okHttpClient = OkHttpClient(),
            audioFileName = "amenerrasulu.mp3",
            useAssetPackAudio = false,
            audioStartupEnabled = false,
            savedStateHandle = SavedStateHandle(),
        )
}
