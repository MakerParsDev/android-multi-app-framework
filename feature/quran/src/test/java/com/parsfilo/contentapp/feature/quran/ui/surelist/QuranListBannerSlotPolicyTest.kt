package com.parsfilo.contentapp.feature.quran.ui.surelist

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuranListBannerSlotPolicyTest {
    @Test
    fun pendingEntitlementAndEligibleUserKeepSameSlotBeforeAndAfterAdFill() {
        assertThat(shouldReserveQuranListBannerSlot(true, false, false)).isTrue()
        assertThat(shouldReserveQuranListBannerSlot(true, true, true)).isTrue()
    }

    @Test
    fun confirmedPremiumAndAdFreeUsersHaveNoBlankAdSlot() {
        assertThat(shouldReserveQuranListBannerSlot(true, true, false)).isFalse()
    }

    @Test
    fun slotIsAbsentWhenScreenDoesNotHaveBannerContent() {
        assertThat(shouldReserveQuranListBannerSlot(false, false, false)).isFalse()
        assertThat(shouldReserveQuranListBannerSlot(false, true, true)).isFalse()
    }
}
