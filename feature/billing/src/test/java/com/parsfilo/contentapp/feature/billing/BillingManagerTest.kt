package com.parsfilo.contentapp.feature.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsResult
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.model.SubscriptionState
import com.parsfilo.contentapp.feature.billing.model.BillingProduct
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
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
    fun catalogErrorSurvivesSuccessfulEmptyPurchaseQuery() = runBlocking {
        val response = slot<ProductDetailsResponseListener>()
        every { billingClient.queryProductDetailsAsync(any(), capture(response)) } just Runs
        manager.queryProductDetails()
        response.captured.onProductDetailsResponse(
            BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.NETWORK_ERROR)
                .build(),
            mockk(relaxed = true)
        )
        val processed = CompletableDeferred<Unit>()
        manager.onPurchaseResultProcessed = { processed.complete(Unit) }
        manager.handlePurchases(emptyList())
        withTimeout(5_000) { processed.await() }
        assertThat(manager.subscriptionState.value).isEqualTo(SubscriptionState.Inactive)
        assertThat(manager.availabilityError.value).isEqualTo(BillingErrorKeys.UNAVAILABLE)
    }

    @Test
    fun retryInvalidatesEarlierCatalogCallbackEvenOnSameReadyClient() {
        val responses = mutableListOf<ProductDetailsResponseListener>()
        every { billingClient.isReady } returns true
        every { billingClient.queryProductDetailsAsync(any(), capture(responses)) } just Runs
        manager.queryProductDetails()
        manager.retryConnection()
        assertThat(responses).hasSize(2)

        responses[0].onProductDetailsResponse(
            BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.NETWORK_ERROR)
                .build(),
            mockk(relaxed = true)
        )
        assertThat(manager.availabilityError.value).isNull()
        val catalog = mockk<QueryProductDetailsResult> {
            every { productDetailsList } returns emptyList()
            every { unfetchedProductList } returns emptyList()
        }
        responses[1].onProductDetailsResponse(
            BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.OK)
                .build(),
            catalog
        )
        assertThat(manager.availabilityError.value).isEqualTo(BillingErrorKeys.NO_PRODUCTS)
    }

    @Test
    fun activeVerifiedEntitlementSurvivesPlayCatalogFailure() = runBlocking {
        val purchase = mockk<Purchase> {
            every { purchaseState } returns Purchase.PurchaseState.PURCHASED
            every { isAcknowledged } returns true
            every { products } returns listOf("monthly_sub")
        }
        coEvery { purchaseVerifier.verify(any(), purchase) } returns VerificationResult(
            verified = true,
            expiryTimeMillis = 4102444800000L,
            isAutoRenewing = true,
            purchaseState = "ACTIVE",
            acknowledgementState = "ACKNOWLEDGED",
            error = null,
        )
        val processed = CompletableDeferred<Unit>()
        manager.onPurchaseResultProcessed = { processed.complete(Unit) }
        manager.handlePurchases(listOf(purchase))
        withTimeout(5_000) { processed.await() }

        val response = slot<ProductDetailsResponseListener>()
        every { billingClient.queryProductDetailsAsync(any(), capture(response)) } just Runs
        manager.queryProductDetails()
        response.captured.onProductDetailsResponse(
            BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.NETWORK_ERROR)
                .setDebugMessage("Offline")
                .build(),
            mockk(relaxed = true),
        )

        assertThat(manager.subscriptionState.value).isInstanceOf(SubscriptionState.Active::class.java)
        manager.retryConnection()
        assertThat(manager.subscriptionState.value).isInstanceOf(SubscriptionState.Active::class.java)
        coVerify(exactly = 0) { preferencesDataSource.setPremium(false) }
    }

    @Test
    fun staleCatalogCallbackCannotReplaceNewRetryState() {
        val oldClient = mockk<BillingClient>(relaxed = true)
        val newClient = mockk<BillingClient>(relaxed = true)
        // A connection retry rotates away from the stale client.
        var count = 0
        val factory = BillingClientFactory { _, _ ->
            if (++count == 1) oldClient else newClient
        }
        val refreshedManager = BillingManager(
            context, preferencesDataSource, purchaseVerifier, factory
        )
        val response = slot<ProductDetailsResponseListener>()
        every { oldClient.queryProductDetailsAsync(any(), capture(response)) } just Runs
        refreshedManager.queryProductDetails()
        refreshedManager.replaceBillingClient(closeCurrent = false, resetScope = false)

        response.captured.onProductDetailsResponse(
            BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.NETWORK_ERROR)
                .build(),
            mockk(relaxed = true)
        )
        assertThat(refreshedManager.subscriptionState.value).isEqualTo(SubscriptionState.Loading)
        assertThat(refreshedManager.availabilityError.value).isNull()
    }

    @Test
    fun setupFailureExitsLoadingWithLocalizedAvailabilityKey() {
        val setup = slot<BillingClientStateListener>()
        every { billingClient.isReady } returns false
        every { billingClient.startConnection(capture(setup)) } just Runs

        manager.startConnection()
        setup.captured.onBillingSetupFinished(
            BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
                .setDebugMessage("Service unavailable")
                .build()
        )

        assertThat(manager.availabilityError.value).isEqualTo(BillingErrorKeys.UNAVAILABLE)
        coVerify(exactly = 0) { preferencesDataSource.setPremium(false) }
    }

    @Test
    fun disconnectExitsLoadingWithReconnectingKey() {
        val setup = slot<BillingClientStateListener>()
        every { billingClient.isReady } returns false
        every { billingClient.startConnection(capture(setup)) } just Runs

        manager.startConnection()
        setup.captured.onBillingServiceDisconnected()

        assertThat(manager.availabilityError.value).isEqualTo(BillingErrorKeys.RECONNECTING)
    }

    @Test
    fun emptyPlayCatalogSurfacesNoEligiblePlans() {
        val response = slot<ProductDetailsResponseListener>()
        every { billingClient.queryProductDetailsAsync(any(), capture(response)) } just Runs
        val result = mockk<QueryProductDetailsResult> {
            every { productDetailsList } returns emptyList()
            every { unfetchedProductList } returns emptyList()
        }

        manager.queryProductDetails()
        response.captured.onProductDetailsResponse(
            BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.OK)
                .build(),
            result,
        )

        assertThat(manager.availabilityError.value).isEqualTo(BillingErrorKeys.NO_PRODUCTS)
        assertThat(manager.productDetails.value).isEmpty()
    }

    @Test
    fun productQueryFailureSurfacesUnavailable() {
        val response = slot<ProductDetailsResponseListener>()
        every { billingClient.queryProductDetailsAsync(any(), capture(response)) } just Runs

        manager.queryProductDetails()
        response.captured.onProductDetailsResponse(
            BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.NETWORK_ERROR)
                .setDebugMessage("Offline")
                .build(),
            mockk(relaxed = true),
        )

        assertThat(manager.availabilityError.value).isEqualTo(BillingErrorKeys.UNAVAILABLE)
    }

    @Test
    fun retryResetsErrorAndReplacesStalledConnection() {
        val setup = slot<BillingClientStateListener>()
        every { billingClient.isReady } returns false
        every { billingClient.startConnection(capture(setup)) } just Runs

        manager.startConnection()
        manager.retryConnection()

        assertThat(manager.subscriptionState.value).isEqualTo(SubscriptionState.Loading)
        verify(exactly = 2) { billingClient.startConnection(any()) }
        verify(exactly = 1) { billingClient.endConnection() }
        coVerify(exactly = 0) { preferencesDataSource.setPremium(false) }
    }

    @Test
    fun `signed out buyer cannot open Google Play checkout`() {
        every { purchaseVerifier.hasSignedInUser() } returns false

        manager.launchBillingFlow(mockk<Activity>(), mockk<BillingProduct>())

        assertThat(manager.subscriptionState.value)
            .isEqualTo(SubscriptionState.Error(BillingErrorKeys.AUTH_REQUIRED))
        verify(exactly = 0) { billingClient.launchBillingFlow(any(), any()) }
    }

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
            .isEqualTo(SubscriptionState.Error(BillingErrorKeys.VERIFICATION_UNAVAILABLE))
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
