package com.parsfilo.contentapp.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.DisplayMode
import com.parsfilo.contentapp.core.model.Verse
import com.parsfilo.contentapp.feature.content.ui.VerseItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class VerseCopyAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    private val verse =
        Verse(
            id = 1,
            arabic = "بِسْمِ اللَّهِ",
            latin = "Bismillâh",
            turkish = "Allah'ın adıyla",
            english = "In the name of Allah",
            german = "Im Namen Allahs",
        )

    @Test
    fun arabicCopyHasNoDeadCardClickAndWorksAtDoubleFontScale() {
        checkCopy(
            mode = DisplayMode.ARABIC,
            language = "tr",
            label = "Ayet / bölüm 1: Arapça metni kopyala",
            expectedText = verse.arabic,
            fontScale = 2f,
        )
    }

    @Test
    fun latinCopyUsesTheCurrentTransliteration() {
        checkCopy(
            mode = DisplayMode.LATIN,
            language = "tr",
            label = "Ayet / bölüm 1: Latince metni kopyala",
            expectedText = verse.latin,
        )
    }

    @Test
    fun englishTranslationCopiesEnglishAndNotTurkish() {
        checkCopy(
            mode = DisplayMode.TURKISH,
            language = "en",
            label = "Verse / section 1: Copy translation",
            expectedText = verse.english,
        )
    }

    @Test
    fun germanTranslationCopiesGermanWithLocalizedAccessibleLabel() {
        checkCopy(
            mode = DisplayMode.TURKISH,
            language = "de",
            label = "Vers / Abschnitt 1: Übersetzung kopieren",
            expectedText = verse.german,
        )
    }

    @Test
    fun twoVersesProvideDistinctLocalizedCopyTargets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val second = verse.copy(id = 2, latin = "İkinci ayetin okunuşu")
        try {
            rule.setContent {
                val density = LocalDensity.current
                val baseContext = LocalContext.current
                val config = Configuration(LocalConfiguration.current).apply {
                    setLocales(LocaleList(Locale.forLanguageTag("tr")))
                    screenWidthDp = 320
                }
                CompositionLocalProvider(
                    LocalContext provides baseContext.createConfigurationContext(config),
                    LocalConfiguration provides config,
                    LocalDensity provides Density(density.density, config.fontScale),
                ) {
                    AppTheme(flavorName = "ayetelkursi") {
                        Box(Modifier.width(320.dp)) {
                            Column {
                                VerseItem(verse = verse, displayMode = DisplayMode.LATIN, fontSize = 24)
                                VerseItem(verse = second, displayMode = DisplayMode.LATIN, fontSize = 24)
                            }
                        }
                    }
                }
            }

            // No persistent Copy icons; individual verse actions stay available to TalkBack.
            rule.onNodeWithTag("verse_copy_button", useUnmergedTree = true).assertDoesNotExist()
            val first = rule.onNodeWithTag("verse_copy_action_1", useUnmergedTree = true)
                .fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
            val secondAction = rule.onNodeWithTag("verse_copy_action_2", useUnmergedTree = true)
                .fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
            assertTrue(first.label.contains("1"))
            assertTrue(secondAction.label.contains("2"))
            assertTrue(first.label != secondAction.label)
            rule.runOnIdle { assertTrue(secondAction.action()) }
            rule.waitForIdle()
            val actual = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            assertEquals(second.latin, actual)
        } finally {
            clipboard.clearPrimaryClip()
        }
    }

    private fun checkCopy(
        mode: DisplayMode,
        language: String,
        label: String,
        expectedText: String,
        fontScale: Float = 1f,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        try {
            rule.setContent {
                val density = LocalDensity.current
                val currentContext = LocalContext.current
                val config =
                    Configuration(LocalConfiguration.current).apply {
                        setLocales(LocaleList(Locale.forLanguageTag(language)))
                        screenWidthDp = 320
                        this.fontScale = fontScale
                    }
                val localizedContext = currentContext.createConfigurationContext(config)
                CompositionLocalProvider(
                    LocalContext provides localizedContext,
                    LocalConfiguration provides config,
                    LocalDensity provides Density(density.density, fontScale),
                ) {
                    AppTheme(flavorName = "ayetelkursi") {
                        Box(Modifier.width(320.dp)) {
                            VerseItem(verse = verse, displayMode = mode, fontSize = 30)
                        }
                    }
                }
            }

            rule.onNodeWithText(expectedText, useUnmergedTree = true).assertExists()
            rule.onNodeWithTag("verse_copy_button", useUnmergedTree = true).assertDoesNotExist()
            val clickTargets = rule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(0, clickTargets.size)
            val action = rule.onNodeWithTag(
                "verse_copy_action_1",
                useUnmergedTree = true,
            ).fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
            assertEquals(label, action.label)
            rule.runOnIdle { assertTrue(action.action()) }
            rule.waitForIdle()
            val actual = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            // Do not include sensitive clipboard contents in assertion failure logs.
            assertTrue(actual == expectedText)
        } finally {
            clipboard.clearPrimaryClip()
        }
    }
}
