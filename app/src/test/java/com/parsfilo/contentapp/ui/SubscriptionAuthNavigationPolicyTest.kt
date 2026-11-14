package com.parsfilo.contentapp.ui

import com.parsfilo.contentapp.feature.auth.R
import com.parsfilo.contentapp.feature.auth.ui.AuthPurpose
import com.parsfilo.contentapp.navigation.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionAuthNavigationPolicyTest {
    @Test
    fun subscriptionSignInHasItsOwnRouteWithinProtectedSubscriptionNamespace() {
        assertEquals("subscription/auth", AppRoute.SubscriptionAuth.route)
        assertTrue(AppRoute.SubscriptionAuth.route.startsWith(AppRoute.Subscription.route + "/"))
        assertNotEquals(AppRoute.Auth.route, AppRoute.SubscriptionAuth.route)
    }

    @Test
    fun subscriptionSignInHasPurchaseSpecificCopyInsteadOfMessagesCopy() {
        assertEquals(R.string.auth_subscription_title, AuthPurpose.SUBSCRIPTION.titleRes)
        assertEquals(R.string.auth_subscription_description, AuthPurpose.SUBSCRIPTION.descriptionRes)
        assertEquals(R.string.auth_subscription_value_short, AuthPurpose.SUBSCRIPTION.valueRes)
        assertNotEquals(AuthPurpose.MESSAGES.titleRes, AuthPurpose.SUBSCRIPTION.titleRes)
        assertNotEquals(AuthPurpose.MESSAGES.descriptionRes, AuthPurpose.SUBSCRIPTION.descriptionRes)
    }

    @Test
    fun regularMessageSignInKeepsItsExistingCopy() {
        assertEquals(R.string.auth_title, AuthPurpose.MESSAGES.titleRes)
        assertEquals(R.string.auth_description, AuthPurpose.MESSAGES.descriptionRes)
        assertEquals(R.string.auth_value_short, AuthPurpose.MESSAGES.valueRes)
    }
}
