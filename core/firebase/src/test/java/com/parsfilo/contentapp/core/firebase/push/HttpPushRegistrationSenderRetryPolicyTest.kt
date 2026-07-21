package com.parsfilo.contentapp.core.firebase.push

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.random.Random

class HttpPushRegistrationSenderRetryPolicyTest {

    @Test
    fun `should retry once for 5xx`() {
        assertTrue(shouldRetryPushRegistration(attempt = 0, statusCode = 500, throwable = null))
        assertFalse(shouldRetryPushRegistration(attempt = 1, statusCode = 500, throwable = null))
    }

    @Test
    fun `should not retry for 4xx`() {
        assertFalse(shouldRetryPushRegistration(attempt = 0, statusCode = 400, throwable = null))
    }

    @Test
    fun `should retry for io exception once`() {
        assertTrue(
            shouldRetryPushRegistration(
                attempt = 0,
                statusCode = null,
                throwable = IOException("network"),
            ),
        )
        assertFalse(
            shouldRetryPushRegistration(
                attempt = 1,
                statusCode = null,
                throwable = IOException("network"),
            ),
        )
    }

    @Test
    fun `should never retry on cancellation`() {
        assertFalse(
            shouldRetryPushRegistration(
                attempt = 0,
                statusCode = null,
                throwable = CancellationException("cancelled"),
            ),
        )
    }

    @Test
    fun `should not retry unknown failure state`() {
        assertFalse(
            shouldRetryPushRegistration(
                attempt = 0,
                statusCode = null,
                throwable = null,
            ),
        )
    }

    @Test
    fun `should not retry non io exception`() {
        assertFalse(
            shouldRetryPushRegistration(
                attempt = 0,
                statusCode = null,
                throwable = IllegalStateException("state"),
            ),
        )
    }

    @Test
    fun `retry status boundaries are limited to server errors`() {
        assertTrue(shouldRetryPushRegistration(0, 599, null))
        assertFalse(shouldRetryPushRegistration(0, 600, null))
        assertFalse(shouldRetryPushRegistration(0, 499, null))
    }

    @Test
    fun `retry delay stays within expected jitter window`() {
        repeat(20) {
            val delay = nextRetryDelayMillis(Random(it))
            assertTrue(delay in 300L..900L)
        }
    }
}
