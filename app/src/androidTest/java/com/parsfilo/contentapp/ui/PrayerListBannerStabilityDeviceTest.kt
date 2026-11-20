package com.parsfilo.contentapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.Prayer
import com.parsfilo.contentapp.feature.ads.ui.MAX_INLINE_BANNER_HEIGHT_DP
import com.parsfilo.contentapp.feature.content.ui.prayer.PrayerListScreen
import com.parsfilo.contentapp.feature.content.ui.prayer.PrayerListUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrayerListBannerStabilityDeviceTest {
    @get:Rule val rule = createComposeRule()

    private val prayers = listOf(
        Prayer(
            sureID = 1,
            sureAdiAR = "Synthetic Arabic",
            sureAdiEN = "Synthetic",
            sureAdiTR = "Synthetic Prayer",
            ayetSayisi = 7,
            sureMedya = "SYNTHETIC.MP3",
            ayetler = emptyList(),
        ),
    )

    @Test
    fun lateBannerCreativeNeverMovesTheFirstPrayer() {
        var creativeReady by mutableStateOf(false)
        rule.setContent {
            AppTheme(flavorName = "namazsurelerivedualarsesli") {
                PrayerListScreen(
                    uiState = PrayerListUiState.Success(prayers, shouldShowAds = true),
                    onPrayerClick = {},
                    bannerAdUnitId = "synthetic-placeholder",
                    bannerAdContent = {
                        if (creativeReady) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(MAX_INLINE_BANNER_HEIGHT_DP.dp),
                            )
                        }
                    },
                )
            }
        }
        val slot = rule.onNodeWithTag("prayer_banner_slot")
        val first = rule.onNodeWithTag("content_first_item")
        val slotBefore = slot.getUnclippedBoundsInRoot()
        val rowBefore = first.getUnclippedBoundsInRoot()
        assertEquals(MAX_INLINE_BANNER_HEIGHT_DP.dp, slotBefore.bottom - slotBefore.top)

        rule.runOnUiThread { creativeReady = true }
        rule.waitForIdle()

        assertEquals(slotBefore, slot.getUnclippedBoundsInRoot())
        assertEquals(rowBefore.top, first.getUnclippedBoundsInRoot().top)
    }

    @Test
    fun adFreePrayerListDoesNotReserveUnnecessaryBannerSpace() {
        rule.setContent {
            AppTheme(flavorName = "namazsurelerivedualarsesli") {
                PrayerListScreen(
                    uiState = PrayerListUiState.Success(prayers, shouldShowAds = false),
                    onPrayerClick = {},
                    bannerAdUnitId = "synthetic-placeholder",
                    bannerAdContent = {},
                )
            }
        }
        rule.onNodeWithTag("prayer_banner_slot").assertDoesNotExist()
        rule.onNodeWithTag("content_first_item").assertExists()
    }
}
