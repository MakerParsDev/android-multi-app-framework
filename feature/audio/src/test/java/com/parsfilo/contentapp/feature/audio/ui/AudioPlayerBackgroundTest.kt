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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun playPauseKeepsUiStateInSyncWithoutMediaControllerCallbacks() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (viewModel, player) = viewModelWithPlayer(isPlaying = false, positionMs = 0L)
            var playing = false
            var positionMs = 0L
            every { player.isPlaying } answers { playing }
            every { player.currentPosition } answers { positionMs }
            every { player.play() } answers { playing = true }
            every { player.pause() } answers { playing = false }

            // No MediaController listener callback is dispatched in this test.
            viewModel.togglePlayPause()
            assertTrue(viewModel.playerState.value.isPlaying)

            positionMs = 2_000L
            advanceTimeBy(600L)
            runCurrent()
            assertEquals(2_000L, viewModel.playerState.value.currentPosition)

            viewModel.togglePlayPause()
            assertFalse(viewModel.playerState.value.isPlaying)
            verify(exactly = 1) { player.play() }
            verify(exactly = 1) { player.pause() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun directPlayAndPauseAlsoUpdateUiWithoutMediaControllerCallbacks() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (viewModel, player) = viewModelWithPlayer(isPlaying = false, positionMs = 0L)
            var playing = false
            every { player.isPlaying } answers { playing }
            every { player.play() } answers { playing = true }
            every { player.pause() } answers { playing = false }

            viewModel.play()
            assertTrue(viewModel.playerState.value.isPlaying)
            viewModel.pause()
            assertFalse(viewModel.playerState.value.isPlaying)
        } finally {
            Dispatchers.resetMain()
        }
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
