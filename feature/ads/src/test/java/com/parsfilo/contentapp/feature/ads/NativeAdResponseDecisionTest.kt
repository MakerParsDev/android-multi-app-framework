package com.parsfilo.contentapp.feature.ads

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NativeAdResponseDecisionTest {
    @Test
    fun acceptsCurrentResponseOnlyWhenGateOpenAndSpaceRemains() {
        assertThat(nativeAdDiscardReason(3L, 3L, gateOpen = true, poolSize = 0, poolMax = 2))
            .isNull()
        assertThat(nativeAdDiscardReason(3L, 3L, gateOpen = true, poolSize = 1, poolMax = 2))
            .isNull()
    }

    @Test
    fun rejectsLateResponseAfterPoolResetEvenIfGateReopened() {
        assertThat(nativeAdDiscardReason(3L, 4L, gateOpen = true, poolSize = 0, poolMax = 2))
            .isEqualTo(NativeAdDiscardReason.RESET)
    }

    @Test
    fun closedGateTakesPrecedenceOverStaleResponseAndPoolCapacity() {
        assertThat(nativeAdDiscardReason(3L, 4L, gateOpen = false, poolSize = 2, poolMax = 2))
            .isEqualTo(NativeAdDiscardReason.GATE_CLOSED)
        assertThat(nativeAdDiscardReason(3L, 3L, gateOpen = false, poolSize = 0, poolMax = 2))
            .isEqualTo(NativeAdDiscardReason.GATE_CLOSED)
    }

    @Test
    fun refusesOverfullAndExactlyFullPools() {
        assertThat(nativeAdDiscardReason(9L, 9L, gateOpen = true, poolSize = 2, poolMax = 2))
            .isEqualTo(NativeAdDiscardReason.POOL_FULL)
        assertThat(nativeAdDiscardReason(9L, 9L, gateOpen = true, poolSize = 4, poolMax = 2))
            .isEqualTo(NativeAdDiscardReason.POOL_FULL)
    }
}
