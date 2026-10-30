package com.parsfilo.contentapp.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.feature.audio.ui.AudioPlayerState
import com.parsfilo.contentapp.feature.audio.ui.InlineAudioPlayer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Shared audio accessibility names follow the app language and playback state.
 * Only a synthetic state is used: no audio asset, MediaPlayer, network or playback is started.
 */
@RunWith(AndroidJUnit4::class)
class AudioPlayerLocalizedSemanticsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun turkishAudioActionsAtLargeText() =
        verifyAudio(
            locale = "tr-TR",
            play = "Oynat",
            pause = "Duraklat",
            stop = "Durdur",
            seek = "Ses kaydında ilerleme",
            missing = "Ses dosyası bulunamadı",
            retry = "Tekrar dene",
        )

    @Test
    fun englishAudioActionsAtLargeText() =
        verifyAudio(
            locale = "en-US",
            play = "Play",
            pause = "Pause",
            stop = "Stop",
            seek = "Audio playback position",
            missing = "Audio file not found",
            retry = "Retry",
        )

    @Test
    fun germanAudioActionsAtLargeText() =
        verifyAudio(
            locale = "de-DE",
            play = "Abspielen",
            pause = "Pausieren",
            stop = "Stoppen",
            seek = "Audiowiedergabeposition",
            missing = "Audiodatei nicht gefunden",
            retry = "Erneut versuchen",
        )

    private fun verifyAudio(
        locale: String,
        play: String,
        pause: String,
        stop: String,
        seek: String,
        missing: String,
        retry: String,
    ) {
        var state by mutableStateOf(AudioPlayerState(assetReady = true, duration = 20_000L))
        var playClicks = 0
        var stopClicks = 0
        var retryClicks = 0

        rule.setContent {
            val context = LocalContext.current
            val density = LocalDensity.current
            val config = Configuration(LocalConfiguration.current).apply {
                setLocales(LocaleList(Locale.forLanguageTag(locale)))
                screenWidthDp = 320
                fontScale = 2f
            }
            CompositionLocalProvider(
                LocalContext provides context.createConfigurationContext(config),
                LocalConfiguration provides config,
                LocalDensity provides Density(density.density, 2f),
            ) {
                AppTheme(flavorName = "fetihsuresi") {
                    Box(Modifier.width(320.dp)) {
                        InlineAudioPlayer(
                            state = state,
                            onPlayPause = {
                                playClicks++
                                state = state.copy(isPlaying = !state.isPlaying)
                            },
                            onStop = { stopClicks++ },
                            onSeek = {},
                            onRetry = { retryClicks++ },
                        )
                    }
                }
            }
        }

        rule.onNodeWithContentDescription(play).performClick()
        rule.onNodeWithContentDescription(pause).assertExists()
        rule.onNodeWithContentDescription(seek).assertExists()
        rule.onNodeWithContentDescription(stop).performClick()
        rule.onNodeWithContentDescription(pause).performClick()
        rule.onNodeWithContentDescription(play).assertExists()
        rule.runOnIdle {
            assertEquals(2, playClicks)
            assertEquals(1, stopClicks)
            assertEquals(0, retryClicks)
        }

        rule.runOnIdle { state = AudioPlayerState(assetReady = false) }
        rule.onNodeWithText(missing).assertExists()
        rule.onNodeWithContentDescription(retry).performClick()
        rule.runOnIdle {
            assertEquals(1, retryClicks)
            assertEquals(2, playClicks)
        }
    }
}
