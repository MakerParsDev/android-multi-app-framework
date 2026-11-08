package com.parsfilo.contentapp.ui

import androidx.compose.ui.test.assertIsDisplayed
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

/** Validate that moving verse actions into the overflow keeps the reading workflow intact. */
@RunWith(AndroidJUnit4::class)
class YasinReaderActionsDeviceTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun jumpToVerseTwelveAndSavePlaceFromReaderTools() {
        assumeTrue(BuildConfig.FLAVOR_NAME == "yasinsuresi")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val goLabel = context.getString(R.string.content_jump_action)

        rule.waitUntil(30_000) {
            rule.onAllNodesWithTag("content_reader_tools").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("content_reader_tools").assertIsDisplayed().performClick()
        rule.onNodeWithTag("content_go_to_verse").performClick()
        rule.onNodeWithTag("content_verse_number").performTextInput("12")
        rule.onNodeWithText(goLabel).performClick()

        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("12").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("content_reader_tools").performClick()
        rule.onNodeWithTag("content_save_current_verse").performClick()
        rule.onNodeWithTag("content_reader_tools").performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithTag("content_resume_reading").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("content_resume_reading").performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("12").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
