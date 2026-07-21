package com.parsfilo.contentapp.feature.billing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.model.SubscriptionState
import com.parsfilo.contentapp.feature.billing.BillingManager
import com.parsfilo.contentapp.feature.billing.model.BillingProduct
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    val billingManager: BillingManager
) : ViewModel() {

    val uiState: StateFlow<SubscriptionUiState> = combine(
        billingManager.subscriptionState,
        billingManager.productDetails
    ) { subState, products ->
        SubscriptionUiState(
            subscriptionState = subState,
            productDetails = products
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SubscriptionUiState()
    )
}

data class SubscriptionUiState(
    val subscriptionState: SubscriptionState = SubscriptionState.Loading,
    val productDetails: List<BillingProduct> = emptyList()
)
