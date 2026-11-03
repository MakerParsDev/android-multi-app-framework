package com.parsfilo.contentapp.feature.ads

import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RewardedAdCallbackLifecycleTest {
    private val context = mockk<Context>(relaxed = true)
    private val logger = mockk<AdRevenueLogger>(relaxed = true)
    private val policyProvider = mockk<AdsPolicyProvider>()
    private val policy = mockk<AdsPolicyConfig>(relaxed = true)

    @After
    fun tearDown() {
        AdsConsentRuntimeState.update(false)
        unmockkStatic(RewardedAd::class)
    }

    private fun manager(): RewardedAdManager {
        every { policyProvider.getPolicy() } returns policy
        every { policy.isRewardedPlacementEnabled(any()) } returns true
        every { policy.rewardedPoolMax } returns 2
        AdsConsentRuntimeState.update(true)
        return RewardedAdManager(context, logger, policyProvider)
    }

    private fun loadedAd(): RewardedAd = mockk(relaxed = true) {
        every { adUnitId } returns "test-rewarded"
        every { responseInfo } returns mockk(relaxed = true)
    }

    private fun setUnit(manager: RewardedAdManager) {
        val field = manager.javaClass.getDeclaredField("currentAdUnitId")
        field.isAccessible = true
        field.set(manager, "test-rewarded")
    }

    private fun nextSlot(manager: RewardedAdManager): RewardedAd? {
        val field = manager.javaClass.getDeclaredField("rewardedAdNext")
        field.isAccessible = true
        return field.get(manager) as RewardedAd?
    }

    @Test
    fun clearedPrimaryLoadCannotRestoreRewardedInventory() {
        val manager = manager()
        every { policy.rewardedPoolMax } returns 1
        val callback = slot<RewardedAdLoadCallback>()
        mockkStatic(RewardedAd::class)
        every { RewardedAd.load(any(), any(), any<AdRequest>(), capture(callback)) } just Runs

        manager.loadAd("test-rewarded")
        manager.clearAd()
        callback.captured.onAdLoaded(loadedAd())

        assertThat(manager.isAdReadyNow()).isFalse()
        verify(exactly = 0) { logger.logLoaded(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun consentRevocationBeforePrimaryCallbackRejectsRewardedAd() {
        val manager = manager()
        every { policy.rewardedPoolMax } returns 1
        val callback = slot<RewardedAdLoadCallback>()
        mockkStatic(RewardedAd::class)
        every { RewardedAd.load(any(), any(), any<AdRequest>(), capture(callback)) } just Runs

        manager.loadAd("test-rewarded")
        AdsConsentRuntimeState.update(false)
        callback.captured.onAdLoaded(loadedAd())

        assertThat(manager.isAdReadyNow()).isFalse()
    }

    @Test
    fun clearedSecondaryLoadCannotRestoreRewardedInventory() {
        val manager = manager()
        setUnit(manager)
        val callback = slot<RewardedAdLoadCallback>()
        mockkStatic(RewardedAd::class)
        every { RewardedAd.load(any(), any(), any<AdRequest>(), capture(callback)) } just Runs

        manager.loadNextIfNeeded()
        manager.clearAd()
        callback.captured.onAdLoaded(loadedAd())

        assertThat(nextSlot(manager)).isNull()
        verify(exactly = 0) { logger.logLoaded(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun consentRevocationBeforeSecondaryCallbackRejectsRewardedAd() {
        val manager = manager()
        setUnit(manager)
        val callback = slot<RewardedAdLoadCallback>()
        mockkStatic(RewardedAd::class)
        every { RewardedAd.load(any(), any(), any<AdRequest>(), capture(callback)) } just Runs

        manager.loadNextIfNeeded()
        AdsConsentRuntimeState.update(false)
        callback.captured.onAdLoaded(loadedAd())

        assertThat(nextSlot(manager)).isNull()
    }

    @Test
    fun cancelledPrimaryFailureCannotPoisonNewRewardedRequest() {
        val manager = manager()
        every { policy.rewardedPoolMax } returns 1
        val callbacks = mutableListOf<RewardedAdLoadCallback>()
        mockkStatic(RewardedAd::class)
        every { RewardedAd.load(any(), any(), any<AdRequest>(), any()) } answers {
            callbacks.add(arg<RewardedAdLoadCallback>(3))
        }

        manager.loadAd("test-rewarded")
        manager.clearAd()
        manager.loadAd("test-rewarded")
        assertThat(callbacks).hasSize(2)
        val error = mockk<LoadAdError>(relaxed = true) {
            every { code } returns 3
            every { domain } returns MobileAds.ERROR_DOMAIN
        }
        callbacks[0].onAdFailedToLoad(error)

        verify(exactly = 0) { logger.logFailedToLoad(any(), any(), any(), any(), any(), any(), any(), any()) }
        callbacks[1].onAdLoaded(loadedAd())
        assertThat(manager.isAdReadyNow()).isTrue()
    }

    @Test
    fun supersededSecondaryLoadCannotReserveFutureRewardedSlot() {
        val manager = manager()
        setUnit(manager)
        val callbacks = mutableListOf<RewardedAdLoadCallback>()
        mockkStatic(RewardedAd::class)
        every { RewardedAd.load(any(), any(), any<AdRequest>(), any()) } answers {
            callbacks.add(arg<RewardedAdLoadCallback>(3))
        }

        manager.loadNextIfNeeded()
        manager.loadAd("test-rewarded")
        assertThat(callbacks).hasSize(2)
        callbacks[0].onAdLoaded(loadedAd())
        callbacks[1].onAdLoaded(loadedAd())

        assertThat(nextSlot(manager)).isNull()
        assertThat(callbacks).hasSize(3)
        assertThat(manager.isAdReadyNow()).isTrue()
    }
}
