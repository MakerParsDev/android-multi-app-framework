package com.parsfilo.contentapp.feature.ads

import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Drives the production NativeAdManager's actual SDK callbacks without
 * network requests or a Google SDK test-device registration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NativeAdLoaderCallbacksTest {
    private val logger = mockk<AdRevenueLogger>(relaxed = true)
    private val gate = mockk<AdGateChecker> {
        every { shouldShowAds } returns emptyFlow()
    }
    private val policy = mockk<AdsPolicyConfig> {
        every { nativePoolMax } returns 2
        every { nativeTtlMs } returns 30 * 60_000L
        every { nativeExactPlacementOnly } returns false
        every { demandAwarePreloadEnabled } returns true
        every { isNativePlacementEnabled(any()) } returns true
    }
    private val provider = mockk<AdsPolicyProvider> {
        every { getPolicy() } returns policy
    }
    private val budget = mockk<AdPreloadRequestBudget> {
        every { tryReserve(any(), any()) } returns true
    }
    private val factory = mockk<NativeAdLoaderFactory>()
    private val builder = mockk<AdLoader.Builder>()
    private val sdkLoader = mockk<AdLoader>()
    private val nativeListener = slot<NativeAd.OnNativeAdLoadedListener>()
    private val adListener = slot<AdListener>()
    private lateinit var manager: NativeAdManager

    @Before
    fun setUp() {
        AdsConsentRuntimeState.update(true)
        every { factory.create(any(), any()) } returns builder
        every { builder.forNativeAd(capture(nativeListener)) } returns builder
        every { builder.withAdListener(capture(adListener)) } returns builder
        every { builder.withNativeAdOptions(any<NativeAdOptions>()) } returns builder
        every { builder.build() } returns sdkLoader
        every { sdkLoader.loadAd(any<AdRequest>()) } just Runs
        manager =
            NativeAdManager(
                RuntimeEnvironment.getApplication(),
                logger,
                gate,
                provider,
                budget,
                factory,
            )
    }

    @After
    fun tearDown() {
        AdsConsentRuntimeState.update(false)
    }

    private fun beginLoad() {
        manager.loadAds(
            RuntimeEnvironment.getApplication(),
            "test-native",
            AdPlacement.NATIVE_FEED_HOME,
            count = 1,
        )
        verify(atLeast = 1) { sdkLoader.loadAd(any<AdRequest>()) }
    }

    @Test
    fun acceptedSdkResponseIsDeliveredAndNotDiscarded() {
        beginLoad()
        val ad = mockk<NativeAd>(relaxed = true)
        every { ad.responseInfo } returns null

        nativeListener.captured.onNativeAdLoaded(ad)
        adListener.captured.onAdLoaded()

        assertThat(manager.getNativeAd(AdPlacement.NATIVE_FEED_HOME)).isSameInstanceAs(ad)
        verify(exactly = 0) { ad.destroy() }
        verify(exactly = 0) { logger.logNativeInventoryDiscarded(any(), any(), any()) }
    }

    @Test
    fun lateResponseAfterResetIsDestroyedAndCannotRepopulatePool() {
        beginLoad()
        manager.destroyAds()
        val ad = mockk<NativeAd>(relaxed = true)
        every { ad.responseInfo } returns null

        nativeListener.captured.onNativeAdLoaded(ad)
        adListener.captured.onAdLoaded()

        verify(exactly = 1) { ad.destroy() }
        verify(exactly = 1) {
            logger.logNativeInventoryDiscarded(
                AdPlacement.NATIVE_FEED_HOME,
                "test-native",
                NativeAdDiscardReason.RESET,
            )
        }
        assertThat(manager.getNativeAd(AdPlacement.NATIVE_FEED_HOME)).isNull()
    }

    @Test
    fun sdkResponseAfterConsentRevocationIsDiscardedAsGateClosed() {
        beginLoad()
        AdsConsentRuntimeState.update(false)
        val ad = mockk<NativeAd>(relaxed = true)
        every { ad.responseInfo } returns null

        nativeListener.captured.onNativeAdLoaded(ad)
        adListener.captured.onAdLoaded()

        verify(exactly = 1) { ad.destroy() }
        verify(exactly = 1) {
            logger.logNativeInventoryDiscarded(
                AdPlacement.NATIVE_FEED_HOME,
                "test-native",
                NativeAdDiscardReason.GATE_CLOSED,
            )
        }
    }

    @Test
    fun staleSuccessAndFailureCallbacksCannotCompleteTheNextBatch() {
        beginLoad()
        val oldListener = adListener.captured
        manager.destroyAds()
        beginLoad()

        oldListener.onAdLoaded()
        val failure = mockk<LoadAdError>(relaxed = true) {
            every { code } returns 2
            every { domain } returns "com.google.android.gms.ads"
            every { message } returns "timeout"
        }
        oldListener.onAdFailedToLoad(failure)
        // Still in-flight: another load must not create a third SDK request.
        manager.loadAds(
            RuntimeEnvironment.getApplication(),
            "test-native",
            AdPlacement.NATIVE_FEED_HOME,
            count = 1,
        )
        verify(exactly = 2) { sdkLoader.loadAd(any<AdRequest>()) }
        verify(exactly = 0) {
            logger.logFailedToLoad(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun staleFailureCallbackNeverPoisonsTheNextLoadBatch() {
        beginLoad()
        val staleListener = adListener.captured
        manager.destroyAds()

        val failure = mockk<LoadAdError>(relaxed = true) {
            every { code } returns 2
            every { domain } returns "com.google.android.gms.ads"
            every { message } returns "timeout"
        }
        staleListener.onAdFailedToLoad(failure)
        verify(exactly = 0) { logger.logFailedToLoad(any(), any(), any(), any(), any(), any(), any(), any()) }

        beginLoad()
        verify(exactly = 2) { sdkLoader.loadAd(any<AdRequest>()) }
    }
}
