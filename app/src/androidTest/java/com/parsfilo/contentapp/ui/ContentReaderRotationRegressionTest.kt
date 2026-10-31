package com.parsfilo.contentapp.ui

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.BuildConfig
import com.parsfilo.contentapp.MainActivity
import com.parsfilo.contentapp.feature.content.R
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The long reader must not jump back to verse 1 when its adaptive navigation host switches. */
@RunWith(AndroidJUnit4::class)
class ContentReaderRotationRegressionTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun lastVerseRemainsReachableAcrossRepeatedOrientationChanges() {
        assumeTrue(BuildConfig.FLAVOR_NAME == "vakiasuresi")

        val goLabel =
            InstrumentationRegistry.getInstrumentation().targetContext
                .getString(R.string.content_jump_action)
        try {
            rule.waitUntil(20_000) {
                rule.onAllNodesWithTag("content_go_to_verse").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("content_go_to_verse").performClick()
            rule.onNodeWithTag("content_verse_number").performTextInput("96")
            rule.onNodeWithText(goLabel).performClick()
            waitForLastVerses()

            repeat(3) {
                rule.activityRule.scenario.onActivity {
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                waitForLastVerses()
                rule.activityRule.scenario.onActivity {
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                waitForLastVerses()
            }
        } finally {
            rule.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    @Test
    fun localizedArabicKeyboardDigitsCanJumpToLastVerse() {
        assumeTrue(BuildConfig.FLAVOR_NAME == "vakiasuresi")
        val goLabel =
            InstrumentationRegistry.getInstrumentation().targetContext
                .getString(R.string.content_jump_action)
        rule.waitUntil(20_000) {
            rule.onAllNodesWithTag("content_go_to_verse").fetchSemanticsNodes().isNotEmpty()
        }
        listOf("٩٦", "۹۶").forEach { localizedId ->
            rule.onNodeWithTag("content_go_to_verse").performClick()
            rule.onNodeWithTag("content_verse_number").performTextInput(localizedId)
            rule.onNodeWithText(goLabel).assertIsEnabled().performClick()
            waitForLastVerses()
        }
    }

    private fun waitForLastVerses() {
        rule.waitUntil(15_000) {
            (94..96).any { verse ->
                rule.onAllNodesWithText(verse.toString()).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }
}
