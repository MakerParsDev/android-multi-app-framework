package com.parsfilo.contentapp.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies section-scoped copying with the actual bundled Mucize Dualar prayers. */
@RunWith(AndroidJUnit4::class)
class MiraclesCopyAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun longQuranArabicAndLegacyLatinCopySeparatelyAtDoubleFontScale() {
        verifyCopy(index = 15, doubledFont = true)
    }

    @Test
    fun hadithSourceArabicAndLatinCopySeparately() {
        verifyCopy(index = 1)
    }

    @Test
    fun weakSourceArabicAndLatinCopySeparately() {
        verifyCopy(index = 4)
    }

    private fun verifyCopy(index: Int, doubledFont: Boolean = false) {
        assumeTrue(BuildConfig.FLAVOR_NAME == "mucizedualar")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val prayers = runBlocking {
            AssetMiraclesPrayerRepository(context, Dispatchers.IO).getPrayers()
        }
        val prayer = prayers[index]
        val originalArabic = if (prayer.originalArabicVerses.isNotEmpty()) {
            prayer.originalArabicVerses.joinToString("\n") { it.arabic }
        } else {
            when (index) {
                1 -> requireNotNull(prayer.originalArabicSupplication).arabic
                4 -> requireNotNull(prayer.originalArabicVeryWeakHadithSupplication).arabic
                else -> error("Unsupported test fixture")
            }
        }
        try {
            rule.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, if (doubledFont) 2f else density.fontScale)
                ) {
                    AppTheme(flavorName = "mucizedualar") {
                        MiraclesDetailScreen(
                            uiState = MiraclesDetailUiState.Success(prayer, shouldShowAds = false)
                        )
                    }
                }
            }

            val reader = rule.onNodeWithTag("miracles_detail")
            reader.performScrollToNode(hasTestTag("miracles_copy_arabic"))
            val arabicCopy = rule.onNodeWithTag("miracles_copy_arabic", useUnmergedTree = true)
                .fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
            assertEquals(
                context.getString(com.parsfilo.contentapp.feature.content.R.string.content_copy_arabic),
                arabicCopy.label,
            )
            rule.runOnIdle { assertTrue(arabicCopy.action()) }
            rule.waitForIdle()
            assertTrue(clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() == originalArabic)

            reader.performScrollToNode(hasTestTag("miracles_copy_latin"))
            val latinCopy = rule.onNodeWithTag("miracles_copy_latin", useUnmergedTree = true)
                .fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
            assertEquals(
                context.getString(com.parsfilo.contentapp.feature.content.R.string.content_copy_latin),
                latinCopy.label,
            )
            rule.runOnIdle { assertTrue(latinCopy.action()) }
            rule.waitForIdle()
            assertEquals(prayer.duaArapca, clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString())
        } finally {
            clipboard.clearPrimaryClip()
        }
    }
}
