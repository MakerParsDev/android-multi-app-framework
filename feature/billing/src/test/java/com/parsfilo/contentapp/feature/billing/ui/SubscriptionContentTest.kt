package com.parsfilo.contentapp.feature.billing.ui

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.model.SubscriptionState
import com.parsfilo.contentapp.feature.billing.BillingErrorKeys
import org.junit.Test

class SubscriptionContentTest {
    @Test
    fun loadingHasBoundedFallback() {
        assertThat(resolveSubscriptionContent(SubscriptionState.Loading, false, false))
            .isEqualTo(SubscriptionContent.LOADING)
        assertThat(resolveSubscriptionContent(SubscriptionState.Loading, false, true))
            .isEqualTo(SubscriptionContent.TIMED_OUT)
    }

    @Test
    fun emptyCatalogBecomesActionable() {
        assertThat(resolveSubscriptionContent(SubscriptionState.Inactive, false, false))
            .isEqualTo(SubscriptionContent.LOADING)
        assertThat(resolveSubscriptionContent(SubscriptionState.Inactive, false, true))
            .isEqualTo(SubscriptionContent.TIMED_OUT)
    }

    @Test
    fun explicitNoPlansResultOverridesTimeoutWithoutChangingOwnership() {
        assertThat(
            resolveSubscriptionContent(
                SubscriptionState.Inactive, false, true, BillingErrorKeys.NO_PRODUCTS
            )
        ).isEqualTo(SubscriptionContent.AVAILABILITY_ERROR)
    }

    @Test
    fun plansAndActiveEntitlementOverrideTimeout() {
        assertThat(resolveSubscriptionContent(SubscriptionState.Inactive, true, true))
            .isEqualTo(SubscriptionContent.PLANS)
        assertThat(
            resolveSubscriptionContent(SubscriptionState.Active(4102444800000L, true), false, true)
        ).isEqualTo(SubscriptionContent.ACTIVE)
    }

    @Test
    fun errorsOfferRetryRegardlessOfCachedPlans() {
        assertThat(
            resolveSubscriptionContent(SubscriptionState.Error("BILLING_UNAVAILABLE"), true, false)
        ).isEqualTo(SubscriptionContent.ERROR)
    }
}
