package com.parsfilo.contentapp.feature.billing.model

import com.android.billingclient.api.ProductDetails

data class BillingProduct(
    val id: String,
    val productId: String,
    val basePlanId: String?,
    val offerToken: String,
    val priceAmountMicros: Long,
    val priceText: String,
    val billingPeriod: String?,
    val productDetails: ProductDetails
)

fun BillingProduct.toUiLabel(): String {
    return when (billingPeriod) {
        "P1D" -> "Günlük"
        "P1W" -> "Haftalık"
        "P1M" -> "Aylık"
        "P2M" -> "2 Aylık"
        "P3M" -> "3 Aylık"
        "P6M" -> "6 Aylık"
        "P1Y" -> "Yıllık"
        else -> {
            when (basePlanId?.lowercase()) {
                "gunluk", "daily" -> "Günlük"
                "haftalik", "weekly" -> "Haftalık"
                "aylik", "monthly" -> "Aylık"
                "yillik", "yearly", "annual" -> "Yıllık"
                else -> "Plan"
            }
        }
    }
}
