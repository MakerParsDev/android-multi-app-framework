package com.parsfilo.contentapp.feature.quran.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.QuranAyah
import com.parsfilo.contentapp.core.model.QuranDisplayMode
import com.parsfilo.contentapp.feature.quran.R
import com.parsfilo.contentapp.feature.quran.config.QuranApiConfig
import com.parsfilo.contentapp.feature.quran.ui.suradetail.AyahDisplayState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic UI only: no personal Quran database, remote audio or original installed app. */
@RunWith(AndroidJUnit4::class)
class QuranReaderAccessibilityDeviceTest {
    @get:Rule val rule = createComposeRule()

    private val ayah = QuranAyah(
        suraNumber = 2,
        ayahNumber = 1,
        arabic = "Synthetic Arabic",
        latin = "Synthetic Latin",
        turkish = "Synthetic Turkish",
        english = "Synthetic English",
        german = "Synthetic German",
    )

    private fun verifyHost() {
        assertEquals(
            "com.parsfilo.contentapp.feature.quran.test",
            InstrumentationRegistry.getInstrumentation().targetContext.packageName,
        )
    }

    @Test
    fun ayahControlsExposeNamesAndReactToCurrentBookmarkPlaybackAndDownloadState() {
        verifyHost()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        var state by mutableStateOf(
            AyahDisplayState(
                ayah = ayah,
                isBookmarked = false,
                isAudioDownloaded = false,
                isAudioDownloading = false,
                audioDownloadProgress = 0f,
                isCurrentlyPlaying = false,
            )
        )
        val actions = mutableListOf<String>()
        rule.setContent {
            AppTheme(flavorName = "kuran_kerim") {
                AyahItem(
                    state = state,
                    displayMode = QuranDisplayMode.ARABIC,
                    translatedText = "",
                    fontSize = 26,
                    onPlayPauseClick = { actions += "play" },
                    onDownloadClick = { actions += "download" },
                    onBookmarkClick = { actions += "bookmark" },
                    onLongClick = { actions += "actions" },
                )
            }
        }
        rule.onNodeWithTag("quran_ayah_1_bookmark")
            .assertContentDescriptionEquals(ctx.getString(R.string.quran_ayah_bookmark_add, 1))
            .performClick()
        rule.onNodeWithTag("quran_ayah_1_play")
            .assertContentDescriptionEquals(ctx.getString(R.string.quran_ayah_play, 1))
            .performClick()
        rule.onNodeWithTag("quran_ayah_1_download")
            .assertContentDescriptionEquals(ctx.getString(R.string.quran_ayah_download, 1))
            .performClick()
        rule.onNodeWithTag("quran_ayah_1_card")
            .assertHasClickAction()
            .performClick()
        rule.runOnIdle {
            assertEquals(listOf("bookmark", "play", "download", "actions"), actions)
            state = state.copy(
                isBookmarked = true,
                isCurrentlyPlaying = true,
                isAudioDownloaded = true,
            )
        }
        rule.onNodeWithTag("quran_ayah_1_bookmark")
            .assertContentDescriptionEquals(ctx.getString(R.string.quran_ayah_bookmark_remove, 1))
        rule.onNodeWithTag("quran_ayah_1_play")
            .assertContentDescriptionEquals(ctx.getString(R.string.quran_ayah_pause, 1))
        rule.onNodeWithTag("quran_ayah_1_download")
            .assertContentDescriptionEquals(ctx.getString(R.string.quran_ayah_downloaded, 1))
    }

    @Test
    fun reciterIsAnActualMutuallyExclusiveRadioChoice() {
        verifyHost()
        val reciters = QuranApiConfig.Reciters.ALL.take(2)
        var selected by mutableStateOf(reciters[0].id)
        rule.setContent {
            AppTheme(flavorName = "kuran_kerim") {
                ReciterSelectionSheet(
                    reciters = reciters,
                    selectedReciterId = selected,
                    onSelect = { selected = it },
                    onDismiss = {},
                )
            }
        }
        val radioRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        rule.onNodeWithText(reciters[0].displayName + " ✓").assert(radioRole).assertIsSelected()
        rule.onNodeWithText(reciters[1].displayName).assert(radioRole).assertIsNotSelected().performClick()
        rule.onNodeWithText(reciters[1].displayName + " ✓").assert(radioRole).assertIsSelected()
        rule.onNodeWithText(reciters[0].displayName).assert(radioRole).assertIsNotSelected()
        rule.runOnIdle { assertEquals(reciters[1].id, selected) }
    }
}
