package com.parsfilo.contentapp.feature.ads

import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
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
class RewardedInterstitialAdCallbackLifecycleTest {
    private val context = mockk<Context>(relaxed = true)
    private val logger = mockk<AdRevenueLogger>(relaxed = true)
    private val policyProvider = mockk<AdsPolicyProvider>()
    private val policy = mockk<AdsPolicyConfig>(relaxed = true)

    @After
    fun tearDown() {
        AdsConsentRuntimeState.update(false)
        unmockkStatic(RewardedInterstitialAd::class)
    }

    private fun manager(): RewardedInterstitialAdManager {
        every { policyProvider.getPolicy() } returns policy
        every { policy.isRewardedInterstitialPlacementEnabled(any()) } returns true
        AdsConsentRuntimeState.update(true)
        return RewardedInterstitialAdManager(
            context,
            logger,
            policyProvider,
            mockk<RewardedInterstitialCoordinator>(relaxed = true),
        )
    }

    private fun loadedAd(): RewardedInterstitialAd = mockk(relaxed = true) {
        every { adUnitId } returns "test-rewarded-interstitial"
        every { responseInfo } returns mockk(relaxed = true)
    }

    @Test
    fun clearedLoadCannotRestoreInventoryAfterLateSuccess() {
        val manager = manager()
        val callback = slot<RewardedInterstitialAdLoadCallback>()
        mockkStatic(RewardedInterstitialAd::class)
        every { RewardedInterstitialAd.load(any(), any(), any<AdRequest>(), capture(callback)) } just Runs

        manager.loadAd("test-rewarded-interstitial")
        manager.clearAd()
        callback.captured.onAdLoaded(loadedAd())

        assertThat(manager.isAdReadyNow()).isFalse()
        verify(exactly = 0) { logger.logLoaded(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun consentRevocationBeforeCallbackCannotRestoreInventory() {
        val manager = manager()
        val callback = slot<RewardedInterstitialAdLoadCallback>()
        mockkStatic(RewardedInterstitialAd::class)
        every { RewardedInterstitialAd.load(any(), any(), any<AdRequest>(), capture(callback)) } just Runs

        manager.loadAd("test-rewarded-interstitial")
        AdsConsentRuntimeState.update(false)
        callback.captured.onAdLoaded(loadedAd())

        assertThat(manager.isAdReadyNow()).isFalse()
    }

    @Test
    fun disablingPlacementBeforeCallbackCannotRestoreInventory() {
        val manager = manager()
        val callback = slot<RewardedInterstitialAdLoadCallback>()
        mockkStatic(RewardedInterstitialAd::class)
        every { RewardedInterstitialAd.load(any(), any(), any<AdRequest>(), capture(callback)) } just Runs

        manager.loadAd("test-rewarded-interstitial")
        every { policy.isRewardedInterstitialPlacementEnabled(any()) } returns false
        callback.captured.onAdLoaded(loadedAd())

        assertThat(manager.isAdReadyNow()).isFalse()
    }

    @Test
    fun cancelledFailureCannotCorruptNewRequest() {
        val manager = manager()
        val callbacks = mutableListOf<RewardedInterstitialAdLoadCallback>()
        mockkStatic(RewardedInterstitialAd::class)
        every { RewardedInterstitialAd.load(any(), any(), any<AdRequest>(), any()) } answers {
            callbacks.add(arg<RewardedInterstitialAdLoadCallback>(3))
        }

        manager.loadAd("test-rewarded-interstitial")
        manager.clearAd()
        manager.loadAd("test-rewarded-interstitial")
        assertThat(callbacks).hasSize(2)
        callbacks[0].onAdFailedToLoad(mockk<LoadAdError>(relaxed = true))
        callbacks[1].onAdLoaded(loadedAd())

        assertThat(manager.isAdReadyNow()).isTrue()
        verify(exactly = 0) {
            logger.logFailedToLoad(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun supersededSuccessCannotReplaceNewRequestInventory() {
        val manager = manager()
        val callbacks = mutableListOf<RewardedInterstitialAdLoadCallback>()
        mockkStatic(RewardedInterstitialAd::class)
        every { RewardedInterstitialAd.load(any(), any(), any<AdRequest>(), any()) } answers {
            callbacks.add(arg<RewardedInterstitialAdLoadCallback>(3))
        }

        manager.loadAd("test-rewarded-interstitial")
        manager.clearAd()
        manager.loadAd("test-rewarded-interstitial")
        assertThat(callbacks).hasSize(2)
        callbacks[1].onAdLoaded(loadedAd())
        callbacks[0].onAdLoaded(loadedAd())

        assertThat(manager.isAdReadyNow()).isTrue()
        verify(exactly = 1) { logger.logLoaded(any(), any(), any(), any(), any(), any()) }
    }
}
