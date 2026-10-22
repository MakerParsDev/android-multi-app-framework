package com.parsfilo.contentapp.ui

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomBarAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun at320dpAnd200PercentIconsRetainFullAccessibleNamesAndTouchTargets() {
        showNavigation(widthDp = 320, fontScale = 2.0f)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithTag("primary_navigation").assertExists()
        val names =
            listOf(
                R.string.nav_premium,
                R.string.nav_apps,
                R.string.nav_home,
                R.string.nav_messages,
                R.string.nav_alerts,
            ).map(context::getString)
        names.forEach { fullName ->
            rule.onNodeWithContentDescription(fullName, useUnmergedTree = true).assertExists()
            rule.onNodeWithText(fullName, useUnmergedTree = true).assertDoesNotExist()
        }
        val items =
            rule.onAllNodes(
                hasClickAction() and hasAnyAncestor(hasTestTag("primary_navigation")),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
        val selectedItems =
            rule.onAllNodes(
                isSelected() and hasAnyAncestor(hasTestTag("primary_navigation")),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
        // Compose exposes all five semantic click targets, including the
        // selected tab. UIAutomator may report the selected tab as non-clickable.
        assertEquals(5, items.size)
        assertEquals(1, selectedItems.size)
        val minimumTouchPixels = 48f * context.resources.displayMetrics.density
        items.forEach { item ->
            assertTrue(item.boundsInRoot.width >= minimumTouchPixels)
            assertTrue(item.boundsInRoot.height >= minimumTouchPixels)
        }
    }

    @Test
    fun at411dpAnd100PercentReadableLabelsRemainVisible() {
        showNavigation(widthDp = 411, fontScale = 1.0f)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(
            R.string.nav_premium,
            R.string.nav_apps,
            R.string.nav_home,
            R.string.nav_messages,
            R.string.nav_alerts,
        ).forEach { titleRes ->
            rule.onNodeWithText(context.getString(titleRes), useUnmergedTree = true).assertExists()
        }
    }

    private fun showNavigation(widthDp: Int, fontScale: Float) {
        rule.setContent {
            val config =
                Configuration(LocalConfiguration.current).apply {
                    screenWidthDp = widthDp
                    this.fontScale = fontScale
                }
            CompositionLocalProvider(LocalConfiguration provides config) {
                MaterialTheme {
                    AppBottomBarWithFab(
                        currentDestination = null,
                        onNavigateToDestination = {},
                        unreadNotificationCount = 0,
                        unreadMessageCount = 0,
                        newOtherAppsCount = 0,
                        shouldShowSubscriptionBadge = false,
                    )
                }
            }
        }
    }
}
