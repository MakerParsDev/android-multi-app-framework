package com.parsfilo.contentapp.ui

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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.DisplayMode
import com.parsfilo.contentapp.core.model.Verse
import com.parsfilo.contentapp.feature.content.ui.AppHeader
import com.parsfilo.contentapp.feature.content.ui.BottomModeSelector
import com.parsfilo.contentapp.feature.content.ui.VerseItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ContentReaderLocaleAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun germanFullModeLabelsRemainVisibleAt320dpAnd200Percent() {
        showReader("de-DE", 320, 2f)
        assertGermanModeFits()
    }

    @Test
    fun germanFullModeLabelsRemainVisibleAt360dpAnd200Percent() {
        showReader("de-DE", 360, 2f)
        assertGermanModeFits()
    }

    @Test
    fun germanFullModeLabelsRemainVisibleAt411dpAnd200Percent() {
        showReader("de-DE", 411, 2f)
        assertGermanModeFits()
    }

    @Test
    fun localizedHeaderActionsAndTurkishFallbackAreExposedInEnglish() {
        showReader("en-US", 320, 2f)
        rule.onNodeWithContentDescription("Open settings").assertExists()
        rule.onNodeWithContentDescription("Open rewards").assertExists()
        rule.onNodeWithText("Turkish translation (not available in this language)").assertExists()
        rule.onNodeWithText("Türkçe anlam").assertExists()
    }

    @Test
    fun localizedHeaderActionsAndTurkishFallbackAreExposedInGerman() {
        showReader("de-DE", 320, 2f)
        rule.onNodeWithContentDescription("Einstellungen öffnen").assertExists()
        rule.onNodeWithContentDescription("Belohnungen öffnen").assertExists()
        rule.onNodeWithText("Türkische Übersetzung (in dieser Sprache nicht verfügbar)").assertExists()
        rule.onNodeWithText("Türkçe anlam").assertExists()
    }

    @Test
    fun turkishHeaderActionsDoNotExposeForeignLanguageFallback() {
        showReader("tr-TR", 320, 2f)
        rule.onNodeWithContentDescription("Ayarları aç").assertExists()
        rule.onNodeWithContentDescription("Ödülleri aç").assertExists()
        rule.onNodeWithText("Türkçe anlam").assertExists()
        rule.onNodeWithText("Türkçe meal (seçili dilde çeviri bulunamadı)").assertDoesNotExist()
    }

    private fun assertGermanModeFits() {
        listOf("Arabisch", "Latein", "Übersetzung").forEach { label ->
            val node = rule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            val collected = node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            assertTrue("$label must expose its actual text layout", collected == true)
            val layout = layouts.single()
            assertFalse("$label height overflow", layout.didOverflowHeight)
            assertEquals("$label should fit on one readable line", 1, layout.lineCount)
            assertEquals("$label must not be truncated", label.length, layout.getLineEnd(0, visibleEnd = true))
        }
        val arabic = rule.onNodeWithText("Arabisch", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val latin = rule.onNodeWithText("Latein", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val translation = rule.onNodeWithText("Übersetzung", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Arabic and Latin modes should stack", arabic.bottom <= latin.top)
        assertTrue("Latin and Translation modes should stack", latin.bottom <= translation.top)
    }

    private fun showReader(locale: String, widthDp: Int, fontScale: Float) {
        rule.setContent {
            val baseContext = LocalContext.current
            val density = LocalDensity.current
            val configuration = Configuration(LocalConfiguration.current).apply {
                setLocales(LocaleList(Locale.forLanguageTag(locale)))
                screenWidthDp = widthDp
                this.fontScale = fontScale
            }
            CompositionLocalProvider(
                LocalContext provides baseContext.createConfigurationContext(configuration),
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                AppTheme(flavorName = "fetihsuresi") {
                    Box(Modifier.width(widthDp.dp)) {
                        Column {
                            AppHeader(appName = "Reader")
                            BottomModeSelector(
                                currentMode = DisplayMode.TURKISH,
                                onModeSelected = {},
                            )
                            VerseItem(
                                verse = Verse(
                                    id = 1,
                                    arabic = "نص",
                                    latin = "Latin",
                                    turkish = "Türkçe anlam",
                                    english = "",
                                    german = "",
                                ),
                                displayMode = DisplayMode.TURKISH,
                                fontSize = 18,
                            )
                        }
                    }
                }
            }
        }
    }
}
