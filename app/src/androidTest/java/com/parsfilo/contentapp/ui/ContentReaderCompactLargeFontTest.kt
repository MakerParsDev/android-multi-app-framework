package com.parsfilo.contentapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.DisplayMode
import com.parsfilo.contentapp.core.model.Verse
import com.parsfilo.contentapp.feature.content.R
import com.parsfilo.contentapp.feature.content.ui.ContentScreen
import com.parsfilo.contentapp.feature.content.ui.ContentUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the ACTUAL shared reader layout, not a separately assembled header/selector. */
@RunWith(AndroidJUnit4::class)
class ContentReaderCompactLargeFontTest {
    @get:Rule val rule = createComposeRule()

    @Test fun twoHundredPercentFontMakesReaderToolsAndTranslationReachable() {
        showReader(2f)
        // Compact layout must provide the reader-tools shortcut to its scrollable modes.
        rule.onNodeWithTag("content_reader_tools").assertIsDisplayed().performClick()
        rule.waitForIdle()
        rule.onNodeWithText(translationLabel()).assertIsDisplayed().performClick()
    }

    @Test fun normalFontKeepsOrdinaryNonCompactReaderAtSameHeight() {
        showReader(1f)
        rule.onNodeWithTag("content_reader_tools").assertDoesNotExist()
        rule.onNodeWithText(translationLabel()).assertIsDisplayed()
    }

    private fun translationLabel() = InstrumentationRegistry.getInstrumentation()
        .targetContext.getString(R.string.content_mode_turkish)

    private fun showReader(fontScale: Float) {
        val pixelDensity = 2.625f
        val readerWindow = object : WindowInfo {
            override val isWindowFocused: Boolean = true
            override val containerSize: IntSize = IntSize(
                width = (320f * pixelDensity).toInt(),
                height = (569f * pixelDensity).toInt(),
            )
        }
        rule.setContent {
            var mode by androidx.compose.runtime.remember { mutableStateOf(DisplayMode.ARABIC) }
            CompositionLocalProvider(
                LocalDensity provides Density(pixelDensity, fontScale),
                LocalWindowInfo provides readerWindow,
            ) {
                AppTheme(flavorName = "fetihsuresi") {
                    Box(Modifier.width(320.dp).height(569.dp)) {
                        ContentScreen(
                            appName = "Reader",
                            uiState = ContentUiState.Success(
                                verses = listOf(
                                    Verse(1, "نص", "Latin", "Türkçe anlam", "", "")
                                ),
                                displayMode = mode,
                                fontSize = 18,
                                shouldShowAds = false,
                            ),
                            onModeSelected = { mode = it },
                            bannerAdUnitId = "",
                        )
                    }
                }
            }
        }
    }
}
