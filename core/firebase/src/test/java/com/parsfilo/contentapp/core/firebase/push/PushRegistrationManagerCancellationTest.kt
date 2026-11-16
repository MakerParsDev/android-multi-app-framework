package com.parsfilo.contentapp.core.firebase.push

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.firebase.ReleaseHealthOperation
import com.parsfilo.contentapp.core.firebase.ReleaseHealthTelemetry
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PushRegistrationManagerCancellationTest {
    @Test
    fun cancellationPropagatesWithoutFabricatingRegistrationFailure() = runTest {
        val preferences = mockk<PreferencesDataSource>()
        val telemetry = mockk<ReleaseHealthTelemetry>(relaxed = true)
        coEvery { preferences.getOrCreateInstallationId() } throws CancellationException("worker cancelled")
        val manager = PushRegistrationManager(
            context = mockk<Context>(),
            firebaseMessaging = mockk<FirebaseMessaging>(),
            preferencesDataSource = preferences,
            pushRegistrationSender = mockk<PushRegistrationSender>(),
            crashlytics = mockk<FirebaseCrashlytics>(relaxed = true),
            releaseHealthTelemetry = telemetry,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        var cancellation: Throwable? = null
        try {
            manager.syncRegistration(reason = "unit_test", scheduleRetryOnFailure = false)
        } catch (expected: CancellationException) {
            cancellation = expected
        }

        assertTrue(cancellation is CancellationException)
        verify(exactly = 1) { telemetry.attempt(ReleaseHealthOperation.PUSH_REGISTRATION) }
        verify(exactly = 0) { telemetry.result(any(), any()) }
    }
}
