package com.parsfilo.contentapp.feature.billing.ui

import com.parsfilo.contentapp.feature.billing.model.BillingProduct
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionCheckoutRoutingTest {
    private val plan = mockk<BillingProduct>(relaxed = true)

    @Test
    fun guestCheckoutSelectsSignInRatherThanPayment() {
        var signIns = 0
        var purchases = 0
        routeSubscriptionCheckout(
            isSignedIn = false,
            plan = plan,
            onSignInClick = { signIns++ },
            onPlanSelected = { purchases++ },
        )
        assertEquals(1, signIns)
        assertEquals(0, purchases)
    }

    @Test
    fun signedInCheckoutSelectsExactPlanWithoutAnotherLogin() {
        var signIns = 0
        var purchases = 0
        var selected: BillingProduct? = null
        routeSubscriptionCheckout(
            isSignedIn = true,
            plan = plan,
            onSignInClick = { signIns++ },
            onPlanSelected = {
                selected = it
                purchases++
            },
        )
        assertEquals(0, signIns)
        assertEquals(1, purchases)
        assertEquals(plan, selected)
    }
}
