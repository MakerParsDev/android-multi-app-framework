package com.parsfilo.contentapp.monetization

import android.app.Activity
import android.content.Context
import com.parsfilo.contentapp.BuildConfig
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.feature.ads.AdGateChecker
import com.parsfilo.contentapp.feature.ads.AdManager
import com.parsfilo.contentapp.feature.ads.AdRevenueLogger
import com.parsfilo.contentapp.feature.ads.AdsConsentRuntimeState
import com.parsfilo.contentapp.feature.ads.AdsPlacementPolicyEvaluator
import com.parsfilo.contentapp.feature.ads.AdsPolicyProvider
import com.parsfilo.contentapp.feature.ads.AppOpenAdManager
import com.parsfilo.contentapp.feature.ads.AppOpenEligibilityTracker
import com.parsfilo.contentapp.feature.ads.InterstitialAdManager
import com.parsfilo.contentapp.feature.ads.NativeAdManager
import com.parsfilo.contentapp.feature.ads.RewardedAdManager
import com.parsfilo.contentapp.feature.ads.RewardedInterstitialAdManager
import com.parsfilo.contentapp.feature.ads.RewardedInterstitialCoordinator
import com.parsfilo.contentapp.product.AppProductDefinition
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdOrchestratorPreloadGateTest {
    private val sdkReady = MutableStateFlow(false)
    private val adsAllowed = MutableStateFlow(true)
    private val manager = mockk<AdManager>(relaxed = true)
    private val appOpen = mockk<AppOpenAdManager>(relaxed = true)
    private val interstitial = mockk<InterstitialAdManager>(relaxed = true)
    private val native = mockk<NativeAdManager>(relaxed = true)
    private val rewarded = mockk<RewardedAdManager>(relaxed = true)
    private val rewardedInterstitial = mockk<RewardedInterstitialAdManager>(relaxed = true)
    private val gateChecker = mockk<AdGateChecker>(relaxed = true)
    private val policies = mockk<AdsPolicyProvider>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        AdsConsentRuntimeState.update(false)
        every { manager.isSdkReady } returns sdkReady
        every { gateChecker.shouldShowAds } returns adsAllowed
    }

    @After
    fun cleanup() {
        AdsConsentRuntimeState.update(false)
        Dispatchers.resetMain()
    }

    @Test
    fun pauseDoesNotPreloadForPremiumOrTemporaryAdFreeUsers() = runTest {
        val orchestrator =
            AdOrchestrator(
                manager,
                appOpen,
                interstitial,
                native,
                rewarded,
                rewardedInterstitial,
                mockk<AdRevenueLogger>(relaxed = true),
                gateChecker,
                policies,
                mockk<PreferencesDataSource>(relaxed = true),
                mockk<AdsPlacementPolicyEvaluator>(relaxed = true),
                mockk<RewardedInterstitialCoordinator>(relaxed = true),
                mockk<AppOpenEligibilityTracker>(relaxed = true),
                mockk<AdsConfigValidator>(relaxed = true),
            )
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } returns "test-app-open-id"
        every { context.packageName } returns "com.parsfilo.ismiazamduasi"
        AdsConsentRuntimeState.update(true)

        // AdGateChecker maps both premium and rewarded ad-free entitlements to false.
        adsAllowed.value = false
        orchestrator.onAppPaused(context)
        runCurrent()
        verify(exactly = 0) { appOpen.loadAd(any(), any(), any()) }

        adsAllowed.value = true
        orchestrator.onAppPaused(context)
        runCurrent()
        verify(exactly = 1) { appOpen.loadAd(any(), any(), any()) }
    }

    @Test
    fun preloadsOnlyAfterConsentGateAndSdkReadyThenClearsOnRevocation() = runTest {
        val orchestrator =
            AdOrchestrator(
                manager,
                appOpen,
                interstitial,
                native,
                rewarded,
                rewardedInterstitial,
                mockk<AdRevenueLogger>(relaxed = true),
                gateChecker,
                policies,
                mockk<PreferencesDataSource>(relaxed = true),
                mockk<AdsPlacementPolicyEvaluator>(relaxed = true),
                mockk<RewardedInterstitialCoordinator>(relaxed = true),
                mockk<AppOpenEligibilityTracker>(relaxed = true),
                mockk<AdsConfigValidator>(relaxed = true),
            )
        // The production preload path requests flavor-specific Android strings.
        // Stub those resources so this test exercises the gate, not APK resource setup.
        val appContext = mockk<Context>(relaxed = true)
        every { appContext.getString(any()) } returns ""
        every { appContext.packageName } returns "com.parsfilo.ismiazamduasi"
        val activity = mockk<Activity>(relaxed = true)
        every { activity.applicationContext } returns appContext
        orchestrator.initialize(activity, this)
        runCurrent()

        // Both privacy and ad-show gates can be open while GMA initialization is pending.
        AdsConsentRuntimeState.update(true)
        runCurrent()
        verify(exactly = 0) { interstitial.loadAd(any(), any(), any(), any(), any()) }

        sdkReady.value = true
        runCurrent()
        verify(exactly = 1) { interstitial.loadAd(any(), any(), any(), any(), any()) }

        // A paid or temporarily ad-free user must not preload, even with the SDK ready.
        adsAllowed.value = false
        runCurrent()
        verify(exactly = 1) { interstitial.loadAd(any(), any(), any(), any(), any()) }

        adsAllowed.value = true
        runCurrent()
        verify(exactly = 2) { interstitial.loadAd(any(), any(), any(), any(), any()) }

        // Short-content flavors have fewer than 12 verses and cannot compose
        // an inline native slot. Avoid loading ads they can never display.
        val noNativeSlotFlavors =
            setOf("amenerrasulu", "ayetelkursi", "bereketduasi", "insirahsuresi", "nazarayeti")
        if (BuildConfig.FLAVOR_NAME in noNativeSlotFlavors) {
            assertTrue(AppProductDefinition.current.hasCapability("no_inline_native_slot"))
            verify(exactly = 0) { native.loadAds(any(), any(), any(), any()) }
        } else {
            // Other flavors retain their existing native preload behavior.
            verify(atLeast = 1) { native.loadAds(any(), any(), any(), any()) }
        }

        AdsConsentRuntimeState.update(false)
        runCurrent()
        verify(atLeast = 1) { interstitial.clearAd() }

        // SDK ready alone cannot reopen the ad gate after consent is revoked.
        assertEquals(false, AdsConsentRuntimeState.canRequestAds.value)
        runCurrent()
        verify(exactly = 2) { interstitial.loadAd(any(), any(), any(), any(), any()) }
    }
}
