package com.parsfilo.contentapp.feature.billing

import com.android.billingclient.api.Purchase
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.common.truth.Truth.assertThat
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.parsfilo.contentapp.core.firebase.RuntimeFailure
import com.parsfilo.contentapp.core.firebase.RuntimeObservability
import com.parsfilo.contentapp.core.firebase.RuntimeSignal
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BillingPurchaseVerifierTest {

    private val firebaseAuth = mockk<FirebaseAuth>()
    private val firebaseAppCheck = mockk<FirebaseAppCheck>(relaxed = true)
    private val runtimeObservability = mockk<RuntimeObservability>(relaxed = true)
    private val purchase = mockk<Purchase>(relaxed = true)

    @Test
    fun blankVerificationUrlReturnsMisconfigured() = runTest {
        val verifier = BillingPurchaseVerifier(
            firebaseAuth = firebaseAuth,
            firebaseAppCheck = firebaseAppCheck,
            runtimeObservability = runtimeObservability,
            verificationUrl = "",
        )

        val result = verifier.verify("com.parsfilo.test", purchase)

        assertThat(result.verified).isFalse()
        assertThat(result.purchaseState).isEqualTo("MISCONFIGURED")
        verify {
            runtimeObservability.recordFailure(
                match<RuntimeFailure> { failure ->
                    failure.signal == RuntimeSignal.BILLING_PURCHASE_VERIFICATION &&
                        failure.code == "MISCONFIGURED" &&
                        failure.message == "Purchase verification URL is missing" &&
                        failure.cause == null
                },
            )
        }
    }

    @Test
    fun taskAwaiterReturnsSuccessfulResult() = runTest {
        val result = BillingPurchaseVerifier.awaitTaskResult(Tasks.forResult("token"))

        assertThat(result).isEqualTo("token")
    }

    @Test
    fun taskAwaiterPropagatesFailure() = runTest {
        val expected = IllegalStateException("token failure")
        val failure = runCatching {
            BillingPurchaseVerifier.awaitTaskResult(Tasks.forException<String>(expected))
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
        assertThat(failure).hasMessageThat().isEqualTo(expected.message)
    }

    @Test
    fun taskAwaiterUsesFallbackWhenFailureHasNoException() = runTest {
        val task = mockk<Task<String>>()
        every { task.isSuccessful } returns false
        every { task.isCanceled } returns false
        every { task.exception } returns null
        every { task.addOnCompleteListener(any<Executor>(), any()) } answers {
            secondArg<OnCompleteListener<String>>().onComplete(task)
            task
        }

        val failure = runCatching {
            BillingPurchaseVerifier.awaitTaskResult(task)
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
        assertThat(failure).hasMessageThat().isEqualTo("Google Task failed without an exception")
        verify(exactly = 0) { task.result }
    }

    @Test
    fun taskAwaiterPropagatesCancellation() = runTest {
        val task = Tasks.forCanceled<String>()

        val failure = runCatching {
            BillingPurchaseVerifier.awaitTaskResult(task)
        }.exceptionOrNull()

        assertThat(task.isCanceled).isTrue()
        assertThat(failure).isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun missingAuthUserReturnsAuthRequired() = runTest {
        every { firebaseAuth.currentUser } returns null
        val verifier = BillingPurchaseVerifier(
            firebaseAuth = firebaseAuth,
            firebaseAppCheck = firebaseAppCheck,
            runtimeObservability = runtimeObservability,
            verificationUrl = "https://example.com/verify",
        )

        val result = verifier.verify("com.parsfilo.test", purchase)

        assertThat(result.verified).isFalse()
        assertThat(result.purchaseState).isEqualTo("AUTH_REQUIRED")
        verify(exactly = 0) {
            runtimeObservability.recordFailure(any())
        }
    }
}
