package com.parsfilo.contentapp.feature.quran.ui.bookmarks

import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.QuranBookmark
import com.parsfilo.contentapp.feature.quran.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic view and callbacks only; never touches a Quran database or installed user app. */
@RunWith(AndroidJUnit4::class)
class QuranBookmarksTouchTargetDeviceTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun tappingVerseLabelNavigatesWithoutRemovingBookmark() {
        val navigations = mutableListOf<Pair<Int, Int>>()
        val removals = mutableListOf<Pair<Int, Int>>()
        setScreen(navigations, removals)

        // Physical touch at the verse label's center, not a semantic performClick.
        rule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.quran_last_read_format, 2, 5),
            useUnmergedTree = true,
        ).performTouchInput { click() }

        rule.runOnIdle {
            assertTrue("A verse-label touch must never remove the saved bookmark", removals.isEmpty())
            assertEquals(listOf(2 to 5), navigations)
        }
    }

    @Test
    fun tappingExplicitRemoveActionOnlyRemovesBookmark() {
        val navigations = mutableListOf<Pair<Int, Int>>()
        val removals = mutableListOf<Pair<Int, Int>>()
        setScreen(navigations, removals)

        rule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.quran_remove_bookmark),
            useUnmergedTree = true,
        ).performTouchInput { click() }

        rule.runOnIdle {
            assertEquals(listOf(2 to 5), removals)
            assertTrue("Explicit remove must not open the reader", navigations.isEmpty())
        }
    }

    private fun setScreen(
        navigations: MutableList<Pair<Int, Int>>,
        removals: MutableList<Pair<Int, Int>>,
    ) {
        assertEquals(
            "Run synthetic touchscreen QA only in the disposable feature test host",
            "com.parsfilo.contentapp.feature.quran.test",
            InstrumentationRegistry.getInstrumentation().targetContext.packageName,
        )
        rule.setContent {
            AppTheme(flavorName = "kuran_kerim") {
                QuranBookmarksScreen(
                    state = QuranBookmarksUiState(
                        items = listOf(
                            QuranBookmarkItem(
                                bookmark = QuranBookmark(suraNumber = 2, ayahNumber = 5),
                                suraName = "Al-Baqara",
                            ),
                        ),
                    ),
                    onBack = {},
                    onAyahClick = { sura, ayah -> navigations.add(sura to ayah) },
                    onRemoveBookmark = { sura, ayah -> removals.add(sura to ayah) },
                )
            }
        }
    }
}
