package com.parsfilo.contentapp.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.SubscriptionState
import com.parsfilo.contentapp.feature.billing.BillingErrorKeys
import com.parsfilo.contentapp.feature.billing.R
import com.parsfilo.contentapp.feature.billing.ui.SubscriptionScreen
import com.parsfilo.contentapp.feature.billing.ui.SubscriptionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests only synthetic, signed-in paywall states. No BillingClient, product, purchase or
 * entitlement operation is performed and no Play account/installed app is modified.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionLoadingRecoveryUiTest {
    @get:Rule val rule = createComposeRule()

    private fun text(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun stalledCatalogLeavesLoadingAndOffersRetryAndFreePath() {
        var retries = 0
        var purchases = 0
        var backActions = 0

        rule.setContent {
            AppTheme(flavorName = "mucizedualar") {
                SubscriptionScreen(
                    uiState = SubscriptionUiState(
                        subscriptionState = SubscriptionState.Loading,
                        isSignedIn = true,
                    ),
                    onRetry = { retries++ },
                    onBackClick = { backActions++ },
                    onPlanSelected = { purchases++ },
                )
            }
        }

        val retry = text(R.string.billing_retry)
        val timeout = text(R.string.billing_error_timeout)
        val free = text(R.string.billing_continue_free)

        // Loading must stop within the documented 15-second bound, never spin indefinitely.
        rule.waitUntil(21_000) {
            rule.onAllNodesWithText(timeout).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(retry).assertExists()
        rule.onNodeWithText(free).assertExists()
        rule.onNodeWithText(retry).performClick()
        rule.runOnIdle {
            assertEquals(1, retries)
            assertEquals(0, purchases)
        }
        assertTrue(rule.onAllNodesWithText(timeout).fetchSemanticsNodes().isEmpty())

        rule.onNodeWithContentDescription(text(R.string.billing_back))
            .performClick()
        rule.runOnIdle {
            assertEquals(1, backActions)
            assertEquals(0, purchases)
        }
    }

    @Test
    fun unavailableBillingImmediatelyShowsGuidanceAndRetryWithoutPurchase() {
        var retries = 0
        var purchases = 0

        rule.setContent {
            AppTheme(flavorName = "mucizedualar") {
                SubscriptionScreen(
                    uiState = SubscriptionUiState(
                        subscriptionState = SubscriptionState.Loading,
                        isSignedIn = true,
                        availabilityError = BillingErrorKeys.UNAVAILABLE,
                    ),
                    onRetry = { retries++ },
                    onPlanSelected = { purchases++ },
                )
            }
        }

        rule.onNodeWithText(text(R.string.billing_error_unavailable)).assertExists()
        rule.onNodeWithText(text(R.string.billing_continue_free)).assertExists()
        rule.onNodeWithText(text(R.string.billing_retry)).performClick()
        rule.runOnIdle {
            assertEquals(1, retries)
            assertEquals(0, purchases)
        }
    }
}
