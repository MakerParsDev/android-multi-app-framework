package com.parsfilo.contentapp.feature.ads.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NativeFeedAdSlotGateTest {
    @Test
    fun allowsNativeRenderingAndPreloadingOnlyWhenAllGatesAreOpen() {
        assertThat(nativeFeedSlotEligible(canRequestAds = true, canShowAds = true, sdkReady = true)).isTrue()
    }

    @Test
    fun blocksNativeRenderingWhenConsentNotAvailable() {
        assertThat(nativeFeedSlotEligible(canRequestAds = false, canShowAds = true, sdkReady = true)).isFalse()
    }

    @Test
    fun blocksNativeRenderingForPremiumOrRewardedAdFreeUser() {
        assertThat(nativeFeedSlotEligible(canRequestAds = true, canShowAds = false, sdkReady = true)).isFalse()
    }

    @Test
    fun blocksNativeRenderingWhenBothGatesAreClosed() {
        assertThat(nativeFeedSlotEligible(canRequestAds = false, canShowAds = false, sdkReady = true)).isFalse()
    }

    @Test
    fun blocksNativePreloadsWhileSdkInitializationIsPending() {
        assertThat(nativeFeedSlotEligible(canRequestAds = true, canShowAds = true, sdkReady = false)).isFalse()
    }

    @Test
    fun permitsNativePreloadAfterSdkInitializationCompletes() {
        assertThat(nativeFeedSlotEligible(canRequestAds = true, canShowAds = true, sdkReady = false)).isFalse()
        assertThat(nativeFeedSlotEligible(canRequestAds = true, canShowAds = true, sdkReady = true)).isTrue()
    }

    @Test
    fun sdkReadinessDoesNotOverrideMissingConsent() {
        assertThat(nativeFeedSlotEligible(canRequestAds = false, canShowAds = true, sdkReady = false)).isFalse()
        assertThat(nativeFeedSlotEligible(canRequestAds = false, canShowAds = true, sdkReady = true)).isFalse()
    }
}
