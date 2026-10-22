package com.parsfilo.contentapp.feature.billing

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.model.SubscriptionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BillingManagerTest {

    private val context = mockk<Context> {
        every { packageName } returns "com.parsfilo.test"
    }
    private val preferencesDataSource = mockk<PreferencesDataSource>(relaxed = true)
    private val purchaseVerifier = mockk<BillingPurchaseVerifier>()
    private val billingClient = mockk<BillingClient>(relaxed = true)
    private val billingClientFactory = BillingClientFactory { _, _ -> billingClient }

    private val manager = BillingManager(
        appContext = context,
        preferencesDataSource = preferencesDataSource,
        billingPurchaseVerifier = purchaseVerifier,
        billingClientFactory = billingClientFactory,
    )

    @Test
    fun `older verification cannot replace a newer purchase state`() = runBlocking {
        val delayed = mockk<Purchase> {
            every { purchaseState } returns Purchase.PurchaseState.PURCHASED
            every { isAcknowledged } returns true
            every { products } returns listOf("monthly_sub")
        }
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val processed = Channel<Unit>(Channel.UNLIMITED)
        manager.onPurchaseResultProcessed = { processed.trySend(Unit) }
        coEvery { purchaseVerifier.verify(any(), delayed) } coAnswers {
            started.complete(Unit)
            release.await()
            VerificationResult(true, 4102444800000L, true, "ACTIVE", "ACKNOWLEDGED", null)
        }
        try {
            manager.handlePurchases(listOf(delayed))
            withTimeout(5_000) { started.await() }
            manager.handlePurchases(emptyList())
            withTimeout(5_000) { processed.receive() }
        } finally {
            release.complete(Unit)
        }
        withTimeout(5_000) { processed.receive() }
        assertThat(manager.subscriptionState.value).isEqualTo(SubscriptionState.Inactive)
        coVerify(exactly = 0) { preferencesDataSource.setPremium(true) }
    }

    @Test
    fun `temporary outage preserves a still-valid verified subscription`() = runBlocking {
        val purchase = mockk<Purchase> {
            every { purchaseState } returns Purchase.PurchaseState.PURCHASED
            every { isAcknowledged } returns true
            every { products } returns listOf("monthly_sub")
        }
        val secondVerification = CompletableDeferred<Unit>()
        val processed = Channel<Unit>(Channel.UNLIMITED)
        manager.onPurchaseResultProcessed = { processed.trySend(Unit) }
        var calls = 0
        coEvery { purchaseVerifier.verify(any(), purchase) } coAnswers {
            if (++calls == 1) {
                VerificationResult(true, 4102444800000L, true, "ACTIVE", "ACKNOWLEDGED", null)
            } else {
                secondVerification.complete(Unit)
                VerificationResult(false, null, false, "NETWORK_ERROR", "UNKNOWN", "Network unavailable")
            }
        }
        manager.handlePurchases(listOf(purchase))
        withTimeout(5_000) { processed.receive() }
        manager.handlePurchases(listOf(purchase))
        withTimeout(5_000) { secondVerification.await() }
        withTimeout(5_000) { processed.receive() }
        assertThat(manager.subscriptionState.value).isInstanceOf(SubscriptionState.Active::class.java)
        coVerify(exactly = 0) { preferencesDataSource.setPremium(false) }
    }

    @Test
    fun `verified entitlement is stored before acknowledging the purchase`() = runBlocking {
        val purchase = mockk<Purchase> {
            every { purchaseState } returns Purchase.PurchaseState.PURCHASED
            every { isAcknowledged } returns false
            every { products } returns listOf("monthly_sub")
            every { purchaseToken } returns "token_abc"
        }
        coEvery { purchaseVerifier.verify(any(), purchase) } returns VerificationResult(
            true, 4102444800000L, true, "ACTIVE", "PENDING", null,
        )
        val acknowledged = CompletableDeferred<Unit>()
        every { billingClient.acknowledgePurchase(any(), any()) } answers {
            acknowledged.complete(Unit)
        }
        manager.handlePurchases(listOf(purchase))
        withTimeout(5_000) { acknowledged.await() }
        coVerifyOrder {
            preferencesDataSource.setPremium(true)
            billingClient.acknowledgePurchase(any(), any())
        }
    }

    @Test
    fun `empty purchases resolve to inactive`() = runTest {
        val state = manager.resolveSubscriptionStateForPurchases(emptyList())

        assertThat(state).isEqualTo(SubscriptionState.Inactive)
    }

    @Test
    fun `verification failure resolves to inactive`() = runTest {
        val purchase = mockk<Purchase> {
            every { purchaseState } returns Purchase.PurchaseState.PURCHASED
            every { isAcknowledged } returns true
            every { products } returns listOf("monthly_sub")
            every { purchaseToken } returns "token_abc"
        }
        coEvery {
            purchaseVerifier.verify(any(), purchase)
        } returns VerificationResult(
            verified = false,
            expiryTimeMillis = null,
            isAutoRenewing = false,
            purchaseState = "INVALID",
            acknowledgementState = "UNKNOWN",
            error = "invalid",
        )

        val state = manager.resolveSubscriptionStateForPurchases(listOf(purchase))

        assertThat(state).isEqualTo(SubscriptionState.Inactive)
    }

    @Test
    fun `temporary verification outage is not treated as an invalid purchase`() = runTest {
        val purchase = mockk<Purchase> {
            every { purchaseState } returns Purchase.PurchaseState.PURCHASED
            every { isAcknowledged } returns false
            every { products } returns listOf("monthly_sub")
        }
        coEvery { purchaseVerifier.verify(any(), purchase) } returns VerificationResult(
            verified = false, expiryTimeMillis = null, isAutoRenewing = false,
            purchaseState = "NETWORK_ERROR", acknowledgementState = "UNKNOWN",
            error = "Network error while verifying purchase",
        )
        assertThat(manager.resolveSubscriptionStateForPurchases(listOf(purchase)))
            .isEqualTo(SubscriptionState.Error("BILLING_VERIFICATION_UNAVAILABLE"))
        verify(exactly = 0) { billingClient.acknowledgePurchase(any(), any()) }
    }

    @Test
    fun `verification never acknowledges a purchase before entitlement is applied`() = runTest {
        val purchase = mockk<Purchase> {
            every { purchaseState } returns Purchase.PurchaseState.PURCHASED
            every { isAcknowledged } returns false
            every { products } returns listOf("monthly_sub")
        }
        coEvery { purchaseVerifier.verify(any(), purchase) } returns VerificationResult(
            verified = true, expiryTimeMillis = 4102444800000L, isAutoRenewing = true,
            purchaseState = "SUBSCRIPTION_STATE_ACTIVE", acknowledgementState = "PENDING",
            error = null,
        )
        assertThat(manager.resolveSubscriptionStateForPurchases(listOf(purchase)))
            .isInstanceOf(SubscriptionState.Active::class.java)
        verify(exactly = 0) { billingClient.acknowledgePurchase(any(), any()) }
    }

    @Test
    fun `verified purchase resolves to active with expiry`() = runTest {
        val purchase = mockk<Purchase> {
            every { purchaseState } returns Purchase.PurchaseState.PURCHASED
            every { isAcknowledged } returns true
            every { products } returns listOf("monthly_sub")
            every { purchaseToken } returns "token_abc"
        }
        coEvery {
            purchaseVerifier.verify(any(), purchase)
        } returns VerificationResult(
            verified = true,
            expiryTimeMillis = 123456789L,
            isAutoRenewing = true,
            purchaseState = "PURCHASED",
            acknowledgementState = "ACKNOWLEDGED",
            error = null,
        )

        val state = manager.resolveSubscriptionStateForPurchases(listOf(purchase))

        assertThat(state).isEqualTo(
            SubscriptionState.Active(
                expiryDate = 123456789L,
                isAutoRenewing = true,
            )
        )
    }
}
