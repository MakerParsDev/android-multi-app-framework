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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.DisplayMode
import com.parsfilo.contentapp.feature.content.ui.prayer.BottomModeSelector
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

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
                        BottomModeSelector(
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
        rule.onNodeWithText(latin).performClick()
        assertSelection(latin)
        rule.onNodeWithText(translated).performClick()
        assertSelection(translated)
        rule.runOnIdle {
            assertEquals(DisplayMode.TURKISH, mode)
            assertEquals(2, callbacks)
        }
    }
}
