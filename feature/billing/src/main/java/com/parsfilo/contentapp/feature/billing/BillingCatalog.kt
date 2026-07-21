package com.parsfilo.contentapp.feature.billing

import com.parsfilo.contentapp.feature.billing.model.BillingProduct

internal object BillingCatalog {
    val subscriptionProductIds: List<String> = listOf(
        "reklamsiz_kullanim",
        "monthly_no_ads",
        "monthly",
        "weekly",
        "yearly"
    )

    private val allowedBasePlanIds: Set<String> = setOf(
        "gunluk", "haftalik", "aylik",
        "daily", "weekly", "monthly", "yearly", "annual"
    )

    private val periodSortOrder: Map<String, Int> = mapOf(
        "P1D" to 1,
        "P1W" to 2,
        "P1M" to 3,
        "P2M" to 4,
        "P3M" to 5,
        "P6M" to 6,
        "P1Y" to 7
    )

    fun resolvePreferredProductId(products: List<BillingProduct>): String? {
        return subscriptionProductIds.firstOrNull { preferred ->
            products.any { it.productId == preferred }
        }
    }

    fun isAllowedBasePlan(basePlanId: String?): Boolean {
        val normalized = basePlanId?.lowercase() ?: return true
        return normalized in allowedBasePlanIds
    }

    fun deduplicationKey(product: BillingProduct): String {
        return product.basePlanId?.lowercase() ?: product.billingPeriod ?: product.id
    }

    fun periodOrder(billingPeriod: String?): Int {
        return periodSortOrder[billingPeriod] ?: 99
    }
}
