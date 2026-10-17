package com.parsfilo.contentapp.feature.ads

/**
 * Pure decision for an asynchronously returned native ad, evaluated under the
 * same lock protecting the pool. Null means safe to cache; otherwise the SDK
 * ad must be destroyed and the reason logged.
 */
internal fun nativeAdDiscardReason(
    requestGeneration: Long,
    activeGeneration: Long,
    gateOpen: Boolean,
    poolSize: Int,
    poolMax: Int,
): NativeAdDiscardReason? =
    when {
        !gateOpen -> NativeAdDiscardReason.GATE_CLOSED
        requestGeneration != activeGeneration -> NativeAdDiscardReason.RESET
        poolSize >= poolMax -> NativeAdDiscardReason.POOL_FULL
        else -> null
    }
