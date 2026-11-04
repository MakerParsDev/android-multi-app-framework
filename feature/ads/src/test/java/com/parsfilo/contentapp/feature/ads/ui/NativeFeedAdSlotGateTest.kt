package com.parsfilo.contentapp.feature.ads.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NativeFeedAdSlotGateTest {
    @Test
    fun allowsNativeRenderingAndPreloadingOnlyWhenBothGatesAreOpen() {
        assertThat(nativeFeedSlotEligible(canRequestAds = true, canShowAds = true)).isTrue()
    }

    @Test
    fun blocksNativeRenderingWhenConsentNotAvailable() {
        assertThat(nativeFeedSlotEligible(canRequestAds = false, canShowAds = true)).isFalse()
    }

    @Test
    fun blocksNativeRenderingForPremiumOrRewardedAdFreeUser() {
        assertThat(nativeFeedSlotEligible(canRequestAds = true, canShowAds = false)).isFalse()
    }

    @Test
    fun blocksNativeRenderingWhenBothGatesAreClosed() {
        assertThat(nativeFeedSlotEligible(canRequestAds = false, canShowAds = false)).isFalse()
    }
}
