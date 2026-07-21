package com.parsfilo.contentapp.ui

import com.parsfilo.contentapp.navigation.AppRoute
import org.junit.Assert.assertEquals
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

    private fun destination(route: AppRoute): BottomDestination {
        val result = bottomDestinations.firstOrNull { it.route == route }
        assertTrue("Destination must exist for ${route.route}", result != null)
        return requireNotNull(result)
    }
}
