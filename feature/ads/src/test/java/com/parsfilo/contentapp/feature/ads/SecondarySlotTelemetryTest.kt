package com.parsfilo.contentapp.feature.ads

import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
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
class SecondarySlotTelemetryTest {
    private val logger = mockk<AdRevenueLogger>(relaxed = true)
    private val policy = mockk<AdsPolicyProvider>()
    private val config = mockk<AdsPolicyConfig>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @After
    fun tearDown() {
        AdsConsentRuntimeState.update(false)
        unmockkStatic(InterstitialAd::class, RewardedAd::class)
    }

    @Test
    fun interstitialSecondSlotLogsMatchingRequestAndCategorizedFailure() {
        every { config.interstitialPoolMax } returns 2
        every { config.demandAwarePreloadEnabled } returns false
        every { policy.getPolicy() } returns config
        AdsConsentRuntimeState.update(true)
        val manager = InterstitialAdManager(
            appContext = context,
            preferencesDataSource = mockk<PreferencesDataSource>(relaxed = true),
            adRevenueLogger = logger,
            adsPolicyProvider = policy,
            fullScreenShowCoordinator = mockk(relaxed = true),
            adPreloadRequestBudget = mockk(relaxed = true),
        )
        setAdUnitId(manager, "interstitial-slot")
        val callback = slot<InterstitialAdLoadCallback>()
        mockkStatic(InterstitialAd::class)
        every { InterstitialAd.load(any(), any(), any<AdRequest>(), capture(callback)) } just Runs

        manager.loadNextIfNeeded()

        verify(exactly = 1) {
            logger.logPreloadRequested(
                AdFormat.INTERSTITIAL, AdPlacement.INTERSTITIAL_DEFAULT, "interstitial-slot",
                null, "pool_next_slot",
            )
        }
        verify(exactly = 1) {
            logger.logRequest(AdFormat.INTERSTITIAL, AdPlacement.INTERSTITIAL_DEFAULT, "interstitial-slot", null)
        }
        callback.captured.onAdFailedToLoad(mockFailure(3))
        verify(exactly = 1) {
            logger.logFailedToLoad(
                AdFormat.INTERSTITIAL, AdPlacement.INTERSTITIAL_DEFAULT, "interstitial-slot",
                3, null, MobileAds.ERROR_DOMAIN, null, null,
            )
        }
    }

    @Test
    fun rewardedSecondSlotLogsMatchingRequestAndCategorizedFailure() {
        every { config.rewardedPoolMax } returns 2
        every { policy.getPolicy() } returns config
        AdsConsentRuntimeState.update(true)
        val manager = RewardedAdManager(context, logger, policy)
        setAdUnitId(manager, "rewarded-slot")
        val callback = slot<RewardedAdLoadCallback>()
        mockkStatic(RewardedAd::class)
        every { RewardedAd.load(any(), any(), any<AdRequest>(), capture(callback)) } just Runs

        manager.loadNextIfNeeded()

        verify(exactly = 1) {
            logger.logPreloadRequested(
                AdFormat.REWARDED, AdPlacement.REWARDED_DEFAULT, "rewarded-slot",
                null, "pool_next_slot",
            )
        }
        verify(exactly = 1) {
            logger.logRequest(AdFormat.REWARDED, AdPlacement.REWARDED_DEFAULT, "rewarded-slot", null)
        }
        callback.captured.onAdFailedToLoad(mockFailure(2))
        verify(exactly = 1) {
            logger.logFailedToLoad(
                AdFormat.REWARDED, AdPlacement.REWARDED_DEFAULT, "rewarded-slot",
                2, null, MobileAds.ERROR_DOMAIN, null, null,
            )
        }
    }

    @Test
    fun interstitialSecondSlotLoadedEventMatchesTheSDKRequest() {
        every { config.interstitialPoolMax } returns 2
        every { config.demandAwarePreloadEnabled } returns false
        every { policy.getPolicy() } returns config
        AdsConsentRuntimeState.update(true)
        val manager = InterstitialAdManager(
            appContext = context,
            preferencesDataSource = mockk<PreferencesDataSource>(relaxed = true),
            adRevenueLogger = logger,
            adsPolicyProvider = policy,
            fullScreenShowCoordinator = mockk(relaxed = true),
            adPreloadRequestBudget = mockk(relaxed = true),
        )
        setAdUnitId(manager, "interstitial-slot")
        val callback = slot<InterstitialAdLoadCallback>()
        mockkStatic(InterstitialAd::class)
        every { InterstitialAd.load(any(), any(), any<AdRequest>(), capture(callback)) } just Runs
        val info = mockk<ResponseInfo> { every { mediationAdapterClassName } returns "test-adapter" }
        val ad = mockk<InterstitialAd>(relaxed = true) {
            every { adUnitId } returns "interstitial-slot"
            every { responseInfo } returns info
        }

        manager.loadNextIfNeeded()
        callback.captured.onAdLoaded(ad)

        verify(exactly = 1) {
            logger.logLoaded(
                AdFormat.INTERSTITIAL, AdPlacement.INTERSTITIAL_DEFAULT,
                "interstitial-slot", null, match { it >= 0L }, "test-adapter",
            )
        }
    }

    @Test
    fun rewardedSecondSlotLoadedEventMatchesTheSDKRequest() {
        every { config.rewardedPoolMax } returns 2
        every { policy.getPolicy() } returns config
        AdsConsentRuntimeState.update(true)
        val manager = RewardedAdManager(context, logger, policy)
        setAdUnitId(manager, "rewarded-slot")
        val callback = slot<RewardedAdLoadCallback>()
        mockkStatic(RewardedAd::class)
        every { RewardedAd.load(any(), any(), any<AdRequest>(), capture(callback)) } just Runs
        val info = mockk<ResponseInfo> { every { mediationAdapterClassName } returns "test-adapter" }
        val ad = mockk<RewardedAd>(relaxed = true) {
            every { adUnitId } returns "rewarded-slot"
            every { responseInfo } returns info
        }

        manager.loadNextIfNeeded()
        callback.captured.onAdLoaded(ad)

        verify(exactly = 1) {
            logger.logLoaded(
                AdFormat.REWARDED, AdPlacement.REWARDED_DEFAULT,
                "rewarded-slot", null, match { it >= 0L }, "test-adapter",
            )
        }
    }

    @Test
    fun secondarySlotsDoNotRequestAdsWithoutConsent() {
        AdsConsentRuntimeState.update(false)
        val manager = RewardedAdManager(context, logger, policy)
        setAdUnitId(manager, "rewarded-slot")
        manager.loadNextIfNeeded()
        verify(exactly = 0) { logger.logRequest(any(), any(), any(), any()) }
        verify(exactly = 0) { policy.getPolicy() }
    }

    private fun setAdUnitId(manager: Any, adUnitId: String) {
        val field = manager.javaClass.getDeclaredField("currentAdUnitId")
        field.isAccessible = true
        field.set(manager, adUnitId)
    }

    private fun mockFailure(code: Int) = mockk<LoadAdError> {
        every { this@mockk.code } returns code
        every { domain } returns MobileAds.ERROR_DOMAIN
    }
}
