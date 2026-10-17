package com.parsfilo.contentapp.feature.ads

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FullScreenShowCoordinatorTest {
    @Test
    fun serializesDifferentAutomaticFormats() {
        val gate = FullScreenShowCoordinator()
        assertThat(gate.tryBegin(AdFormat.INTERSTITIAL, 1_000L)).isTrue()
        assertThat(gate.tryBegin(AdFormat.APP_OPEN, 1_001L)).isFalse()
        gate.onImpression(AdFormat.INTERSTITIAL, 2_000L)
        gate.finish(AdFormat.INTERSTITIAL)
        assertThat(gate.tryBegin(AdFormat.APP_OPEN, 20_000L)).isFalse()
        assertThat(gate.tryBegin(AdFormat.APP_OPEN, 32_000L)).isTrue()
        gate.finish(AdFormat.APP_OPEN)
    }

    @Test
    fun unsuccessfulShowDoesNotCauseUnnecessaryCooldown() {
        val gate = FullScreenShowCoordinator()
        assertThat(gate.tryBegin(AdFormat.APP_OPEN, 1_000L)).isTrue()
        gate.finish(AdFormat.APP_OPEN)
        assertThat(gate.tryBegin(AdFormat.INTERSTITIAL, 1_001L)).isTrue()
    }

    @Test
    fun activeShowNeverExpiresWithoutSdkCallback() {
        val gate = FullScreenShowCoordinator()
        assertThat(gate.tryBegin(AdFormat.INTERSTITIAL, 1_000L)).isTrue()
        assertThat(gate.tryBegin(AdFormat.APP_OPEN, 601_001L)).isFalse()
        gate.finish(AdFormat.INTERSTITIAL)
        assertThat(gate.tryBegin(AdFormat.APP_OPEN, 601_002L)).isTrue()
    }

    @Test
    fun onlyOwnerCanReleaseTheActiveShow() {
        val gate = FullScreenShowCoordinator()
        assertThat(gate.tryBegin(AdFormat.APP_OPEN, 1_000L)).isTrue()
        gate.finish(AdFormat.INTERSTITIAL)
        assertThat(gate.tryBegin(AdFormat.INTERSTITIAL, 1_001L)).isFalse()
    }
}
