package com.parsfilo.contentapp.feature.quran.ui.component

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.feature.quran.R
import com.parsfilo.contentapp.feature.quran.ui.suradetail.QuranSuraDetailScreen
import com.parsfilo.contentapp.feature.quran.ui.suradetail.QuranSuraDetailUiState
import com.parsfilo.contentapp.feature.quran.ui.surelist.QuranSuraListScreen
import com.parsfilo.contentapp.feature.quran.ui.surelist.SuraListViewModelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated Compose host: checks touch targets without modifying the installed Play app. */
@RunWith(AndroidJUnit4::class)
class QuranHeaderTouchTargetDeviceTest {
    @get:Rule val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun assertMinimumTouchTarget(description: String) {
        val bounds = rule.onNodeWithContentDescription(description)
            .assertHasClickAction()
            .fetchSemanticsNode()
            .boundsInRoot
        val density = context.resources.displayMetrics.density
        assertTrue("$description width must be at least 48dp", bounds.width / density >= 47.5f)
        assertTrue("$description height must be at least 48dp", bounds.height / density >= 47.5f)
    }

    @Test
    fun detailHeaderOffersAccessibleBackBookmarksAndReciterTargets() {
        var backCalls = 0
        var bookmarkCalls = 0
        rule.setContent {
            AppTheme(flavorName = "kuran_kerim") {
                QuranSuraDetailScreen(
                    state = QuranSuraDetailUiState(isLoading = false),
                    onBack = { backCalls++ },
                    onBookmarksClick = { bookmarkCalls++ },
                    onRetry = {},
                    onDisplayModeSelected = {},
                    onFontSizeChange = {},
                    onReciterSelected = {},
                    onPlayPauseAyah = { _, _ -> },
                    onToggleBookmark = {},
                    onAyahVisible = {},
                )
            }
        }
        assertMinimumTouchTarget(context.getString(R.string.quran_back))
        assertMinimumTouchTarget(context.getString(R.string.quran_open_bookmarks))
        assertMinimumTouchTarget(context.getString(R.string.quran_select_reciter))
        rule.onNodeWithContentDescription(context.getString(R.string.quran_back)).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.quran_open_bookmarks)).performClick()
        rule.runOnIdle {
            assertEquals(1, backCalls)
            assertEquals(1, bookmarkCalls)
        }
    }

    @Test
    fun suraListHeaderOffersAccessibleSettingsAndOverflowTargets() {
        var settingsCalls = 0
        rule.setContent {
            AppTheme(flavorName = "kuran_kerim") {
                QuranSuraListScreen(
                    state = SuraListViewModelState(isLoading = false),
                    onSuraClick = {},
                    onLastReadClick = { _, _ -> },
                    onBookmarksClick = {},
                    onSettingsClick = { settingsCalls++ },
                    onSearch = {},
                    onRetry = {},
                )
            }
        }
        assertMinimumTouchTarget(context.getString(R.string.quran_open_settings))
        assertMinimumTouchTarget(context.getString(R.string.quran_more_options))
        rule.onNodeWithContentDescription(context.getString(R.string.quran_open_settings)).performClick()
        rule.runOnIdle { assertEquals(1, settingsCalls) }
    }
}
