package com.parsfilo.contentapp.feature.content.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NativeAdInsertionPolicyTest {

    @Test
    fun `top banner only shows for short scrollable content`() {
        assertThat(shouldShowTopBannerForScrollableContent(0)).isFalse()
        assertThat(shouldShowTopBannerForScrollableContent(1)).isTrue()
        assertThat(shouldShowTopBannerForScrollableContent(11)).isTrue()
        assertThat(shouldShowTopBannerForScrollableContent(12)).isFalse()
    }

    @Test
    fun `inline ads stay disabled below minimum content threshold`() {
        assertThat(shouldPreferInlineFeedAds(0)).isFalse()
        assertThat(shouldPreferInlineFeedAds(11)).isFalse()
        assertThat(shouldInsertInlineFeedAdAfterItem(9, 11)).isFalse()
        assertThat(shouldInsertNativeAdAfterVerse(9, 11)).isFalse()
    }

    @Test
    fun `twelve items insert a single inline ad after tenth item`() {
        assertThat(shouldPreferInlineFeedAds(12)).isTrue()
        assertThat(shouldInsertInlineFeedAdAfterItem(8, 12)).isFalse()
        assertThat(shouldInsertInlineFeedAdAfterItem(9, 12)).isTrue()
        assertThat(shouldInsertInlineFeedAdAfterItem(10, 12)).isFalse()
    }

    @Test
    fun `twenty items insert one inline ad because another would be too close to the end`() {
        assertThat(shouldInsertInlineFeedAdAfterItem(9, 20)).isTrue()
        assertThat(shouldInsertInlineFeedAdAfterItem(19, 20)).isFalse()
    }

    @Test
    fun `twenty two items insert ads after tenth and twentieth items`() {
        assertThat(shouldInsertInlineFeedAdAfterItem(9, 22)).isTrue()
        assertThat(shouldInsertInlineFeedAdAfterItem(19, 22)).isTrue()
        assertThat(shouldInsertInlineFeedAdAfterItem(20, 22)).isFalse()
    }

    @Test
    fun `verse helper uses the same conservative feed spacing`() {
        assertThat(shouldInsertNativeAdAfterVerse(9, 12)).isTrue()
        assertThat(shouldInsertNativeAdAfterVerse(19, 22)).isTrue()
        assertThat(shouldInsertNativeAdAfterVerse(9, 11)).isFalse()
    }

    @Test
    fun `invalid indexes and totals return false`() {
        assertThat(shouldInsertNativeAdAfterVerse(-1, 3)).isFalse()
        assertThat(shouldInsertNativeAdAfterVerse(12, 12)).isFalse()
        assertThat(shouldInsertInlineFeedAdAfterItem(-1, 12)).isFalse()
        assertThat(shouldInsertInlineFeedAdAfterItem(12, 12)).isFalse()
        assertThat(shouldInsertInlineFeedAdAfterItem(0, 0)).isFalse()
    }
}
