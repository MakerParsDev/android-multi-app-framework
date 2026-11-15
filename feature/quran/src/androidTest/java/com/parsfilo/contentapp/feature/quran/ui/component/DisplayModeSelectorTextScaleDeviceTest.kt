package com.parsfilo.contentapp.feature.quran.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.QuranDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisplayModeSelectorTextScaleDeviceTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun defaultTextScaleKeepsModeSelectionCompactOnTypicalPhoneWidth() {
        rule.setContent {
            val originalDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(originalDensity.density, fontScale = 1f),
            ) {
                AppTheme(flavorName = "kuran_kerim") {
                    Box(modifier = Modifier.width(411.dp)) {
                        DisplayModeSelector(
                            currentMode = QuranDisplayMode.ARABIC,
                            arabicLabel = "Arabic",
                            latinLabel = "Transliteration",
                            translationLabel = "Translation",
                            onModeSelected = {},
                        )
                    }
                }
            }
        }

        val arabicY = rule.onNodeWithText("Arabic").fetchSemanticsNode().boundsInRoot.center.y
        val latinY = rule.onNodeWithText("Transliteration").fetchSemanticsNode().boundsInRoot.center.y
        val translationY = rule.onNodeWithText("Translation").fetchSemanticsNode().boundsInRoot.center.y
        assertEquals("Default labels must stay on one compact row", arabicY, latinY, 2f)
        assertEquals("Default labels must stay on one compact row", arabicY, translationY, 2f)
    }

    @Test
    fun fullLanguageLabelsRemainVisibleAndSelectableOnNarrowScreenAtTwoHundredPercent() {
        var currentMode by mutableStateOf(QuranDisplayMode.ARABIC)

        rule.setContent {
            val originalDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(originalDensity.density, fontScale = 2f),
            ) {
                AppTheme(flavorName = "kuran_kerim") {
                    Box(modifier = Modifier.width(320.dp)) {
                        DisplayModeSelector(
                            currentMode = currentMode,
                            arabicLabel = "Arabic",
                            latinLabel = "Transliteration",
                            translationLabel = "Translation",
                            onModeSelected = { currentMode = it },
                        )
                    }
                }
            }
        }

        listOf("Arabic", "Transliteration", "Translation").forEach { label ->
            val textNode = rule.onNodeWithText(label, useUnmergedTree = true)
            textNode.assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            textNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("Expected a text layout for $label", 1, layouts.size)
            assertFalse("$label overflow: size=${layouts.single().size}, width=${layouts.single().didOverflowWidth}, height=${layouts.single().didOverflowHeight}, lines=${layouts.single().lineCount}", layouts.single().hasVisualOverflow)
        }

        rule.onNodeWithText("Arabic").assertIsSelected()
        rule.onNodeWithText("Transliteration").performClick()
        rule.runOnIdle { assertEquals(QuranDisplayMode.LATIN, currentMode) }
        rule.onNodeWithText("Transliteration").assertIsSelected()
        rule.onNodeWithText("Translation").performClick()
        rule.runOnIdle { assertEquals(QuranDisplayMode.TRANSLATION, currentMode) }
        rule.onNodeWithText("Translation").assertIsSelected()
    }
}
