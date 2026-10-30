package com.parsfilo.contentapp.feature.quran.ui.suradetail

import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.feature.quran.R
import com.parsfilo.contentapp.feature.quran.ui.bookmarks.QuranBookmarksScreen
import com.parsfilo.contentapp.feature.quran.ui.bookmarks.QuranBookmarksUiState
import org.junit.Assert.assertEquals
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
    fun fontSizeSliderExposesLocalizedNameAndNumericRange() {
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
                    onDownloadAyah = {},
                    onToggleBookmark = {},
                    onAyahVisible = {},
                )
            }
        }
        rule.onNodeWithTag("quran_text_size_slider")
            .assertContentDescriptionEquals(context.getString(R.string.quran_text_size))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
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
