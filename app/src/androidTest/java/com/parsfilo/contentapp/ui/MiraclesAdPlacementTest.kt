package com.parsfilo.contentapp.ui

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.MiraclesPrayer
import com.parsfilo.contentapp.feature.content.ui.miracles.MiraclesContentVariant
import com.parsfilo.contentapp.feature.content.ui.miracles.MiraclesDetailScreen
import com.parsfilo.contentapp.feature.content.ui.miracles.MiraclesDetailUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic ad slot only: verifies reading order without loading or clicking real ads. */
@RunWith(AndroidJUnit4::class)
class MiraclesAdPlacementTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun esmaMeaningIsAboveTheNativeAd() {
        rule.setContent {
            AppTheme(flavorName = "esmaulhusna") {
                MiraclesDetailScreen(
                    uiState = MiraclesDetailUiState.Success(
                        MiraclesPrayer(
                            duaIsim = "Test",
                            duaAciklama = "Meaning before ad",
                            duaBesmele = "Bismillah",
                            duaArapca = "الله",
                            duaLatinOkunus = "Allah",
                        ),
                        shouldShowAds = true,
                    ),
                    variant = MiraclesContentVariant.ESMAUL_HUSNA,
                ) {
                    Text("Sponsored fixture", modifier = Modifier.testTag("synthetic_native_ad"))
                }
            }
        }

        rule.onNodeWithText("Meaning before ad").assertIsDisplayed()
        rule.onNodeWithTag("synthetic_native_ad").assertIsDisplayed()
        val meaningTop = rule.onNodeWithText("Meaning before ad").fetchSemanticsNode().boundsInRoot.top
        val adTop = rule.onNodeWithTag("synthetic_native_ad").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Native ad must follow the explanation", meaningTop < adTop)
    }

    @Test
    fun premiumReaderDoesNotComposeTheAdSlot() {
        rule.setContent {
            AppTheme(flavorName = "esmaulhusna") {
                MiraclesDetailScreen(
                    uiState = MiraclesDetailUiState.Success(
                        MiraclesPrayer("Test", "Meaning", "Bismillah", "الله", "Allah"),
                        shouldShowAds = false,
                    ),
                    variant = MiraclesContentVariant.ESMAUL_HUSNA,
                ) {
                    Text("Sponsored fixture", modifier = Modifier.testTag("synthetic_native_ad"))
                }
            }
        }
        rule.onNodeWithTag("synthetic_native_ad").assertDoesNotExist()
    }
}
