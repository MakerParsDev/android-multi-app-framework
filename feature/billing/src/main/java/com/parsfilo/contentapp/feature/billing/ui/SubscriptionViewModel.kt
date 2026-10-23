package com.parsfilo.contentapp.feature.billing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.auth.AuthManager
import com.parsfilo.contentapp.core.model.SubscriptionState
import com.parsfilo.contentapp.feature.billing.BillingManager
import com.parsfilo.contentapp.feature.billing.model.BillingProduct
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    val billingManager: BillingManager,
    private val authManager: AuthManager,
) : ViewModel() {

    init {
        // Recover previously bought but unverified subscriptions after Google sign-in.
        viewModelScope.launch {
            authManager.authState.filter { it }.collect {
                billingManager.restorePurchases()
            }
        }
    }

    val uiState: StateFlow<SubscriptionUiState> = combine(
        billingManager.subscriptionState,
        billingManager.productDetails,
        authManager.authState,
    ) { subState, products, isSignedIn ->
        SubscriptionUiState(
            subscriptionState = subState,
            productDetails = products,
            isSignedIn = isSignedIn,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SubscriptionUiState(isSignedIn = authManager.isUserSignedIn())
    )
}

data class SubscriptionUiState(
    val subscriptionState: SubscriptionState = SubscriptionState.Loading,
    val productDetails: List<BillingProduct> = emptyList(),
    val isSignedIn: Boolean = false,
)
