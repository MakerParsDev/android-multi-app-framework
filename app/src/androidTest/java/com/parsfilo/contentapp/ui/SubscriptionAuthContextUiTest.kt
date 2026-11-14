package com.parsfilo.contentapp.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.feature.auth.R
import com.parsfilo.contentapp.feature.auth.ui.AuthPurpose
import com.parsfilo.contentapp.feature.auth.ui.AuthScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionAuthContextUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun subscriptionSignInExplainsBillingRatherThanMessages() {
        var backRequests = 0
        rule.setContent {
            AppTheme(flavorName = "amenerrasulu") {
                AuthScreen(
                    isLoading = false,
                    errorMessage = null,
                    onSignInClick = {},
                    onErrorDismissed = {},
                    purpose = AuthPurpose.SUBSCRIPTION,
                    onBackClick = { backRequests++ },
                )
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithText(context.getString(R.string.auth_subscription_title)).assertExists()
        rule.onNodeWithText(context.getString(R.string.auth_subscription_description)).assertExists()
        rule.onNodeWithText(context.getString(R.string.auth_sign_in_google)).assertExists()
        rule.onNodeWithText(context.getString(R.string.auth_title)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.auth_subscription_back_to_plans))
            .assertExists()
            .performClick()
        rule.runOnIdle { assertEquals(1, backRequests) }
    }
}
