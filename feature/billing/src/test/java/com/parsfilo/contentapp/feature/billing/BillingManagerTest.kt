package com.parsfilo.contentapp.feature.billing

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.model.SubscriptionState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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
