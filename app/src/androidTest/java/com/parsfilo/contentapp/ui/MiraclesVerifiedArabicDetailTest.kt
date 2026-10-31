package com.parsfilo.contentapp.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.BuildConfig
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.feature.content.data.AssetMiraclesPrayerRepository
import com.parsfilo.contentapp.feature.content.ui.miracles.MiraclesDetailScreen
import com.parsfilo.contentapp.feature.content.ui.miracles.MiraclesDetailUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses the installed Mucize Dualar assets and Compose semantics, not a mocked prayer list.
 * Avoids external advertisements, notification permission prompts and flaky list navigation.
 */
@RunWith(AndroidJUnit4::class)
class MiraclesVerifiedArabicDetailTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun nazarShowsOriginalTwoVersesAndSourceWithLegacyLatin() {
        checkDetail(index = 9, firstReference = "68:51", lastReference = "68:52")
    }

    @Test
    fun ayetelKursiShowsOriginalVerseAndSourceWithLegacyLatin() {
        checkDetail(index = 13, firstReference = "2:255", lastReference = "2:255")
    }

    @Test
    fun fetihShowsAllTenVersesAndKeepsLatinBasmala() {
        checkDetail(index = 15, firstReference = "48:1", lastReference = "48:10")
    }

    @Test
    fun quranSourceEntry01ShowsOriginalArabicAndLegacyLatin() {
        checkDetail(index = 0, firstReference = "38:35", lastReference = "38:35")
    }

    @Test
    fun quranSourceEntry04ShowsOriginalArabicAndLegacyLatin() {
        checkDetail(index = 3, firstReference = "3:173", lastReference = "3:173")
    }

    @Test
    fun quranSourceEntry06ShowsOriginalArabicAndLegacyLatin() {
        checkDetail(index = 5, firstReference = "59:22", lastReference = "59:24")
    }

    @Test
    fun quranSourceEntry08ShowsOriginalArabicAndLegacyLatin() {
        checkDetail(index = 7, firstReference = "19:96", lastReference = "19:96")
    }

    @Test
    fun quranSourceEntry12ShowsOriginalArabicAndLegacyLatin() {
        checkDetail(index = 11, firstReference = "20:25", lastReference = "20:28")
    }

    @Test
    fun quranSourceEntry13ShowsOriginalArabicAndLegacyLatin() {
        checkDetail(index = 12, firstReference = "12:101", lastReference = "12:101")
    }

    @Test
    fun quranSourceEntry17ShowsOriginalArabicAndLegacyLatin() {
        checkDetail(index = 16, firstReference = "23:29", lastReference = "23:29")
    }

    @Test
    fun quranSourceEntry20ShowsOriginalArabicAndLegacyLatin() {
        checkDetail(index = 19, firstReference = "2:107", lastReference = "2:107")
    }

    @Test
    fun fetihOriginalArabicRemainsAccessibleInRtlLayout() {
        checkDetail(index = 15, firstReference = "48:1", lastReference = "48:10", rtl = true)
    }

    @Test
    fun healingShowsCitedBukhariArabicNotTanzil() {
        checkHadithDetail(index = 1, reference = "Sahih al-Bukhari 5675")
    }

    @Test
    fun repentanceShowsCitedTirmidhiArabicNotTanzil() {
        checkHadithDetail(index = 2, reference = "Jami at-Tirmidhi 3577")
    }

    @Test
    fun protectionShowsCitedAhmadArabicNotTanzil() {
        checkHadithDetail(index = 6, reference = "Musnad Ahmad 446")
    }

    @Test
    fun courageShowsCitedAbuDawudArabicNotTanzil() {
        checkHadithDetail(index = 18, reference = "Sunan Abi Dawud 3893")
    }

    private fun checkHadithDetail(index: Int, reference: String) {
        assumeTrue(BuildConfig.FLAVOR_NAME == "mucizedualar")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prayers = runBlocking {
            AssetMiraclesPrayerRepository(context, Dispatchers.IO).getPrayers()
        }
        assertEquals(20, prayers.size)
        val prayer = prayers[index]
        val hadith = requireNotNull(prayer.originalArabicSupplication)
        assertEquals(reference, hadith.reference)
        assertEquals(0, prayer.originalArabicVerses.size)
        composeRule.setContent {
            AppTheme(flavorName = "mucizedualar") {
                MiraclesDetailScreen(
                    uiState = MiraclesDetailUiState.Success(prayer, shouldShowAds = false),
                )
            }
        }
        val list = composeRule.onNodeWithTag("miracles_detail")
        composeRule.onNodeWithText(
            context.getString(
                com.parsfilo.contentapp.feature.content.R.string.miracles_hadith_source,
                reference,
            )
        ).assertIsDisplayed()
        composeRule.onNodeWithText(hadith.arabic).assertExists()
        composeRule.onNodeWithText(
            context.getString(com.parsfilo.contentapp.feature.content.R.string.miracles_quran_tanzil_attribution)
        ).assertDoesNotExist()
        list.performScrollToNode(hasText(prayer.duaArapca))
        composeRule.onNodeWithText(prayer.duaArapca).assertExists()
    }

    private fun checkDetail(
        index: Int,
        firstReference: String,
        lastReference: String,
        rtl: Boolean = false,
    ) {
        assumeTrue(BuildConfig.FLAVOR_NAME == "mucizedualar")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prayers = runBlocking {
            AssetMiraclesPrayerRepository(context, Dispatchers.IO).getPrayers()
        }
        assertEquals(20, prayers.size)
        val prayer = prayers[index]
        val first = prayer.originalArabicVerses.first()
        val last = prayer.originalArabicVerses.last()
        assertEquals(firstReference, first.reference)
        assertEquals(lastReference, last.reference)

        composeRule.setContent {
            AppTheme(flavorName = "mucizedualar") {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    MiraclesDetailScreen(
                        uiState = MiraclesDetailUiState.Success(prayer, shouldShowAds = false),
                    )
                }
            }
        }

        val list = composeRule.onNodeWithTag("miracles_detail")
        // Attribution is up front, including for long passages such as Fetih.
        composeRule.onNodeWithText(
            context.getString(com.parsfilo.contentapp.feature.content.R.string.miracles_quran_tanzil_attribution)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(firstReference).assertExists()
        composeRule.onNodeWithText(first.arabic).assertExists()

        list.performScrollToNode(hasText(lastReference))
        composeRule.onNodeWithText(lastReference).assertIsDisplayed()
        composeRule.onNodeWithText(last.arabic).assertExists()

        // Adding a source-verified Arabic verse must not hide the legacy Latin reading.
        list.performScrollToNode(hasText(prayer.duaBesmele))
        composeRule.onNodeWithText(prayer.duaBesmele).assertIsDisplayed()
        list.performScrollToNode(hasText(prayer.duaArapca))
        composeRule.onNodeWithText(prayer.duaArapca).assertExists()
    }
}
