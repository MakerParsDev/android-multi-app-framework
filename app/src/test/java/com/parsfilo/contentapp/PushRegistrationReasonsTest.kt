package com.parsfilo.contentapp

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PushRegistrationReasonsTest {
    @Test
    fun oneInitialRegistrationThenOnlyRealPreferenceChanges() = runTest {
        val reasons =
            flowOf(false, false, true, true, false)
                .pushRegistrationReasons()
                .toList()

        assertEquals(
            listOf("app_start", "notification_setting_changed", "notification_setting_changed"),
            reasons,
        )
    }

    @Test
    fun aNewActivitySubscriptionGetsItsOwnInitialRegistration() = runTest {
        val notifications = flowOf(true, true)

        assertEquals(listOf("app_start"), notifications.pushRegistrationReasons().toList())
        assertEquals(listOf("app_start"), notifications.pushRegistrationReasons().toList())
    }
}
