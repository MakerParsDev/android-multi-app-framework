package com.parsfilo.contentapp.core.model

sealed interface SubscriptionState {
    data object Unknown : SubscriptionState

    data object Loading : SubscriptionState

    data class Active(val expiryDate: Long?, val isAutoRenewing: Boolean) : SubscriptionState

    data object Inactive : SubscriptionState

    data class Error(val message: String) : SubscriptionState
}
