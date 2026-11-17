package com.parsfilo.contentapp.core.firebase.push

import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PushRegistrationSyncWorkerTest {
    @Test
    fun `immediate retry is not expedited and retains network and reason constraints`() {
        val request = PushRegistrationSyncWorker.buildImmediateWorkRequest("retry_after_app_start")

        assertFalse(request.workSpec.expedited)
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals("retry_after_app_start", request.workSpec.input.getString("reason"))
        assertTrue(request.workSpec.input.getBoolean("is_immediate", false))
    }

    @Test
    fun `repeated foreground failures preserve the same bounded immediate retry`() {
        assertEquals(ExistingWorkPolicy.KEEP, PushRegistrationSyncWorker.IMMEDIATE_CONFLICT_POLICY)
    }

    @Test
    fun immediateRegistrationBoundsFailedAttemptsButNotPeriodicRecovery() {
        fun kind(success: Boolean, immediate: Boolean, attempt: Int): Class<*> =
            PushRegistrationSyncWorker.resultForAttempt(success, immediate, attempt)::class.java

        val retry = androidx.work.ListenableWorker.Result.retry()::class.java
        val failure = androidx.work.ListenableWorker.Result.failure()::class.java
        val success = androidx.work.ListenableWorker.Result.success()::class.java

        assertEquals(retry, kind(false, true, 0))
        assertEquals(retry, kind(false, true, 3))
        assertEquals(failure, kind(false, true, 4))
        assertEquals(failure, kind(false, true, 9))
        assertEquals(retry, kind(false, false, 9))
        assertEquals(success, kind(true, true, 9))
        assertEquals(success, kind(true, false, 9))
    }
}
