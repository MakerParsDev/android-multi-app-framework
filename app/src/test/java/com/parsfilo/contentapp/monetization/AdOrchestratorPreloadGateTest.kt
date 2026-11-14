package com.parsfilo.contentapp.monetization

import android.app.Activity
import android.content.Context
import com.parsfilo.contentapp.BuildConfig
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.datastore.UserPreferencesData
import com.parsfilo.contentapp.feature.ads.AdEligibility
import com.parsfilo.contentapp.feature.ads.AdGateChecker
import com.parsfilo.contentapp.feature.ads.AdManager
import com.parsfilo.contentapp.feature.ads.AdRevenueLogger
import com.parsfilo.contentapp.feature.ads.AdsConsentRuntimeState
import com.parsfilo.contentapp.feature.ads.AdsPlacementPolicyEvaluator
import com.parsfilo.contentapp.feature.ads.AdsPolicyConfig
import com.parsfilo.contentapp.feature.ads.AdsPolicyProvider
import com.parsfilo.contentapp.feature.ads.AppOpenAdManager
import com.parsfilo.contentapp.feature.ads.AppOpenAdManager.ShowCompletionReason
import com.parsfilo.contentapp.feature.ads.AppOpenEligibilityTracker
import com.parsfilo.contentapp.feature.ads.InterstitialAdManager
import com.parsfilo.contentapp.feature.ads.NativeAdManager
import com.parsfilo.contentapp.feature.ads.RewardedAdManager
import com.parsfilo.contentapp.feature.ads.RewardedInterstitialAdManager
import com.parsfilo.contentapp.feature.ads.RewardedInterstitialCoordinator
import com.parsfilo.contentapp.product.AppProductDefinition
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
    fun appOpenCompletionDoesNotReloadAfterAdFreeEntitlementBegins() = runTest {
        for (reason in listOf(ShowCompletionReason.ATTEMPTED, ShowCompletionReason.NOT_LOADED)) {
            adsAllowed.value = true
            AdsConsentRuntimeState.update(true)
            val preferences = mockk<PreferencesDataSource>()
            every { preferences.userData } returns flowOf(
                UserPreferencesData(
                    darkMode = false,
                    displayMode = "default",
                    fontSize = 16,
                    developerModeEnabled = false,
                    isPremium = false,
                    lastAppOpenAdShown = 0L,
                    rewardedAdFreeUntil = 0L,
                    rewardWatchCount = 0,
                    lastInterstitialShown = 0L,
                    lastRewardedInterstitialShown = 0L,
                    notificationsEnabled = true,
                ),
            )
            val eligibility = mockk<AdsPlacementPolicyEvaluator>()
            every { eligibility.evaluateAppOpen(any()) } returns AdEligibility.Allowed
            val tracker = mockk<AppOpenEligibilityTracker>()
            every { tracker.onResume(any()) } returns AppOpenEligibilityTracker.ResumeSnapshot(
                isColdStart = true,
                resumeGapMs = null,
                sessionCount = 1,
            )
            val adManager = mockk<AppOpenAdManager>(relaxed = true)
            var closeGateOnCompletion = true
            var revokeConsentOnCompletion = false
            coEvery { adManager.showAdIfAvailable(any(), any(), any(), any(), any()) } coAnswers {
                // Entitlement or UMP consent can change while the full-screen ad is finishing.
                if (closeGateOnCompletion) adsAllowed.value = false
                if (revokeConsentOnCompletion) AdsConsentRuntimeState.update(false)
                arg<(ShowCompletionReason) -> Unit>(4)(reason)
            }
            val orchestrator = AdOrchestrator(
                manager,
                adManager,
                interstitial,
                native,
                rewarded,
                rewardedInterstitial,
                mockk<AdRevenueLogger>(relaxed = true),
                gateChecker,
                policies,
                preferences,
                eligibility,
                mockk<RewardedInterstitialCoordinator>(relaxed = true),
                tracker,
                mockk<AdsConfigValidator>(relaxed = true),
            )
            val activity = mockk<Activity>(relaxed = true)
            every { activity.getString(any()) } returns "test-app-open-id"
            orchestrator.showAppOpenAdIfEligible(activity)
            runCurrent()
            verify(exactly = 0) { adManager.loadAd(any(), any(), any()) }

            // After the gate reopens, the existing post-show reload must still work.
            closeGateOnCompletion = false
            adsAllowed.value = true
            orchestrator.showAppOpenAdIfEligible(activity)
            runCurrent()
            verify(exactly = 1) { adManager.loadAd(any(), any(), any()) }

            revokeConsentOnCompletion = true
            AdsConsentRuntimeState.update(true)
            orchestrator.showAppOpenAdIfEligible(activity)
            runCurrent()
            verify(exactly = 1) { adManager.loadAd(any(), any(), any()) }
        }
    }

    @Test
    fun coldStartBootstrapSkipsAppOpenWarmupButBackgroundResumePreloads() = runTest {
        val routePolicy = mockk<AdsPolicyConfig>(relaxed = true)
        every { policies.getPolicy() } returns routePolicy
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } returns "test-ad-unit"
        every { context.packageName } returns "com.parsfilo.ismiazamduasi"
        val activity = mockk<Activity>(relaxed = true)
        every { activity.applicationContext } returns context
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
        orchestrator.initialize(activity, this)
        runCurrent()

        // Cold-start app-open display is forbidden by the placement evaluator.
        AdsConsentRuntimeState.update(true)
        sdkReady.value = true
        runCurrent()
        verify(exactly = 0) { appOpen.loadAd(any(), any(), any()) }

        // Background preparation must still ready ads for a later eligible resume.
        orchestrator.onAppPaused(context)
        runCurrent()
        verify(exactly = 1) { appOpen.loadAd(any(), any(), any()) }
    }

    @Test
    fun routeHintPreloadRespectsAdFreeEntitlementsAndConsent() = runTest {
        val routePolicy = mockk<AdsPolicyConfig>(relaxed = true)
        every { policies.getPolicy() } returns routePolicy
        every { routePolicy.isHotInterstitialRoute(any()) } returns true
        every { routePolicy.shouldUseAggressiveInterstitialPreload(any()) } returns true
        every { routePolicy.shouldUseAggressiveAppOpenPreload(any()) } returns true
        every { routePolicy.isInterstitialPlacementEnabled(any()) } returns true
        every { routePolicy.isAppOpenPlacementEnabled(any()) } returns true
        every { routePolicy.isBlockedContext(any()) } returns false
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
        every { context.getString(any()) } returns "test-ad-id"
        every { context.packageName } returns "com.parsfilo.mucizedualar"
        val activity = mockk<Activity>(relaxed = true)
        every { activity.applicationContext } returns context
        orchestrator.initialize(activity, this)
        runCurrent()

        AdsConsentRuntimeState.update(true)
        adsAllowed.value = false
        orchestrator.updateSessionContext(activeRoute = "content")
        runCurrent()
        verify(exactly = 0) { interstitial.loadAd(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { appOpen.loadAd(any(), any(), any()) }

        // The successful route-preload case requires an initialized SDK.
        sdkReady.value = true
        adsAllowed.value = true
        orchestrator.updateSessionContext(activeRoute = "prayer_list")
        runCurrent()
        // The SDK-ready observer and route-hint can each legitimately request a load.
        verify(atLeast = 1) { interstitial.loadAd(any(), any(), any(), any(), any()) }
        verify(atLeast = 1) { appOpen.loadAd(any(), any(), any()) }

        // Isolate the revocation period: no *new* request is permitted.
        clearMocks(interstitial, appOpen, recordedCalls = true, answers = false)
        AdsConsentRuntimeState.update(false)
        orchestrator.updateSessionContext(activeRoute = "prayer_detail")
        runCurrent()
        verify(exactly = 0) { interstitial.loadAd(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { appOpen.loadAd(any(), any(), any()) }
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

        // Once the SDK has initialized, normal background warm-up is allowed.
        sdkReady.value = true
        adsAllowed.value = true
        orchestrator.onAppPaused(context)
        runCurrent()
        verify(exactly = 1) { appOpen.loadAd(any(), any(), any()) }
    }

    @Test
    fun consentRefreshWaitsForSdkReadyWithoutLosingConsentResult() = runTest {
        // UMP callback can complete while GMA initialization is still asynchronous.
        // Consent success must reach the UI, but no ad loaders should start early.
        val activity = mockk<Activity>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { activity.applicationContext } returns context
        every { context.getString(any()) } returns "test-ad-id"
        every { context.packageName } returns "com.parsfilo.namazsurelerivedualarsesli"
        every { manager.refreshConsent(any(), any()) } answers {
            AdsConsentRuntimeState.update(true)
            secondArg<(Boolean) -> Unit>().invoke(true)
        }
        val orchestrator =
            AdOrchestrator(
                manager, appOpen, interstitial, native, rewarded, rewardedInterstitial,
                mockk<AdRevenueLogger>(relaxed = true),
                gateChecker, policies,
                mockk<PreferencesDataSource>(relaxed = true),
                mockk<AdsPlacementPolicyEvaluator>(relaxed = true),
                mockk<RewardedInterstitialCoordinator>(relaxed = true),
                mockk<AppOpenEligibilityTracker>(relaxed = true),
                mockk<AdsConfigValidator>(relaxed = true),
            )
        orchestrator.initialize(activity, this)
        runCurrent()

        var callbackResult: Boolean? = null
        orchestrator.refreshConsent(activity, this) { callbackResult = it }
        runCurrent()
        assertEquals(true, callbackResult)
        verify(exactly = 0) { interstitial.loadAd(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { appOpen.loadAd(any(), any(), any()) }

        // SDK readiness still enables interstitials, but a cold-start app-open
        // request cannot be shown and must wait for the background-resume path.
        sdkReady.value = true
        runCurrent()
        verify(exactly = 1) { interstitial.loadAd(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { appOpen.loadAd(any(), any(), any()) }
    }

    @Test
    fun pauseCannotWarmAppOpenBeforeSdkReady() = runTest {
        AdsConsentRuntimeState.update(true)
        val orchestrator =
            AdOrchestrator(
                manager, appOpen, interstitial, native, rewarded, rewardedInterstitial,
                mockk<AdRevenueLogger>(relaxed = true),
                gateChecker, policies,
                mockk<PreferencesDataSource>(relaxed = true),
                mockk<AdsPlacementPolicyEvaluator>(relaxed = true),
                mockk<RewardedInterstitialCoordinator>(relaxed = true),
                mockk<AppOpenEligibilityTracker>(relaxed = true),
                mockk<AdsConfigValidator>(relaxed = true),
            )
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } returns "test-ad-id"
        orchestrator.onAppPaused(context)
        runCurrent()
        verify(exactly = 0) { appOpen.loadAd(any(), any(), any()) }
    }

    @Test
    fun routeHintCannotPreloadBeforeSdkReady() = runTest {
        val routePolicy = mockk<AdsPolicyConfig>(relaxed = true)
        every { policies.getPolicy() } returns routePolicy
        every { routePolicy.isHotInterstitialRoute(any()) } returns true
        every { routePolicy.shouldUseAggressiveInterstitialPreload(any()) } returns true
        every { routePolicy.shouldUseAggressiveAppOpenPreload(any()) } returns true
        every { routePolicy.isInterstitialPlacementEnabled(any()) } returns true
        every { routePolicy.isAppOpenPlacementEnabled(any()) } returns true
        every { routePolicy.isBlockedContext(any()) } returns false
        val orchestrator =
            AdOrchestrator(
                manager, appOpen, interstitial, native, rewarded, rewardedInterstitial,
                mockk<AdRevenueLogger>(relaxed = true),
                gateChecker, policies,
                mockk<PreferencesDataSource>(relaxed = true),
                mockk<AdsPlacementPolicyEvaluator>(relaxed = true),
                mockk<RewardedInterstitialCoordinator>(relaxed = true),
                mockk<AppOpenEligibilityTracker>(relaxed = true),
                mockk<AdsConfigValidator>(relaxed = true),
            )
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } returns "test-ad-id"
        every { context.packageName } returns "com.parsfilo.namazsurelerivedualarsesli"
        val activity = mockk<Activity>(relaxed = true)
        every { activity.applicationContext } returns context
        orchestrator.initialize(activity, this)
        runCurrent()
        AdsConsentRuntimeState.update(true)
        orchestrator.updateSessionContext(activeRoute = "prayer_list")
        runCurrent()
        verify(exactly = 0) { interstitial.loadAd(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { appOpen.loadAd(any(), any(), any()) }
    }

    @Test
    fun userInitiatedRewardedRequestIsNotDroppedWhileSdkInitializes() = runTest {
        AdsConsentRuntimeState.update(true)
        val orchestrator =
            AdOrchestrator(
                manager, appOpen, interstitial, native, rewarded, rewardedInterstitial,
                mockk<AdRevenueLogger>(relaxed = true),
                gateChecker, policies,
                mockk<PreferencesDataSource>(relaxed = true),
                mockk<AdsPlacementPolicyEvaluator>(relaxed = true),
                mockk<RewardedInterstitialCoordinator>(relaxed = true),
                mockk<AppOpenEligibilityTracker>(relaxed = true),
                mockk<AdsConfigValidator>(relaxed = true),
            )
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } returns "test-ad-id"
        // Unlike automatic preloads this follows a direct tap, and the caller
        // waits for the SDK load result. Silently dropping it makes the user wait 10s.
        orchestrator.requestRewardedLoad(context)
        runCurrent()
        verify(exactly = 1) { rewarded.loadAd(any(), any(), any()) }
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
