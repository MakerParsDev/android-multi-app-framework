package com.parsfilo.contentapp.feature.quran.ui.suradetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.QuranAyah
import com.parsfilo.contentapp.core.model.QuranDisplayMode
import com.parsfilo.contentapp.core.model.QuranSura
import com.parsfilo.contentapp.core.model.RevelationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Disposable feature test package: no repository, database, network, permissions or real Quran app. */
@RunWith(AndroidJUnit4::class)
class QuranReadingResumeDeviceTest {
    @get:Rule val rule = createComposeRule()

    private fun syntheticState(verseCount: Int = 10) = QuranSuraDetailUiState(
        isLoading = false,
        displayMode = QuranDisplayMode.LATIN,
        sura = QuranSura(
            number = 2,
            nameArabic = "Synthetic",
            nameLatin = "Synthetic",
            nameTurkish = "Synthetic",
            nameEnglish = "Synthetic",
            revelationType = RevelationType.MEDINAN,
            ayahCount = verseCount,
        ),
        ayahs = (1..verseCount).map { number ->
            AyahDisplayState(
                ayah = QuranAyah(
                    suraNumber = 2,
                    ayahNumber = number,
                    arabic = "Synthetic Arabic $number",
                    latin = "Synthetic verse $number",
                    turkish = "Synthetic $number",
                    english = "Synthetic $number",
                    german = "Synthetic $number",
                ),
                isBookmarked = false,
                isAudioDownloaded = false,
                isAudioDownloading = false,
                audioDownloadProgress = 0f,
                isCurrentlyPlaying = false,
            )
        },
    )

    private fun verifyTestHost() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.parsfilo.contentapp.feature.quran.test", context.packageName)
    }

    @Test
    fun lastReadTwoFiveTargetsFifthVerseWithoutRegressingToStart() {
        verifyTestHost()
        val reported = mutableListOf<Int>()
        rule.setContent {
            AppTheme(flavorName = "kuran_kerim") {
                QuranSuraDetailScreen(
                    state = syntheticState(),
                    initialAyahNumber = 5,
                    onBack = {},
                    onRetry = {},
                    onDisplayModeSelected = {},
                    onFontSizeChange = {},
                    onReciterSelected = {},
                    onPlayPauseAyah = { _, _ -> },
                    onDownloadAyah = {},
                    onToggleBookmark = {},
                    onAyahVisible = { reported.add(it.ayahNumber) },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Synthetic verse 5").assertIsDisplayed()
        assertTrue("Target verse should be reported visible", reported.contains(5))
        assertFalse("First verse must not overwrite restored progress", reported.contains(1))
    }

    @Test
    fun asyncLongSuraWaitsForTargetBeforeReportingProgress() {
        verifyTestHost()
        val reported = mutableListOf<Int>()
        var state by mutableStateOf(QuranSuraDetailUiState(isLoading = true))
        rule.setContent {
            AppTheme(flavorName = "kuran_kerim") {
                QuranSuraDetailScreen(
                    state = state,
                    initialAyahNumber = 285,
                    onBack = {},
                    onRetry = {},
                    onDisplayModeSelected = {},
                    onFontSizeChange = {},
                    onReciterSelected = {},
                    onPlayPauseAyah = { _, _ -> },
                    onDownloadAyah = {},
                    onToggleBookmark = {},
                    onAyahVisible = { reported.add(it.ayahNumber) },
                )
            }
        }
        rule.waitForIdle()
        assertTrue("Loading must not persist a dummy position", reported.isEmpty())
        rule.runOnIdle { state = syntheticState(286) }
        rule.waitForIdle()
        rule.onNodeWithText("Synthetic verse 285").assertIsDisplayed()
        rule.waitUntil(timeoutMillis = 10_000) { reported.contains(285) }
        assertTrue("Loaded target must be visible, got $reported", reported.contains(285))
        assertFalse("First ayah must not overwrite the late-loaded target", reported.contains(1))
    }

    @Test
    fun bookmarkToVerseEightSurvivesDisplayModeChange() {
        verifyTestHost()
        val reported = mutableListOf<Int>()
        var state by mutableStateOf(syntheticState())
        rule.setContent {
            AppTheme(flavorName = "kuran_kerim") {
                QuranSuraDetailScreen(
                    state = state,
                    initialAyahNumber = 8,
                    onBack = {},
                    onRetry = {},
                    onDisplayModeSelected = {},
                    onFontSizeChange = {},
                    onReciterSelected = {},
                    onPlayPauseAyah = { _, _ -> },
                    onDownloadAyah = {},
                    onToggleBookmark = {},
                    onAyahVisible = { reported.add(it.ayahNumber) },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Synthetic verse 8").assertIsDisplayed()
        rule.runOnIdle { state = state.copy(displayMode = QuranDisplayMode.ARABIC) }
        rule.waitForIdle()
        assertTrue("Bookmark target should remain the visible reading position", reported.contains(8))
        assertFalse("Mode change must not save the first verse", reported.contains(1))
    }
}
