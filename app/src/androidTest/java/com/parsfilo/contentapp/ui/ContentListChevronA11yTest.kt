package com.parsfilo.contentapp.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.MiraclesPrayer
import com.parsfilo.contentapp.core.model.Prayer
import com.parsfilo.contentapp.feature.content.ui.miracles.MiraclesContentVariant
import com.parsfilo.contentapp.feature.content.ui.miracles.MiraclesListItem
import com.parsfilo.contentapp.feature.content.ui.prayer.PrayerListItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A list item must expose its named click target, not a separate Turkish
 * "Detay" announcement from the purely decorative chevron.
 */
@RunWith(AndroidJUnit4::class)
class ContentListChevronA11yTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun prayerRowDoesNotExposeDecorativeTurkishChevron() {
        rule.setContent {
            AppTheme(flavorName = "namazsurelerivedualarsesli") {
                PrayerListItem(
                    prayer = Prayer(
                        sureID = 1,
                        sureAdiAR = "",
                        sureAdiEN = "Prayer One",
                        sureAdiTR = "Dua Bir",
                        sureAdiDE = "Gebet Eins",
                        ayetSayisi = 0,
                        sureMedya = "",
                        ayetler = emptyList(),
                    ),
                    onClick = {},
                    showVerseCount = false,
                )
            }
        }
        rule.onAllNodes(hasClickAction()).assertCountEquals(1)
        rule.onNodeWithContentDescription("Detay", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun miraclesRowDoesNotExposeDecorativeTurkishChevron() {
        rule.setContent {
            AppTheme(flavorName = "esmaulhusna") {
                MiraclesListItem(
                    prayer = MiraclesPrayer(
                        duaIsim = "Test Name",
                        duaAciklama = "Test description",
                        duaBesmele = "",
                        duaArapca = "",
                    ),
                    index = 1,
                    variant = MiraclesContentVariant.ESMAUL_HUSNA,
                    onClick = {},
                )
            }
        }
        rule.onAllNodes(hasClickAction()).assertCountEquals(1)
        rule.onNodeWithContentDescription("Detay", useUnmergedTree = true).assertDoesNotExist()
    }
}
