package com.parsfilo.contentapp.feature.quran.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.QuranSura
import com.parsfilo.contentapp.core.model.RevelationType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic sura only; verify its metadata is readable, not a disabled or clickable control. */
@RunWith(AndroidJUnit4::class)
class SuraListItemAccessibilityDeviceTest {
    @get:Rule val rule = createComposeRule()

    private val sura = QuranSura(
        number = 1,
        nameArabic = "Synthetic Arabic",
        nameLatin = "Synthetic Latin",
        nameTurkish = "Synthetic Turkish",
        nameEnglish = "Synthetic English",
        revelationType = RevelationType.MECCAN,
        ayahCount = 7,
    )

    @Test
    fun revelationMetadataIsReadableAndNotAnAction() {
        rule.setContent {
            AppTheme(flavorName = "kuran_kerim", darkTheme = false) {
                SuraListItem(
                    sura = sura,
                    meccanLabel = "Meccan",
                    medinanLabel = "Medinan",
                )
            }
        }

        rule.onNodeWithText("Meccan").assertIsEnabled().assertHasNoClickAction()
        rule.onNodeWithTag("quran_revelation_label").assertExists()
    }

    @Test
    fun revelationMetadataMeetsContrastInLightTheme() = assertRevelationContrast(dark = false)

    @Test
    fun revelationMetadataMeetsContrastInDarkTheme() = assertRevelationContrast(dark = true)

    private fun assertRevelationContrast(dark: Boolean) {
        var ratio = 0f
        rule.setContent {
            AppTheme(flavorName = "kuran_kerim", darkTheme = dark) {
                val foreground = MaterialTheme.colorScheme.onSurface.luminance()
                val background = MaterialTheme.colorScheme.surfaceVariant.luminance()
                ratio = (maxOf(foreground, background) + 0.05f) /
                    (minOf(foreground, background) + 0.05f)
                SuraListItem(
                    sura = sura.copy(revelationType = RevelationType.MEDINAN),
                    meccanLabel = "Meccan",
                    medinanLabel = "Medinan",
                )
            }
        }
        rule.onNodeWithText("Medinan").assertIsEnabled().assertHasNoClickAction()
        rule.runOnIdle {
            assertTrue("Revelation label must have 4.5:1 contrast (dark=$dark, ratio=$ratio)", ratio >= 4.5f)
        }
    }
}
