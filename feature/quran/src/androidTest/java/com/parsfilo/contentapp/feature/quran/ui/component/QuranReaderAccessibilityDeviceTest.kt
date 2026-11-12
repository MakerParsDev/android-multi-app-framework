package com.parsfilo.contentapp.feature.quran.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.QuranAyah
import com.parsfilo.contentapp.core.model.QuranDisplayMode
import com.parsfilo.contentapp.feature.quran.R
import com.parsfilo.contentapp.feature.quran.config.QuranApiConfig
import com.parsfilo.contentapp.feature.quran.ui.suradetail.AyahDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                    onBookmarkClick = { actions += "bookmark" },
                )
            }
        }
        rule.onNodeWithTag("quran_ayah_1_bookmark")
            .assertContentDescriptionEquals(ctx.getString(R.string.quran_ayah_bookmark_add, 1))
            .performClick()
        rule.onNodeWithTag("quran_ayah_1_play")
            .assertContentDescriptionEquals(ctx.getString(R.string.quran_ayah_play, 1))
            .performClick()
        rule.onNodeWithTag("quran_ayah_1_download").assertDoesNotExist()
        // Physical left/right positions match users' reading expectations.
        val bookmark = rule.onNodeWithTag("quran_ayah_1_bookmark").fetchSemanticsNode().boundsInRoot
        val play = rule.onNodeWithTag("quran_ayah_1_play").fetchSemanticsNode().boundsInRoot
        assertTrue(bookmark.left < play.left)
        rule.onNodeWithText(ayah.arabic, useUnmergedTree = true).assertExists()
        rule.runOnIdle {
            assertEquals(listOf("bookmark", "play"), actions)
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
        rule.onNodeWithTag("quran_ayah_1_download").assertDoesNotExist()
    }

    @Test
    fun android9ShowsTransientCopyActionAndCopiesSelectedArabicWordOnTap() {
        verifyHost()
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val original = clipboard.primaryClip
        val arabicAyah = ayah.copy(arabic = "بسم الله الرحمن الرحيم")
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText("before", "reader-before"))
            rule.setContent {
                AppTheme(flavorName = "kuran_kerim") {
                    AyahItem(
                        state = AyahDisplayState(
                            ayah = arabicAyah,
                            isBookmarked = false,
                            isAudioDownloaded = false,
                            isAudioDownloading = false,
                            audioDownloadProgress = 0f,
                            isCurrentlyPlaying = false,
                        ),
                        displayMode = QuranDisplayMode.ARABIC,
                        translatedText = "",
                        fontSize = 26,
                        onPlayPauseClick = {},
                        onBookmarkClick = {},
                    )
                }
            }
            rule.onNodeWithTag("reader_copy_action").assertDoesNotExist()
            val text = rule.onNodeWithText(arabicAyah.arabic, useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            text.performTouchInput { longClick(layouts.single().getBoundingBox(1).center) }
            rule.onNodeWithTag("reader_copy_action").assertExists().performClick()
            rule.waitForIdle()
            rule.runOnIdle {
                val actual = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                assertTrue("Touch Copy must copy the selected Arabic word", actual?.contains("بسم") == true)
            }
            rule.onNodeWithTag("reader_copy_action").assertDoesNotExist()
        } finally {
            if (original != null) clipboard.setPrimaryClip(original) else clipboard.clearPrimaryClip()
        }
    }

    @Test
    fun longPressOnArabicGlyphCopiesSelectedWord() {
        val arabicAyah = ayah.copy(arabic = "بسم الله الرحمن الرحيم")
        assertGlyphCopyWorks(QuranDisplayMode.ARABIC, arabicAyah, arabicAyah.arabic, "بسم", glyphIndex = 1)
    }

    @Test
    fun longPressOnTranslationGlyphCopiesSelectedWord() {
        assertGlyphCopyWorks(QuranDisplayMode.TRANSLATION, ayah, ayah.turkish, "Turkish", glyphIndex = 12)
    }

    private fun assertGlyphCopyWorks(
        displayMode: QuranDisplayMode,
        testAyah: QuranAyah,
        targetText: String,
        expectedWord: String,
        glyphIndex: Int,
    ) {
        verifyHost()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val originalClip = clipboard.primaryClip
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText("test sentinel", "reader-copy-before"))
            rule.setContent {
                AppTheme(flavorName = "kuran_kerim") {
                    AyahItem(
                        state = AyahDisplayState(
                            ayah = testAyah,
                            isBookmarked = false,
                            isAudioDownloaded = false,
                            isAudioDownloading = false,
                            audioDownloadProgress = 0f,
                            isCurrentlyPlaying = false,
                        ),
                        displayMode = displayMode,
                        translatedText = testAyah.turkish,
                        fontSize = 26,
                        onPlayPauseClick = {},
                        onBookmarkClick = {},
                    )
                }
            }
            val text = rule.onNodeWithText(targetText, useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val glyph = layouts.single().getBoundingBox(glyphIndex).center
            // Long press an actual glyph, not the center of a wide, aligned Text box.
            text.performTouchInput { longClick(glyph) }
            // Android's standard selection Copy keyboard command is a deterministic
            // way to verify clipboard results without depending on a platform popup.
            text.performKeyInput {
                keyDown(Key.CtrlLeft)
                keyDown(Key.C)
                keyUp(Key.C)
                keyUp(Key.CtrlLeft)
            }
            rule.runOnIdle {
                val actual = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                assertTrue("A long-pressed reader word must be copyable", actual?.contains(expectedWord) == true)
            }
        } finally {
            if (originalClip != null) {
                clipboard.setPrimaryClip(originalClip)
            } else {
                clipboard.clearPrimaryClip()
            }
        }
    }

    @Test
    fun reciterIsAnActualMutuallyExclusiveRadioChoice() {
        verifyHost()
        val reciters = QuranApiConfig.Reciters.ALL
        assertEquals(5, reciters.size)
        var selected by mutableStateOf(reciters.first().id)
        val selectedIds = mutableListOf<String>()
        var dismissals = 0
        rule.setContent {
            AppTheme(flavorName = "kuran_kerim") {
                ReciterSelectionSheet(
                    reciters = reciters,
                    selectedReciterId = selected,
                    onSelect = {
                        selected = it
                        selectedIds += it
                    },
                    onDismiss = { dismissals++ },
                )
            }
        }
        val radioRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        reciters.forEachIndexed { index, reciter ->
            if (index > 0) {
                rule.onNodeWithText(reciter.displayName)
                    .assert(radioRole)
                    .assertIsNotSelected()
                    .performClick()
            }
            reciters.forEach { option ->
                val isSelected = option.id == reciter.id
                val text = option.displayName + if (isSelected) " ✓" else ""
                val node = rule.onNodeWithText(text).assert(radioRole)
                if (isSelected) node.assertIsSelected() else node.assertIsNotSelected()
            }
            rule.runOnIdle {
                assertEquals(reciter.id, selected)
                assertEquals(reciters.take(index + 1).drop(1).map { it.id }, selectedIds)
                assertEquals(index, dismissals)
            }
        }
    }
}
