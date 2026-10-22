package com.parsfilo.contentapp.ui

import com.parsfilo.contentapp.navigation.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBottomBarTest {
    @Test
    fun `bottom destinations have stable unique route ids`() {
        val ids = bottomDestinations.map(BottomDestination::id)

        assertEquals(ids.size, ids.distinct().size)
        assertEquals(
            listOf(
                AppRoute.Subscription.route,
                AppRoute.OtherApps.route,
                AppRoute.HomeGraph.route,
                AppRoute.MessagesGraph.route,
                AppRoute.NotificationsGraph.route,
            ),
            ids,
        )
    }

    @Test
    fun `badge text is capped at nine plus`() {
        assertNull(0.toBottomBarBadgeText())
        assertNull((-1).toBottomBarBadgeText())
        assertEquals("1", 1.toBottomBarBadgeText())
        assertEquals("9", 9.toBottomBarBadgeText())
        assertEquals("9+", 10.toBottomBarBadgeText())
    }

    @Test
    fun `only enabled destination badges expose counts`() {
        val notifications = destination(AppRoute.NotificationsGraph)
        val otherApps = destination(AppRoute.OtherApps)
        val messages = destination(AppRoute.MessagesGraph)
        val subscription = destination(AppRoute.Subscription)

        assertEquals("9+", notifications.badgeText(42, 7, 6, true))
        assertEquals("6", otherApps.badgeText(42, 7, 6, true))
        assertNull(messages.badgeText(42, 7, 6, true))
        assertNull(subscription.badgeText(42, 7, 6, true))
    }

    @Test
    fun `home destination never displays a badge`() {
        val home = destination(AppRoute.HomeGraph)

        assertNull(home.badgeText(42, 7, 6, true))
    }

    @Test
    fun `bottom labels adapt to narrow viewports and system font scale`() {
        // Five equal-weight destinations: keep readable single-line labels
        // where space permits, otherwise show fully described icons.
        val cases =
            listOf(
                Triple(320, 1.0f, false),
                Triple(320, 1.3f, false),
                Triple(320, 1.8f, false),
                Triple(320, 2.0f, false),
                Triple(360, 1.0f, false),
                Triple(360, 1.3f, false),
                Triple(360, 1.8f, false),
                Triple(360, 2.0f, false),
                Triple(411, 1.0f, true),
                Triple(411, 1.1f, true),
                Triple(411, 1.3f, false),
                Triple(411, 1.8f, false),
                Triple(411, 2.0f, false),
                Triple(480, 1.3f, true),
                Triple(700, 2.0f, false),
                Triple(720, 2.0f, true),
            )
        assertFalse(shouldShowBottomBarLabels(411, 1.1f, horizontalNavInsetDp = 16f))
        cases.forEach { (widthDp, scale, expectedLabels) ->
            assertEquals(
                "Unexpected label layout at ${widthDp}dp and ${scale}x font",
                expectedLabels,
                shouldShowBottomBarLabels(widthDp, scale),
            )
        }
    }

    @Test
    fun `five icon only destinations each have at least 48dp in minimum viewport`() {
        assertFalse(shouldShowBottomBarLabels(320, 1.0f))
        assertTrue((320 - 8 * (bottomDestinations.size - 1)) / bottomDestinations.size >= 48)
    }

    private fun destination(route: AppRoute): BottomDestination {
        val result = bottomDestinations.firstOrNull { it.route == route }
        assertTrue("Destination must exist for ${route.route}", result != null)
        return requireNotNull(result)
    }
}
