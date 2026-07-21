package com.parsfilo.contentapp.feature.ads

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MobileAdsInitializationGateTest {

    @Test
    fun `first start begins initialization and duplicate call is skipped`() {
        val gate = MobileAdsInitializationGate()

        assertThat(gate.tryStart()).isEqualTo(MobileAdsInitializationDecision.Start)
        assertThat(gate.currentState()).isEqualTo(MobileAdsInitializationState.Initializing)
        assertThat(gate.tryStart()).isEqualTo(MobileAdsInitializationDecision.SkipInProgress)
    }

    @Test
    fun `ready state skips future initialization attempts`() {
        val gate = MobileAdsInitializationGate()

        assertThat(gate.tryStart()).isEqualTo(MobileAdsInitializationDecision.Start)
        gate.markReady()

        assertThat(gate.currentState()).isEqualTo(MobileAdsInitializationState.Ready)
        assertThat(gate.tryStart()).isEqualTo(MobileAdsInitializationDecision.SkipReady)
    }

    @Test
    fun `soft timeout marks retryable failure and allows retry`() {
        val gate = MobileAdsInitializationGate()

        assertThat(gate.tryStart()).isEqualTo(MobileAdsInitializationDecision.Start)
        gate.markRetryableFailure()

        assertThat(gate.currentState()).isEqualTo(MobileAdsInitializationState.RetryableFailure)
        assertThat(gate.tryStart()).isEqualTo(MobileAdsInitializationDecision.Start)
    }

    @Test
    fun `late timeout cannot downgrade ready initialization`() {
        val gate = MobileAdsInitializationGate()

        assertThat(gate.tryStart()).isEqualTo(MobileAdsInitializationDecision.Start)
        gate.markReady()
        gate.markRetryableFailure()

        assertThat(gate.currentState()).isEqualTo(MobileAdsInitializationState.Ready)
    }
}
