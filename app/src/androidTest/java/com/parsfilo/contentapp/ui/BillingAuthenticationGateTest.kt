package com.parsfilo.contentapp.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.model.SubscriptionState
import com.parsfilo.contentapp.feature.billing.ui.SubscriptionScreen
import com.parsfilo.contentapp.feature.billing.ui.SubscriptionUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BillingAuthenticationGateTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun signedOutPaywallShowsGuestInfoAndRestoreWithoutCheckout() {
        var signInRequests = 0
        var purchaseRequests = 0
        rule.setContent {
            AppTheme(flavorName = "amenerrasulu") {
                SubscriptionScreen(
                    uiState = SubscriptionUiState(
                        subscriptionState = SubscriptionState.Inactive,
                        isSignedIn = false,
                    ),
                    onSignInClick = { signInRequests++ },
                    onPlanSelected = { purchaseRequests++ },
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val guestText = context.getString(
            com.parsfilo.contentapp.feature.billing.R.string.billing_guest_catalog_info
        )
        val restoreAction = context.getString(
            com.parsfilo.contentapp.feature.billing.R.string.billing_restore_sign_in_action
        )
        rule.onNodeWithText(guestText).assertExists()
        rule.runOnIdle {
            assertEquals(0, signInRequests)
            assertEquals(0, purchaseRequests)
        }
        // Explicit restore is the only sign-in action, not merely opening the paywall.
        rule.onNodeWithText(restoreAction).performClick()
        rule.runOnIdle {
            assertEquals(1, signInRequests)
            assertEquals(0, purchaseRequests)
        }
    }
}
