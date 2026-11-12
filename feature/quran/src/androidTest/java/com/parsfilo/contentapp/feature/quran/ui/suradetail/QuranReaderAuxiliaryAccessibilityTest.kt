package com.parsfilo.contentapp.feature.quran.ui.suradetail

import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.feature.quran.R
import com.parsfilo.contentapp.feature.quran.ui.bookmarks.QuranBookmarksScreen
import com.parsfilo.contentapp.feature.quran.ui.bookmarks.QuranBookmarksUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Offline synthetic Compose test host: no user Quran content, settings or Play installation. */
@RunWith(AndroidJUnit4::class)
class QuranReaderAuxiliaryAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun assertIsolatedTestHost() {
        assertEquals("com.parsfilo.contentapp.feature.quran.test", context.packageName)
    }

    @Test
    fun readerHasTalkBackTextSizeActionsWithoutAnInlineSlider() {
        assertIsolatedTestHost()
        rule.setContent {
            AppTheme(flavorName = "kuran_kerim") {
                QuranSuraDetailScreen(
                    state = QuranSuraDetailUiState(isLoading = false),
                    onBack = {},
                    onRetry = {},
                    onDisplayModeSelected = {},
                    onFontSizeChange = {},
                    onReciterSelected = {},
                    onPlayPauseAyah = { _, _ -> },
                    onToggleBookmark = {},
                    onAyahVisible = {},
                )
            }
        }
        rule.onNodeWithTag("quran_text_size_slider").assertDoesNotExist()
        rule.onNodeWithTag("quran_detail")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
    }

    @Test
    fun twoFingerPinchCommitsLargerFontOnceAndDoesNotNeedInlineSlider() {
        assertIsolatedTestHost()
        val sizes = mutableListOf<Int>()
        rule.setContent {
            AppTheme(flavorName = "kuran_kerim") {
                QuranSuraDetailScreen(
                    state = QuranSuraDetailUiState(isLoading = false, fontSize = 28),
                    onBack = {},
                    onRetry = {},
                    onDisplayModeSelected = {},
                    onFontSizeChange = { sizes += it },
                    onReciterSelected = {},
                    onPlayPauseAyah = { _, _ -> },
                    onToggleBookmark = {},
                    onAyahVisible = {},
                )
            }
        }
        rule.onNodeWithTag("quran_detail").performTouchInput {
            pinch(
                start0 = center.copy(x = center.x - 28f),
                end0 = center.copy(x = center.x - 70f),
                start1 = center.copy(x = center.x + 28f),
                end1 = center.copy(x = center.x + 70f),
            )
        }
        rule.runOnIdle {
            assertEquals(1, sizes.size)
            assertTrue(sizes.single() > 28)
        }
    }

    @Test
    fun bookmarksBackHasLocalizedNameAndCallsNavigationOnce() {
        assertIsolatedTestHost()
        var backCalls = 0
        rule.setContent {
            AppTheme(flavorName = "kuran_kerim") {
                QuranBookmarksScreen(
                    state = QuranBookmarksUiState(),
                    onBack = { backCalls++ },
                    onAyahClick = { _, _ -> },
                    onRemoveBookmark = { _, _ -> },
                )
            }
        }
        rule.onNodeWithContentDescription(context.getString(R.string.quran_back))
            .assertHasClickAction()
            .performClick()
        rule.runOnIdle { assertEquals(1, backCalls) }
    }

    @Test
    fun textSizeNameIsLocalizedForTurkishEnglishAndGerman() {
        assertIsolatedTestHost()
        for ((tag, expected) in listOf(
            "tr" to "Yazı boyutu",
            "en" to "Text size",
            "de" to "Schriftgröße",
        )) {
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale.forLanguageTag(tag))
            val local = context.createConfigurationContext(config)
            assertEquals(expected, local.getString(R.string.quran_text_size))
        }
    }
}
