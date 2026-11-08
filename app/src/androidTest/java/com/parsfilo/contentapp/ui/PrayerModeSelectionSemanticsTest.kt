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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.DisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import com.parsfilo.contentapp.feature.content.ui.BottomModeSelector as ContentBottomModeSelector
import com.parsfilo.contentapp.feature.content.ui.prayer.BottomModeSelector as PrayerBottomModeSelector

/** The prayer-detail modes are a true single-choice group, not three visual-only buttons. */
@RunWith(AndroidJUnit4::class)
class PrayerModeSelectionSemanticsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun turkishModeSelectionIsSemanticAndUpdates() =
        verifyModes(
            locale = "tr-TR",
            arabic = "Arapça",
            latin = "Latince",
            translated = "Meal",
        )

    @Test
    fun germanModeSelectionIsSemanticAndUpdates() =
        verifyModes(
            locale = "de-DE",
            arabic = "Arabisch",
            latin = "Latein",
            translated = "Übersetzung",
        )

    @Test
    fun germanLargeTextPrayerModesFitWithoutClippingAt320Dp() =
        verifyGermanLargeTextLayout(widthDp = 320)

    @Test
    fun germanLargeTextPrayerModesFitWithoutClippingAt411Dp() =
        verifyGermanLargeTextLayout(widthDp = 411)

    @Test
    fun germanLargeTextContentModesFitWithoutClippingAt320Dp() =
        verifyGermanLargeTextLayout(widthDp = 320, contentReader = true)

    @Test
    fun germanLargeTextContentModesFitWithoutClippingAt411Dp() =
        verifyGermanLargeTextLayout(widthDp = 411, contentReader = true)

    private fun verifyGermanLargeTextLayout(widthDp: Int, contentReader: Boolean = false) {
        rule.setContent {
            val context = LocalContext.current
            val density = LocalDensity.current
            val config = Configuration(LocalConfiguration.current).apply {
                setLocales(LocaleList.forLanguageTags("de-DE"))
                screenWidthDp = widthDp
                fontScale = 2f
            }
            CompositionLocalProvider(
                LocalContext provides context.createConfigurationContext(config),
                LocalConfiguration provides config,
                LocalDensity provides Density(density.density, 2f),
            ) {
                AppTheme(flavorName = "fetihsuresi") {
                    Box(Modifier.width(widthDp.dp)) {
                        if (contentReader) {
                            ContentBottomModeSelector(
                                currentMode = DisplayMode.TURKISH,
                                onModeSelected = {},
                            )
                        } else {
                            PrayerBottomModeSelector(
                                currentMode = DisplayMode.TURKISH,
                                onModeSelected = {},
                            )
                        }
                    }
                }
            }
        }

        val labels = listOf("Arabisch", "Latein", "Übersetzung")
        labels.forEach { label ->
            val node = rule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(
                "$label should expose its actual text layout",
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true,
            )
            val layout = layouts.single()
            assertFalse("$label must not overflow vertically", layout.didOverflowHeight)
            assertEquals("$label should remain one readable line", 1, layout.lineCount)
            assertEquals("$label must not be clipped", label.length, layout.getLineEnd(0, visibleEnd = true))
        }
        // Compact reader modes intentionally scroll horizontally rather than stacking
        // three tall buttons over reading content. A mode can be outside the viewport
        // until it is brought into view, so assert both order and actual reachability.
        val initialBounds = labels.map { rule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        assertTrue(
            "Reading modes should follow Arabic, Latin, Translation horizontally",
            initialBounds.zipWithNext().all { (first, second) -> first.left < second.left },
        )
        assertTrue(
            "Reading modes should share one row instead of obscuring the reader",
            initialBounds.zipWithNext().all { (first, second) ->
                kotlin.math.abs(first.top - second.top) <= 1f
            },
        )

        val minHeightPx = 48f * rule.density.density
        labels.forEach { label ->
            val mode = rule.onNodeWithText(label).performScrollTo()
            val visibleBounds = mode.fetchSemanticsNode().boundsInRoot
            assertTrue("$label must maintain 48dp touch height", visibleBounds.height >= minHeightPx)
            assertTrue("$label should be reachable within the reader width", visibleBounds.left >= -1f)
            assertTrue(
                "$label must be fully reachable after horizontal scroll",
                visibleBounds.right <= widthDp * rule.density.density + 1f,
            )
        }
    }

    private fun verifyModes(
        locale: String,
        arabic: String,
        latin: String,
        translated: String,
    ) {
        var mode by mutableStateOf(DisplayMode.ARABIC)
        var callbacks = 0
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
                        PrayerBottomModeSelector(
                            currentMode = mode,
                            onModeSelected = {
                                callbacks++
                                mode = it
                            },
                        )
                    }
                }
            }
        }

        val labels = listOf(arabic, latin, translated)

        fun assertSelection(selected: String) {
            labels.forEach { label ->
                val node = rule.onNodeWithText(label)
                node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                if (label == selected) node.assertIsSelected() else node.assertIsNotSelected()
            }
        }

        assertSelection(arabic)
        // At 320dp and 200% font scale these are offscreen horizontally.
        // A real user first scrolls them into view before tapping.
        rule.onNodeWithText(latin).performScrollTo().performClick()
        assertSelection(latin)
        rule.onNodeWithText(translated).performScrollTo().performClick()
        assertSelection(translated)
        rule.runOnIdle {
            assertEquals(DisplayMode.TURKISH, mode)
            assertEquals(2, callbacks)
        }
    }
}
