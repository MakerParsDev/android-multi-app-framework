package com.parsfilo.contentapp.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
            label = "Arapça metni kopyala",
            expectedText = verse.arabic,
            fontScale = 2f,
        )
    }

    @Test
    fun latinCopyUsesTheCurrentTransliteration() {
        checkCopy(
            mode = DisplayMode.LATIN,
            language = "tr",
            label = "Latince metni kopyala",
            expectedText = verse.latin,
        )
    }

    @Test
    fun englishTranslationCopiesEnglishAndNotTurkish() {
        checkCopy(
            mode = DisplayMode.TURKISH,
            language = "en",
            label = "Copy translation",
            expectedText = verse.english,
        )
    }

    @Test
    fun germanTranslationCopiesGermanWithLocalizedAccessibleLabel() {
        checkCopy(
            mode = DisplayMode.TURKISH,
            language = "de",
            label = "Übersetzung kopieren",
            expectedText = verse.german,
        )
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
            rule.onNodeWithContentDescription(label, useUnmergedTree = true).assertExists()

            // The former card-wide onClick={} must not remain in the semantics tree.
            val clickTargets = rule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(1, clickTargets.size)
            val target = rule.onNodeWithTag("verse_copy_button", useUnmergedTree = true)
            val bounds = target.fetchSemanticsNode().boundsInRoot
            val minimumPixels = 48f * context.resources.displayMetrics.density
            assertTrue(bounds.width >= minimumPixels)
            assertTrue(bounds.height >= minimumPixels)

            target.performClick()
            rule.waitForIdle()
            val actual = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            // Do not include sensitive clipboard contents in assertion failure logs.
            assertTrue(actual == expectedText)
        } finally {
            clipboard.clearPrimaryClip()
        }
    }
}
