package com.parsfilo.contentapp.core.firebase

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseHealthTelemetryTest {
    @Test
    fun emitsDistinctAttemptSuccessAndFailureEvents() {
        val analytics = mockk<AppAnalytics>(relaxed = true)
        val telemetry = ReleaseHealthTelemetry(analytics)

        ReleaseHealthOperation.entries.forEach { operation ->
            telemetry.attempt(operation)
            telemetry.result(operation, true)
            telemetry.result(operation, false)
            verify(exactly = 1) { analytics.logEvent(operation.attemptEvent, null) }
            verify(exactly = 1) { analytics.logEvent(operation.successEvent, null) }
            verify(exactly = 1) { analytics.logEvent(operation.failureEvent, null) }
        }
    }

    @Test
    fun operationEventsHaveUniqueNamesAndDoNotRequirePersonalParameters() {
        val names = ReleaseHealthOperation.entries.flatMap { operation ->
            listOf(operation.attemptEvent, operation.successEvent, operation.failureEvent)
        }
        assertEquals(9, names.size)
        assertEquals(names.size, names.toSet().size)
        assert(names.all { it.length <= 40 })
    }

    @Test
    fun unavailableAnalyticsNeverBlocksTheUserOperation() {
        val analytics = mockk<AppAnalytics>()
        every { analytics.logEvent(any(), any()) } throws IllegalStateException("Analytics unavailable")
        val telemetry = ReleaseHealthTelemetry(analytics)

        telemetry.attempt(ReleaseHealthOperation.BILLING_VERIFICATION)
        telemetry.result(ReleaseHealthOperation.BILLING_VERIFICATION, false)
    }
}
