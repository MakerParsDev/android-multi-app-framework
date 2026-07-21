package com.parsfilo.contentapp.feature.ads

import java.util.concurrent.atomic.AtomicReference

internal enum class MobileAdsInitializationState {
    Idle,
    Initializing,
    Ready,
    RetryableFailure,
}

internal enum class MobileAdsInitializationDecision {
    Start,
    SkipInProgress,
    SkipReady,
}

internal class MobileAdsInitializationGate {
    private val state = AtomicReference(MobileAdsInitializationState.Idle)

    fun tryStart(): MobileAdsInitializationDecision {
        while (true) {
            val observed = state.get()
            when (observed) {
                MobileAdsInitializationState.Ready -> return MobileAdsInitializationDecision.SkipReady
                MobileAdsInitializationState.Initializing ->
                    return MobileAdsInitializationDecision.SkipInProgress
                MobileAdsInitializationState.Idle,
                MobileAdsInitializationState.RetryableFailure,
                -> {
                    if (state.compareAndSet(observed, MobileAdsInitializationState.Initializing)) {
                        return MobileAdsInitializationDecision.Start
                    }
                }
            }
        }
    }

    fun markReady() {
        state.set(MobileAdsInitializationState.Ready)
    }

    fun markRetryableFailure() {
        state.compareAndSet(
            MobileAdsInitializationState.Initializing,
            MobileAdsInitializationState.RetryableFailure,
        )
    }

    fun currentState(): MobileAdsInitializationState = state.get()
}
