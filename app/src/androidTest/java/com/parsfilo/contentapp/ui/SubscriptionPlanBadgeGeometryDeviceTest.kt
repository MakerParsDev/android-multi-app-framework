package com.parsfilo.contentapp.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.billingclient.api.ProductDetails
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.SubscriptionState
import com.parsfilo.contentapp.feature.billing.R
import com.parsfilo.contentapp.feature.billing.model.BillingProduct
import com.parsfilo.contentapp.feature.billing.ui.SubscriptionScreen
import com.parsfilo.contentapp.feature.billing.ui.SubscriptionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the actual subscription plan card layout without opening BillingClient or
 * making a purchase. The synthetic ProductDetails payload is never sent to Google Play.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionPlanBadgeGeometryDeviceTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun selectedBadgeDoesNotCoverPriceAtRegularTextScale() = verifySelection(fontScale = 1f)

    @Test
    fun selectedBadgeDoesNotCoverPriceAtDoubleTextScale() = verifySelection(fontScale = 2f)

    private fun verifySelection(fontScale: Float) {
        val price = "₺159,99 / ay"
        val selected = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.billing_selected_badge)
        var checkoutCalls = 0
        val product = sampleProduct(price)

        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale)
            ) {
                AppTheme(flavorName = "ayetelkursi") {
                    SubscriptionScreen(
                        uiState = SubscriptionUiState(
                            subscriptionState = SubscriptionState.Inactive,
                            productDetails = listOf(product),
                            isSignedIn = true,
                        ),
                        onPlanSelected = { checkoutCalls++ },
                    )
                }
            }
        }

        rule.onNodeWithText(price, useUnmergedTree = true)
            .assertExists()
            .performScrollTo()
            .performTouchInput { click() }
        val badge = rule.onNodeWithText(selected, useUnmergedTree = true)
            .assertExists()
            .performScrollTo()
        val priceBounds = rule.onNodeWithText(price, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val badgeBounds = badge.fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Selected badge overlaps the price at fontScale=$fontScale: " +
                "price=$priceBounds badge=$badgeBounds",
            priceBounds.bottom <= badgeBounds.top + 0.5f,
        )
        assertEquals(
            1,
            rule.onAllNodes(
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
            ).fetchSemanticsNodes().size,
        )
        rule.runOnIdle { assertEquals(0, checkoutCalls) }
    }

    private fun sampleProduct(priceText: String): BillingProduct {
        // Billing 9.1.0 accepts this minimal JSON representation for synthetic UI-only data.
        // Keep the test decoupled from the active Play catalog, account and paid checkout.
        val constructor = ProductDetails::class.java.getDeclaredConstructor(String::class.java)
        constructor.isAccessible = true
        val details = constructor.newInstance(
            """{"productId":"test-plan-geometry","type":"subs","title":"Test subscription"}"""
        )
        return BillingProduct(
            id = "test-monthly",
            productId = "test-plan-geometry",
            basePlanId = "aylik",
            offerToken = "synthetic-only",
            priceAmountMicros = 159_990_000L,
            priceText = priceText,
            billingPeriod = "P1M",
            productDetails = details,
        )
    }
}
