package com.parsfilo.contentapp.feature.ads

/** Bounded, non-identifying categories for matched native inventory not displayed. */
internal enum class NativeAdDiscardReason(
    val analyticsValue: String,
) {
    EXPIRED("ttl_expired"),
    POOL_FULL("pool_full"),
    GATE_CLOSED("gate_closed"),
    RESET("reset"),
}
