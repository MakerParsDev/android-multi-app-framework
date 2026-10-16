package com.parsfilo.contentapp.feature.notifications.service

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseMessageProcessingTest {
    @Test
    fun callbackWaitsUntilSuspendingNotificationWorkFinishes() {
        var persisted = false
        awaitFirebaseMessageProcessing {
            delay(25)
            persisted = true
        }
        assertTrue("onMessageReceived must not return before persistence", persisted)
    }

    @Test
    fun processingHasBoundedTimeBudget() {
        assertThrows(TimeoutCancellationException::class.java) {
            awaitFirebaseMessageProcessing(timeoutMs = 30) {
                delay(500)
            }
        }
    }

    @Test
    fun failuresPropagateToServiceErrorHandler() {
        assertThrows(IllegalStateException::class.java) {
            awaitFirebaseMessageProcessing {
                throw IllegalStateException("test-only")
            }
        }
    }
}
