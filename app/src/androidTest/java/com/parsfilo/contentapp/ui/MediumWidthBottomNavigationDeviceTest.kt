package com.parsfilo.contentapp.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parsfilo.contentapp.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Check the actual app host, not an isolated bottom bar preview. */
@RunWith(AndroidJUnit4::class)
class MediumWidthBottomNavigationDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun landscapePhoneKeeps68DpBottomNavigationAndFiveTargets() {
        try {
            rule.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            rule.waitUntil(20_000) {
                rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                    rule.onAllNodesWithTag("primary_navigation").fetchSemanticsNodes().isNotEmpty()
            }
            val widthDp = rule.activity.resources.configuration.screenWidthDp
            assumeTrue("Requires a 600-839dp medium-width landscape window", widthDp in 600..839)
            rule.waitForIdle()

            val barHeightPixels =
                rule.onNodeWithTag("primary_navigation").fetchSemanticsNode().boundsInRoot.height
            val expectedPixels = 68f * rule.activity.resources.displayMetrics.density
            assertEquals(
                "Medium phones must use the same 68dp bottom bar as portrait",
                expectedPixels,
                barHeightPixels,
                2.5f,
            )

            val targets = rule.onAllNodes(
                hasClickAction() and hasAnyAncestor(hasTestTag("primary_navigation")),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            assertEquals("All five navigation targets must remain accessible", 5, targets.size)
        } finally {
            rule.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}
