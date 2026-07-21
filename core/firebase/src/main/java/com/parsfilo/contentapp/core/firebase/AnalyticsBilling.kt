package com.parsfilo.contentapp.core.firebase

fun AppAnalytics.logSubscriptionPurchased(productId: String, price: String, currency: String) {
    logEvent(
        AnalyticsEventName.PURCHASE,
        android.os.Bundle().apply {
            putString(AnalyticsParamKey.ITEM_ID, productId)
            putDouble(AnalyticsParamKey.PRICE, price.toDoubleOrNull() ?: 0.0)
            putString(AnalyticsParamKey.CURRENCY, currency)
        }
    )
}

fun AppAnalytics.logSubscriptionCancelled(productId: String) {
    logEvent(
        AnalyticsEventName.SUBSCRIPTION_CANCELLED,
        android.os.Bundle().apply {
            putString(AnalyticsParamKey.PRODUCT_ID, productId)
        }
    )
}

fun AppAnalytics.logBillingError(errorCode: String, errorMessage: String) {
    logEvent(
        AnalyticsEventName.BILLING_ERROR,
        android.os.Bundle().apply {
            putString(AnalyticsParamKey.ERROR, errorCode)
            putString(AnalyticsParamKey.ERROR_MESSAGE, errorMessage)
        }
    )
}
