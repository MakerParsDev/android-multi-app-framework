package com.parsfilo.contentapp.feature.ads

import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
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
class InterstitialAdCallbackLifecycleTest {
    private val context = mockk<Context>(relaxed = true)
    private val logger = mockk<AdRevenueLogger>(relaxed = true)
    private val policyProvider = mockk<AdsPolicyProvider>()
    private val policy = mockk<AdsPolicyConfig>(relaxed = true)

    @After
    fun tearDown() {
        AdsConsentRuntimeState.update(false)
        unmockkStatic(InterstitialAd::class)
    }

    private fun manager(): InterstitialAdManager {
        every { policyProvider.getPolicy() } returns policy
        every { policy.interstitialPoolMax } returns 2
        every { policy.demandAwarePreloadEnabled } returns false
        every { policy.isInterstitialPlacementEnabled(any()) } returns true
        AdsConsentRuntimeState.update(true)
        return InterstitialAdManager(
            context,
            mockk<PreferencesDataSource>(relaxed = true),
            logger,
            policyProvider,
            mockk(relaxed = true),
            mockk(relaxed = true),
        )
    }

    private fun loadedAd(): InterstitialAd = mockk(relaxed = true) {
        every { adUnitId } returns "test-interstitial"
        every { responseInfo } returns mockk(relaxed = true)
    }

    @Test
    fun clearedPrimaryRequestCannotRestoreAdFromLateSuccess() {
        val manager = manager()
        every { policy.interstitialPoolMax } returns 1
        val callback = slot<InterstitialAdLoadCallback>()
        mockkStatic(InterstitialAd::class)
        every { InterstitialAd.load(any(), any(), any<AdRequest>(), capture(callback)) } just Runs

        manager.loadAd(context, "test-interstitial")
        manager.clearAd()
        callback.captured.onAdLoaded(loadedAd())

        assertThat(manager.isAdReady()).isFalse()
        verify(exactly = 0) { logger.logLoaded(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun revokedConsentBeforePrimaryCallbackCannotRestoreAd() {
        val manager = manager()
        every { policy.interstitialPoolMax } returns 1
        val callback = slot<InterstitialAdLoadCallback>()
        mockkStatic(InterstitialAd::class)
        every { InterstitialAd.load(any(), any(), any<AdRequest>(), capture(callback)) } just Runs

        manager.loadAd(context, "test-interstitial")
        AdsConsentRuntimeState.update(false)
        callback.captured.onAdLoaded(loadedAd())

        assertThat(manager.isAdReady()).isFalse()
    }

    @Test
    fun cancelledPrimaryFailureCannotCorruptNewRequestState() {
        val manager = manager()
        every { policy.interstitialPoolMax } returns 1
        val callbacks = mutableListOf<InterstitialAdLoadCallback>()
        mockkStatic(InterstitialAd::class)
        every { InterstitialAd.load(any(), any(), any<AdRequest>(), any()) } answers {
            callbacks.add(arg<InterstitialAdLoadCallback>(3))
        }

        manager.loadAd(context, "test-interstitial")
        manager.clearAd()
        manager.loadAd(context, "test-interstitial")
        assertThat(callbacks).hasSize(2)
        callbacks[0].onAdFailedToLoad(mockk(relaxed = true))
        callbacks[1].onAdLoaded(loadedAd())

        assertThat(manager.isAdReady()).isTrue()
    }

    @Test
    fun revokedConsentBeforeSecondaryCallbackCannotRestoreSlot() {
        val manager = manager()
        val field = manager.javaClass.getDeclaredField("currentAdUnitId")
        field.isAccessible = true
        field.set(manager, "test-interstitial")
        val callback = slot<InterstitialAdLoadCallback>()
        mockkStatic(InterstitialAd::class)
        every { InterstitialAd.load(any(), any(), any<AdRequest>(), capture(callback)) } just Runs

        manager.loadNextIfNeeded()
        AdsConsentRuntimeState.update(false)
        callback.captured.onAdLoaded(loadedAd())

        val nextField = manager.javaClass.getDeclaredField("interstitialAdNext")
        nextField.isAccessible = true
        assertThat(nextField.get(manager)).isNull()
    }

    @Test
    fun supersedingPrimaryLoadReleasesTheOldSecondarySlotReservation() {
        val manager = manager()
        val unitField = manager.javaClass.getDeclaredField("currentAdUnitId")
        unitField.isAccessible = true
        unitField.set(manager, "test-interstitial")
        val callbacks = mutableListOf<InterstitialAdLoadCallback>()
        mockkStatic(InterstitialAd::class)
        every { InterstitialAd.load(any(), any(), any<AdRequest>(), any()) } answers {
            callbacks.add(arg<InterstitialAdLoadCallback>(3))
        }

        manager.loadNextIfNeeded()
        manager.loadAd(context, "test-interstitial")
        assertThat(callbacks).hasSize(2)
        callbacks[0].onAdLoaded(loadedAd()) // Old secondary must not be cached.
        callbacks[1].onAdLoaded(loadedAd()) // New primary should schedule a new secondary.

        val nextField = manager.javaClass.getDeclaredField("interstitialAdNext")
        nextField.isAccessible = true
        assertThat(nextField.get(manager)).isNull()
        assertThat(callbacks).hasSize(3)
        assertThat(manager.isAdReady()).isTrue()
    }

    @Test
    fun clearedSecondaryRequestCannotRestoreNextSlot() {
        val manager = manager()
        val field = manager.javaClass.getDeclaredField("currentAdUnitId")
        field.isAccessible = true
        field.set(manager, "test-interstitial")
        val callback = slot<InterstitialAdLoadCallback>()
        mockkStatic(InterstitialAd::class)
        every { InterstitialAd.load(any(), any(), any<AdRequest>(), capture(callback)) } just Runs

        manager.loadNextIfNeeded()
        manager.clearAd()
        callback.captured.onAdLoaded(loadedAd())

        val nextField = manager.javaClass.getDeclaredField("interstitialAdNext")
        nextField.isAccessible = true
        assertThat(nextField.get(manager)).isNull()
        verify(exactly = 0) { logger.logLoaded(any(), any(), any(), any(), any(), any()) }
    }
}
