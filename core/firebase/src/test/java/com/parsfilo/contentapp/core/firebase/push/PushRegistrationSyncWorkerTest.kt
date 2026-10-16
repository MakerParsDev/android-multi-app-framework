package com.parsfilo.contentapp.core.firebase.push

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
}
