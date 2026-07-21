package com.parsfilo.contentapp.monetization

import android.app.Activity
import android.content.Context
import com.parsfilo.contentapp.BuildConfig
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.feature.ads.AdEligibility
import com.parsfilo.contentapp.feature.ads.AdFormat
import com.parsfilo.contentapp.feature.ads.AdGateChecker
import com.parsfilo.contentapp.feature.ads.AdManager
import com.parsfilo.contentapp.feature.ads.AdPlacement
import com.parsfilo.contentapp.feature.ads.AdRequestContext
import com.parsfilo.contentapp.feature.ads.AdRevenueLogger
import com.parsfilo.contentapp.feature.ads.AdShowDiagnosticContext
import com.parsfilo.contentapp.feature.ads.AdSuppressReason
import com.parsfilo.contentapp.feature.ads.AdsConsentRuntimeState
import com.parsfilo.contentapp.feature.ads.AdsPlacementPolicyEvaluator
import com.parsfilo.contentapp.feature.ads.AdsPolicyProvider
import com.parsfilo.contentapp.feature.ads.AppOpenAdManager
import com.parsfilo.contentapp.feature.ads.AppOpenAdManager.ShowCompletionReason
import com.parsfilo.contentapp.feature.ads.AppOpenEligibilityTracker
import com.parsfilo.contentapp.feature.ads.AppOpenTriggerReason
import com.parsfilo.contentapp.feature.ads.InterstitialAdManager
import com.parsfilo.contentapp.feature.ads.InterstitialTriggerKind
import com.parsfilo.contentapp.feature.ads.NativeAdManager
import com.parsfilo.contentapp.feature.ads.RewardedAdManager
import com.parsfilo.contentapp.feature.ads.RewardedInterstitialAdManager
import com.parsfilo.contentapp.feature.ads.RewardedInterstitialCoordinator
import com.parsfilo.contentapp.feature.ads.RewardedInterstitialIntroSpec
import com.parsfilo.contentapp.feature.ads.RewardedInterstitialLaunchToken
import com.parsfilo.contentapp.feature.ads.SystemTimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class AdSessionContext(
    val activeRoute: String? = null,
    val contentType: String? = null,
    val verseReadCount: Int = 0,
    val audioPlayedThisSession: Boolean = false,
    val sessionStartedAtMs: Long = System.currentTimeMillis(),
)

private enum class PreloadReason(val analyticsValue: String) {
    INITIALIZE("initialize"),
    GATE_OPENED("gate_opened"),
    PAUSE("pause"),
    POST_SHOW("post_show"),
    NOT_LOADED_RECOVERY("not_loaded_recovery"),
    ROUTE_HINT("route_hint"),
    REFRESH_CONSENT("refresh_consent"),
}

@Singleton
class AdOrchestrator
    @Inject
    constructor(
        private val adManager: AdManager,
        private val appOpenAdManager: AppOpenAdManager,
        private val interstitialAdManager: InterstitialAdManager,
        val nativeAdManager: NativeAdManager,
        private val rewardedAdManager: RewardedAdManager,
        internal val rewardedInterstitialAdManager: RewardedInterstitialAdManager,
        private val adRevenueLogger: AdRevenueLogger,
        private val adGateChecker: AdGateChecker,
        private val adsPolicyProvider: AdsPolicyProvider,
        private val preferencesDataSource: PreferencesDataSource,
        private val placementPolicyEvaluator: AdsPlacementPolicyEvaluator,
        private val rewardedInterstitialCoordinator: RewardedInterstitialCoordinator,
        private val appOpenEligibilityTracker: AppOpenEligibilityTracker,
        private val adsConfigValidator: AdsConfigValidator,
    ) {
        private val orchestratorScope = CoroutineScope(SupervisorJob() + Main.immediate)
        private var rewardedShownThisSession: Int = 0
        private var rewardedInterstitialShownThisSession: Int = 0
        private var interstitialShownThisSession: Int = 0

        @Volatile
        private var adSessionContext: AdSessionContext = AdSessionContext()

        @Volatile
        private var adGateObserverStarted: Boolean = false

        @Volatile
        private var appOpenWarmupRequested: Boolean = false

        @Volatile
        private var applicationContextRef: Context? = null

        @Volatile
        private var lastInterstitialIntentAtMs: Long = 0L

        @Volatile
        private var lastInterstitialIntentKey: String? = null

        fun initialize(
            activity: Activity,
            scope: CoroutineScope,
        ) {
            Timber.d(
                "AdOrchestrator initialize package=%s testAds=%s",
                activity.packageName,
                BuildConfig.USE_TEST_ADS,
            )
            applicationContextRef = activity.applicationContext
            adsConfigValidator.validateOrThrow(activity, BuildConfig.USE_TEST_ADS)
            startAdGateObserver(activity.applicationContext)
            adManager.initialize(activity) {
                scope.launch(Main.immediate) {
                    if (!adGateChecker.shouldShowAds.first()) {
                        Timber.d("AdOrchestrator initialize: ad gate closed, preload skipped")
                        return@launch
                    }
                    requestAdBootstrap(
                        context = activity.applicationContext,
                        reason = PreloadReason.INITIALIZE,
                        includeAppOpenWarmup = true,
                    )
                }
            }
        }

        fun destroy() {
            Timber.d("AdOrchestrator destroy")
            nativeAdManager.destroyAds()
        }

        fun onAppPaused(context: Context? = null) {
            Timber.d("AdOrchestrator onAppPaused")
            appOpenEligibilityTracker.onPause()
            if (context == null) {
                Timber.d("AppOpen preload skipped on pause: context unavailable")
                return
            }
            if (appOpenAdManager.isAdReady()) {
                Timber.d("AppOpen preload skipped on pause: ready ad already cached")
                return
            }
            Timber.d("AppOpen preload requested on pause route=%s", adSessionContext.activeRoute)
            appOpenAdManager.loadAd(
                AppAdUnitIds.resolvePlacement(
                    context,
                    AdPlacement.APP_OPEN_RESUME,
                    BuildConfig.USE_TEST_ADS,
                ),
                AdPlacement.APP_OPEN_RESUME,
                loadReason = PreloadReason.PAUSE.analyticsValue,
            )
        }

        fun isAppOpenAdShowing(): Boolean = appOpenAdManager.isShowingAdNow()

        val rewardedAdIsReady: kotlinx.coroutines.flow.StateFlow<Boolean>
            get() = rewardedAdManager.isAdReady

        fun rewardedAdIsReadyNow(): Boolean = rewardedAdManager.isAdReadyNow()

        fun updateSessionContext(
            activeRoute: String? = null,
            contentType: String? = null,
            verseReadIncrement: Int = 0,
            audioPlayed: Boolean? = null,
        ) {
            val previous = adSessionContext
            adSessionContext =
                previous.copy(
                    activeRoute = activeRoute ?: previous.activeRoute,
                    contentType = contentType ?: previous.contentType,
                    verseReadCount =
                        (previous.verseReadCount + verseReadIncrement).coerceAtLeast(
                            0,
                        ),
                    audioPlayedThisSession = audioPlayed ?: previous.audioPlayedThisSession,
                )
            Timber.d(
                "Ad session updated route=%s contentType=%s verseReadCount=%d audioPlayed=%s",
                adSessionContext.activeRoute,
                adSessionContext.contentType,
                adSessionContext.verseReadCount,
                adSessionContext.audioPlayedThisSession,
            )
            if (activeRoute != null && activeRoute != previous.activeRoute) {
                maybeRequestOpportunisticPreload(activeRoute)
            }
        }

        fun buildRewardedInterstitialIntro(placement: AdPlacement): RewardedInterstitialIntroSpec =
            rewardedInterstitialCoordinator.buildIntroSpec(placement)

        fun onRewardedInterstitialIntroShown(
            placement: AdPlacement,
            route: String?,
            adUnitId: String,
        ) {
            rewardedInterstitialCoordinator.onIntroShown(placement, adUnitId, route)
        }

        fun onRewardedInterstitialIntroSkipped(
            placement: AdPlacement,
            route: String?,
            adUnitId: String,
        ) {
            rewardedInterstitialCoordinator.onIntroSkipped(placement, adUnitId, route)
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.REWARDED_INTERSTITIAL,
                placement = placement,
                adUnitId = adUnitId,
                suppressReason = AdSuppressReason.INTRO_SKIPPED,
                route = route,
            )
        }

        fun confirmRewardedInterstitialIntro(
            placement: AdPlacement,
            route: String?,
            adUnitId: String,
        ): RewardedInterstitialLaunchToken = rewardedInterstitialCoordinator.confirmIntro(placement, adUnitId, route)

        suspend fun showInterstitialIfEligible(
            activity: Activity,
            placement: AdPlacement = AdPlacement.INTERSTITIAL_DEFAULT,
            route: String? = null,
            triggerKind: InterstitialTriggerKind = InterstitialTriggerKind.NAV_BREAK,
            onAdDismissed: () -> Unit = {},
        ) {
            val contextRoute = route ?: adSessionContext.activeRoute
            val screenRoute = adSessionContext.activeRoute
            adRevenueLogger.logShowIntent(
                adFormat = AdFormat.INTERSTITIAL,
                placement = placement,
                route = contextRoute,
                trigger = triggerKind.analyticsValue,
                adReady = interstitialAdManager.isAdReady(),
            )
            Timber.d(
                "Interstitial show requested placement=%s route=%s screenRoute=%s trigger=%s sessionCount=%d",
                placement.analyticsValue,
                contextRoute,
                screenRoute,
                triggerKind.analyticsValue,
                interstitialShownThisSession,
            )
            if (shouldCollapseInterstitialOpportunity(triggerKind, contextRoute)) {
                adRevenueLogger.logShowBlocked(
                    adFormat = AdFormat.INTERSTITIAL,
                    placement = placement,
                    suppressReason = AdSuppressReason.RAPID_REPEAT,
                    route = contextRoute,
                    trigger = triggerKind.analyticsValue,
                )
                onAdDismissed()
                return
            }
            val prefs = preferencesDataSource.userData.first()
            when (
                val eligibility =
                    placementPolicyEvaluator.evaluateInterstitial(
                        AdRequestContext(
                            format = AdFormat.INTERSTITIAL,
                            placement = placement,
                            route = contextRoute,
                            screenRoute = screenRoute,
                            privacyState = AdsConsentRuntimeState.state.value,
                            isPremium = prefs.isPremium,
                            isRewardedAdFree =
                                prefs.rewardedAdFreeUntil > System.currentTimeMillis(),
                            sessionCount = interstitialShownThisSession,
                            lastShownAtMs = prefs.lastInterstitialShown,
                            resumeGapMs = null,
                            contentInProgress = isContentInProgress(screenRoute, triggerKind),
                            interstitialTriggerKind = triggerKind,
                        ),
                    )
            ) {
                is AdEligibility.Blocked -> {
                    Timber.d(
                        "Interstitial blocked placement=%s route=%s reason=%s",
                        placement.analyticsValue,
                        contextRoute,
                        eligibility.reason.analyticsValue,
                    )
                    adRevenueLogger.logSuppressed(
                        adFormat = AdFormat.INTERSTITIAL,
                        placement = placement,
                        adUnitId =
                            AppAdUnitIds.resolvePlacement(
                                activity,
                                placement,
                                BuildConfig.USE_TEST_ADS,
                            ),
                        suppressReason = eligibility.reason,
                        route = contextRoute,
                    )
                    adRevenueLogger.logShowBlocked(
                        adFormat = AdFormat.INTERSTITIAL,
                        placement = placement,
                        suppressReason = eligibility.reason,
                        route = contextRoute,
                        trigger = triggerKind.analyticsValue,
                    )
                    onAdDismissed()
                    return
                }
                AdEligibility.Allowed -> Unit
            }
            Timber.d(
                "Interstitial allowed placement=%s route=%s",
                placement.analyticsValue,
                contextRoute,
            )
            interstitialAdManager.showAd(
                activity = activity,
                placement = placement,
                route = contextRoute,
                triggerKind = triggerKind,
                onAdImpression = { adUnitId ->
                    interstitialShownThisSession += 1
                    Timber.d(
                        "Interstitial impression placement=%s route=%s sessionCount=%d adUnit=%s",
                        placement.analyticsValue,
                        contextRoute,
                        interstitialShownThisSession,
                        adUnitId,
                    )
                    logAdAfterEngagement(
                        adFormat = AdFormat.INTERSTITIAL,
                        placement = placement,
                        adUnitId = adUnitId,
                        route = contextRoute,
                    )
                },
            ) {
                onAdDismissed()
            }
        }

        fun recordAppOpenSuppression(
            context: Context,
            reason: AdSuppressReason,
            triggerReason: AppOpenTriggerReason,
        ) {
            val route = adSessionContext.activeRoute
            val adUnitId =
                AppAdUnitIds.resolvePlacement(
                    context,
                    AdPlacement.APP_OPEN_RESUME,
                    BuildConfig.USE_TEST_ADS,
                )
            adRevenueLogger.logSuppressed(
                adFormat = AdFormat.APP_OPEN,
                placement = AdPlacement.APP_OPEN_RESUME,
                adUnitId = adUnitId,
                suppressReason = reason,
                route = route,
            )
            adRevenueLogger.logShowBlocked(
                adFormat = AdFormat.APP_OPEN,
                placement = AdPlacement.APP_OPEN_RESUME,
                suppressReason = reason,
                route = route,
                trigger = triggerReason.analyticsValue,
                diagnostics = AdShowDiagnosticContext(currentAdUnitId = adUnitId),
            )
        }

        suspend fun showAppOpenAdIfEligible(activity: Activity) {
            showAppOpenAdIfEligible(activity, AppOpenTriggerReason.RESUME)
        }

        suspend fun showAppOpenAdIfEligible(
            activity: Activity,
            triggerReason: AppOpenTriggerReason,
        ) {
            Timber.d(
                "AppOpen show requested route=%s trigger=%s",
                adSessionContext.activeRoute,
                triggerReason.analyticsValue,
            )
            val prefs = preferencesDataSource.userData.first()
            val resumeSnapshot = appOpenEligibilityTracker.onResume()
            val route = adSessionContext.activeRoute
            adRevenueLogger.logShowIntent(
                adFormat = AdFormat.APP_OPEN,
                placement = AdPlacement.APP_OPEN_RESUME,
                route = route,
                trigger = triggerReason.analyticsValue,
                adReady = appOpenAdManager.isAdReady(),
            )
            when (
                val eligibility =
                    placementPolicyEvaluator.evaluateAppOpen(
                        AdRequestContext(
                            format = AdFormat.APP_OPEN,
                            placement = AdPlacement.APP_OPEN_RESUME,
                            route = route,
                            screenRoute = route,
                            privacyState = AdsConsentRuntimeState.state.value,
                            isPremium = prefs.isPremium,
                            isRewardedAdFree =
                                prefs.rewardedAdFreeUntil > System.currentTimeMillis(),
                            sessionCount = resumeSnapshot.sessionCount,
                            lastShownAtMs = prefs.lastAppOpenAdShown,
                            resumeGapMs =
                                if (resumeSnapshot.isColdStart) {
                                    Long.MAX_VALUE
                                } else {
                                    resumeSnapshot.resumeGapMs
                                },
                            isColdStart = resumeSnapshot.isColdStart,
                            contentInProgress = isContentInProgress(route),
                            appOpenTriggerReason = triggerReason,
                        ),
                    )
            ) {
                is AdEligibility.Blocked -> {
                    Timber.d(
                        "AppOpen blocked route=%s reason=%s",
                        route,
                        eligibility.reason.analyticsValue,
                    )
                    adRevenueLogger.logSuppressed(
                        adFormat = AdFormat.APP_OPEN,
                        placement = AdPlacement.APP_OPEN_RESUME,
                        adUnitId =
                            AppAdUnitIds.resolvePlacement(
                                activity,
                                AdPlacement.APP_OPEN_RESUME,
                                BuildConfig.USE_TEST_ADS,
                            ),
                        suppressReason = eligibility.reason,
                        route = route,
                    )
                    adRevenueLogger.logShowBlocked(
                        adFormat = AdFormat.APP_OPEN,
                        placement = AdPlacement.APP_OPEN_RESUME,
                        suppressReason = eligibility.reason,
                        route = route,
                        trigger = triggerReason.analyticsValue,
                    )
                    return
                }
                AdEligibility.Allowed -> Unit
            }
            Timber.d(
                "AppOpen allowed route=%s coldStart=%s sessionCount=%d",
                route,
                resumeSnapshot.isColdStart,
                resumeSnapshot.sessionCount,
            )
            if (activity.isFinishing || activity.isDestroyed) {
                Timber.w("AppOpen show aborted: activity is invalid route=%s", route)
                recordAppOpenSuppression(
                    context = activity.applicationContext,
                    reason = AdSuppressReason.ACTIVITY_UNAVAILABLE,
                    triggerReason = triggerReason,
                )
                return
            }
            appOpenAdManager.showAdIfAvailable(
                activity = activity,
                route = route,
                triggerReason = triggerReason,
                onAdImpression = { adUnitId ->
                    appOpenEligibilityTracker.onShown()
                    Timber.d("AppOpen impression route=%s adUnit=%s", route, adUnitId)
                    logAdAfterEngagement(
                        adFormat = AdFormat.APP_OPEN,
                        placement = AdPlacement.APP_OPEN_RESUME,
                        adUnitId = adUnitId,
                        route = route,
                    )
                },
            ) { completionReason ->
                when (completionReason) {
                    ShowCompletionReason.ATTEMPTED -> {
                        Timber.d("AppOpen show completed after attempt; requesting reload")
                        appOpenAdManager.loadAd(
                            AppAdUnitIds.resolvePlacement(
                                activity,
                                AdPlacement.APP_OPEN_RESUME,
                                BuildConfig.USE_TEST_ADS,
                            ),
                            AdPlacement.APP_OPEN_RESUME,
                            loadReason = PreloadReason.POST_SHOW.analyticsValue,
                        )
                    }
                    ShowCompletionReason.NOT_LOADED -> {
                        Timber.d(
                            "AppOpen show completed without loaded ad; requesting top-up preload",
                        )
                        appOpenAdManager.loadAd(
                            AppAdUnitIds.resolvePlacement(
                                activity,
                                AdPlacement.APP_OPEN_RESUME,
                                BuildConfig.USE_TEST_ADS,
                            ),
                            AdPlacement.APP_OPEN_RESUME,
                            loadReason = PreloadReason.NOT_LOADED_RECOVERY.analyticsValue,
                        )
                    }
                    ShowCompletionReason.BLOCKED -> {
                        Timber.d("AppOpen show completed while blocked; reload skipped")
                    }
                }
            }
        }

        suspend fun showRewardedIfEligible(
            activity: Activity,
            placement: AdPlacement = AdPlacement.REWARDED_REWARDS_SCREEN,
            route: String? = null,
            onUserEarnedReward: () -> Unit = {},
            onAdDismissed: () -> Unit = {},
        ) {
            val contextRoute = route ?: adSessionContext.activeRoute
            Timber.d(
                "Rewarded show requested placement=%s route=%s sessionCount=%d adReady=%s",
                placement.analyticsValue,
                contextRoute,
                rewardedShownThisSession,
                rewardedAdManager.isAdReadyNow(),
            )
            adRevenueLogger.logShowIntent(
                adFormat = AdFormat.REWARDED,
                placement = placement,
                route = contextRoute,
                trigger = "user_action",
                adReady = rewardedAdManager.isAdReadyNow(),
            )
            val prefs = preferencesDataSource.userData.first()
            when (
                val eligibility = placementPolicyEvaluator.evaluateRewarded(
                    AdRequestContext(
                        format = AdFormat.REWARDED,
                        placement = placement,
                        route = contextRoute,
                        privacyState = AdsConsentRuntimeState.state.value,
                        isPremium = prefs.isPremium,
                        isRewardedAdFree = prefs.rewardedAdFreeUntil > System.currentTimeMillis(),
                        sessionCount = rewardedShownThisSession,
                        lastShownAtMs = null,
                        resumeGapMs = null,
                        contentInProgress = false,
                    ),
                )
            ) {
                is AdEligibility.Blocked -> {
                    Timber.d(
                        "Rewarded blocked placement=%s route=%s reason=%s",
                        placement.analyticsValue,
                        contextRoute,
                        eligibility.reason.analyticsValue,
                    )
                    adRevenueLogger.logSuppressed(
                        adFormat = AdFormat.REWARDED,
                        placement = placement,
                        adUnitId = AppAdUnitIds.resolvePlacement(activity, placement, BuildConfig.USE_TEST_ADS),
                        suppressReason = eligibility.reason,
                        route = contextRoute,
                    )
                    adRevenueLogger.logShowBlocked(
                        adFormat = AdFormat.REWARDED,
                        placement = placement,
                        suppressReason = eligibility.reason,
                        route = contextRoute,
                        trigger = "user_action",
                    )
                    onAdDismissed()
                    return
                }
                AdEligibility.Allowed -> Unit
            }
            if (activity.isFinishing || activity.isDestroyed) {
                Timber.w("Rewarded show aborted: activity is invalid placement=%s", placement.analyticsValue)
                val adUnitId = AppAdUnitIds.resolvePlacement(activity, placement, BuildConfig.USE_TEST_ADS)
                adRevenueLogger.logSuppressed(
                    adFormat = AdFormat.REWARDED,
                    placement = placement,
                    adUnitId = adUnitId,
                    suppressReason = AdSuppressReason.ACTIVITY_UNAVAILABLE,
                    route = contextRoute,
                )
                adRevenueLogger.logShowBlocked(
                    adFormat = AdFormat.REWARDED,
                    placement = placement,
                    suppressReason = AdSuppressReason.ACTIVITY_UNAVAILABLE,
                    route = contextRoute,
                    trigger = "user_action",
                    diagnostics = AdShowDiagnosticContext(currentAdUnitId = adUnitId),
                )
                onAdDismissed()
                return
            }
            rewardedAdManager.showAd(
                activity = activity,
                onUserEarnedReward = {
                    orchestratorScope.launch {
                        rewardedShownThisSession += 1
                        Timber.d(
                            "Rewarded reward earned placement=%s route=%s sessionCount=%d",
                            placement.analyticsValue,
                            contextRoute,
                            rewardedShownThisSession,
                        )
                        adGateChecker.onRewardEarned()
                        onUserEarnedReward()
                    }
                },
                onAdDismissed = {
                    Timber.d("Rewarded dismissed; keeping load on-demand placement=%s", placement.analyticsValue)
                    onAdDismissed()
                },
            )
        }

        suspend fun showRewardedInterstitialIfEligible(
            activity: Activity,
            launchToken: RewardedInterstitialLaunchToken,
            placement: AdPlacement = AdPlacement.REWARDED_INTERSTITIAL_DEFAULT,
            route: String? = null,
            onUserEarnedReward: () -> Unit = {},
            onAdDismissed: () -> Unit = {},
        ) {
            Timber.d(
                "RewardedInterstitial show requested placement=%s route=%s sessionCount=%d",
                placement.analyticsValue,
                route ?: adSessionContext.activeRoute,
                rewardedInterstitialShownThisSession,
            )
            val prefs = preferencesDataSource.userData.first()
            val contextRoute = route ?: adSessionContext.activeRoute
            adRevenueLogger.logShowIntent(
                adFormat = AdFormat.REWARDED_INTERSTITIAL,
                placement = placement,
                route = contextRoute,
                trigger = "intro_confirmed",
                adReady = rewardedInterstitialAdManager.isAdReadyNow(),
            )
            when (
                val eligibility =
                    placementPolicyEvaluator.evaluateRewardedInterstitial(
                        AdRequestContext(
                            format = AdFormat.REWARDED_INTERSTITIAL,
                            placement = placement,
                            route = contextRoute,
                            privacyState = AdsConsentRuntimeState.state.value,
                            isPremium = prefs.isPremium,
                            isRewardedAdFree =
                                prefs.rewardedAdFreeUntil > System.currentTimeMillis(),
                            sessionCount = rewardedInterstitialShownThisSession,
                            lastShownAtMs = prefs.lastRewardedInterstitialShown,
                            resumeGapMs = null,
                            contentInProgress = false,
                        ),
                    )
            ) {
                is AdEligibility.Blocked -> {
                    Timber.d(
                        "RewardedInterstitial blocked placement=%s route=%s reason=%s",
                        placement.analyticsValue,
                        contextRoute,
                        eligibility.reason.analyticsValue,
                    )
                    val adUnitId =
                        AppAdUnitIds.resolvePlacement(
                            activity,
                            placement,
                            BuildConfig.USE_TEST_ADS,
                        )
                    adRevenueLogger.logSuppressed(
                        adFormat = AdFormat.REWARDED_INTERSTITIAL,
                        placement = placement,
                        adUnitId = adUnitId,
                        suppressReason = eligibility.reason,
                        route = contextRoute,
                    )
                    adRevenueLogger.logShowBlocked(
                        adFormat = AdFormat.REWARDED_INTERSTITIAL,
                        placement = placement,
                        suppressReason = eligibility.reason,
                        route = contextRoute,
                        trigger = "intro_confirmed",
                        diagnostics = AdShowDiagnosticContext(currentAdUnitId = adUnitId),
                    )
                    onAdDismissed()
                    return
                }
                AdEligibility.Allowed -> Unit
            }
            Timber.d(
                "RewardedInterstitial allowed placement=%s route=%s",
                placement.analyticsValue,
                contextRoute,
            )
            if (!rewardedInterstitialAdManager.isAdReadyNow()) {
                rewardedInterstitialAdManager.loadAd(
                    AppAdUnitIds.resolvePlacement(activity, placement, BuildConfig.USE_TEST_ADS),
                    placement,
                    contextRoute,
                )
                val adReady =
                    withTimeoutOrNull(10_000L) {
                        rewardedInterstitialAdManager.isAdReady
                            .filter { it }
                            .first()
                    } != null
                if (!adReady) {
                    Timber.w(
                        "RewardedInterstitial load timed out placement=%s route=%s",
                        placement.analyticsValue,
                        contextRoute,
                    )
                    adRevenueLogger.logShowNotLoaded(
                        adFormat = AdFormat.REWARDED_INTERSTITIAL,
                        placement = placement,
                        route = contextRoute,
                        trigger = "intro_confirmed",
                        diagnostics =
                            AdShowDiagnosticContext(
                                currentAdUnitId =
                                    AppAdUnitIds.resolvePlacement(
                                        activity,
                                        placement,
                                        BuildConfig.USE_TEST_ADS,
                                    ),
                            ),
                    )
                    onAdDismissed()
                    return
                }
            }
            if (activity.isFinishing || activity.isDestroyed) {
                val adUnitId = AppAdUnitIds.resolvePlacement(activity, placement, BuildConfig.USE_TEST_ADS)
                adRevenueLogger.logSuppressed(
                    adFormat = AdFormat.REWARDED_INTERSTITIAL,
                    placement = placement,
                    adUnitId = adUnitId,
                    suppressReason = AdSuppressReason.ACTIVITY_UNAVAILABLE,
                    route = contextRoute,
                )
                adRevenueLogger.logShowBlocked(
                    adFormat = AdFormat.REWARDED_INTERSTITIAL,
                    placement = placement,
                    suppressReason = AdSuppressReason.ACTIVITY_UNAVAILABLE,
                    route = contextRoute,
                    trigger = "intro_confirmed",
                    diagnostics = AdShowDiagnosticContext(currentAdUnitId = adUnitId),
                )
                onAdDismissed()
                return
            }
            rewardedInterstitialAdManager.showAfterConfirmedIntro(
                launchToken = launchToken,
                activity = activity,
                placement = placement,
                route = contextRoute,
                onAdImpression = { adUnitId ->
                    orchestratorScope.launch {
                        rewardedInterstitialShownThisSession += 1
                        preferencesDataSource.setLastRewardedInterstitialShown(
                            System.currentTimeMillis(),
                        )
                    }
                    Timber.d(
                        "RewardedInterstitial impression placement=%s route=%s sessionCount=%d adUnit=%s",
                        placement.analyticsValue,
                        contextRoute,
                        rewardedInterstitialShownThisSession,
                        adUnitId,
                    )
                    logAdAfterEngagement(
                        adFormat = AdFormat.REWARDED_INTERSTITIAL,
                        placement = placement,
                        adUnitId = adUnitId,
                        route = contextRoute,
                    )
                },
                onUserEarnedReward = { _, _ ->
                    orchestratorScope.launch {
                        Timber.d(
                            "RewardedInterstitial reward earned placement=%s route=%s",
                            placement.analyticsValue,
                            contextRoute,
                        )
                        adGateChecker.onRewardEarned()
                        onUserEarnedReward()
                    }
                },
                onAdDismissed = {
                    Timber.d(
                        "RewardedInterstitial dismissed; keeping load on-demand placement=%s route=%s",
                        placement.analyticsValue,
                        contextRoute,
                    )
                    onAdDismissed()
                },
            )
        }

        private fun startAdGateObserver(context: Context) {
            if (adGateObserverStarted) return
            adGateObserverStarted = true
            Timber.d("AdOrchestrator startAdGateObserver")
            orchestratorScope.launch {
                combine(
                    AdsConsentRuntimeState.canRequestAds,
                    adGateChecker.shouldShowAds,
                ) { consentGranted, adsAllowedByGate ->
                    consentGranted && adsAllowedByGate
                }.distinctUntilChanged()
                    .collect { canPreload ->
                        Timber.d("Ad gate observer changed canPreload=%s", canPreload)
                        if (canPreload) {
                            requestAdBootstrap(
                                context = context,
                                reason = PreloadReason.GATE_OPENED,
                                includeAppOpenWarmup = true,
                            )
                        } else {
                            clearPreloadedAds()
                        }
                    }
            }
        }

        fun refreshConsent(
            activity: Activity,
            scope: CoroutineScope,
            onUpdated: (Boolean) -> Unit = {},
        ) {
            Timber.d("AdOrchestrator refreshConsent requested")
            adManager.refreshConsent(activity) { canRequestAds ->
                scope.launch(Main.immediate) {
                    Timber.d("AdOrchestrator refreshConsent result canRequestAds=%s", canRequestAds)
                    if (!canRequestAds) {
                        clearPreloadedAds()
                        onUpdated(false)
                        return@launch
                    }
                    if (!adGateChecker.shouldShowAds.first()) {
                        Timber.d("AdOrchestrator refreshConsent: ad gate closed, clear preloads")
                        clearPreloadedAds()
                        onUpdated(false)
                        return@launch
                    }
                    requestAdBootstrap(
                        context = activity.applicationContext,
                        reason = PreloadReason.REFRESH_CONSENT,
                        includeAppOpenWarmup = true,
                    )
                    onUpdated(true)
                }
            }
        }

        fun requestRewardedLoad(
            context: Context,
            placement: AdPlacement = AdPlacement.REWARDED_REWARDS_SCREEN,
            route: String? = adSessionContext.activeRoute,
        ) {
            if (!AdsConsentRuntimeState.canRequestAds.value) {
                Timber.d("Rewarded load skipped: consent unavailable placement=%s", placement.analyticsValue)
                return
            }
            rewardedAdManager.loadAd(
                AppAdUnitIds.resolvePlacement(context, placement, BuildConfig.USE_TEST_ADS),
                placement,
                route,
            )
        }

        private fun preloadAds(
            context: Context,
            includeAppOpenWarmup: Boolean,
            reason: PreloadReason,
        ) {
            val policy = adsPolicyProvider.getPolicy()
            Timber.d(
                "Preload ads route=%s nativePoolMax=%d warmAppOpen=%s reason=%s",
                adSessionContext.activeRoute,
                policy.nativePoolMax,
                includeAppOpenWarmup,
                reason.analyticsValue,
            )
            interstitialAdManager.loadAd(
                context,
                AppAdUnitIds.resolvePlacement(
                    context,
                    AdPlacement.INTERSTITIAL_NAV_BREAK,
                    BuildConfig.USE_TEST_ADS,
                ),
                AdPlacement.INTERSTITIAL_NAV_BREAK,
                adSessionContext.activeRoute,
                loadReason = reason.analyticsValue,
            )
            nativeAdManager.loadAds(
                context,
                AppAdUnitIds.resolvePlacement(
                    context,
                    AdPlacement.NATIVE_FEED_HOME,
                    BuildConfig.USE_TEST_ADS,
                ),
                AdPlacement.NATIVE_FEED_HOME,
                policy.nativePoolMax,
            )
            if (includeAppOpenWarmup && !appOpenWarmupRequested) {
                appOpenWarmupRequested = true
                Timber.d("AppOpen warm preload requested during bootstrap")
                appOpenAdManager.loadAd(
                    AppAdUnitIds.resolvePlacement(
                        context,
                        AdPlacement.APP_OPEN_RESUME,
                        BuildConfig.USE_TEST_ADS,
                    ),
                    AdPlacement.APP_OPEN_RESUME,
                    loadReason = reason.analyticsValue,
                )
            } else {
                Timber.d(
                    "AppOpen warm preload skipped include=%s alreadyRequested=%s",
                    includeAppOpenWarmup,
                    appOpenWarmupRequested,
                )
            }
        }

        private fun clearPreloadedAds() {
            Timber.d("Clear preloaded ads")
            appOpenWarmupRequested = false
            appOpenAdManager.clearAd()
            interstitialAdManager.clearAd()
            rewardedAdManager.clearAd()
            rewardedInterstitialAdManager.clearAd()
            nativeAdManager.destroyAds()
        }

        private fun requestAdBootstrap(
            context: Context,
            reason: PreloadReason,
            includeAppOpenWarmup: Boolean,
        ) {
            Timber.d(
                "Ad bootstrap requested reason=%s includeAppOpenWarmup=%s route=%s",
                reason.analyticsValue,
                includeAppOpenWarmup,
                adSessionContext.activeRoute,
            )
            preloadAds(context, includeAppOpenWarmup, reason)
        }

        private fun maybeRequestOpportunisticPreload(route: String) {
            val context = applicationContextRef ?: return
            if (!AdsConsentRuntimeState.canRequestAds.value) return
            val policy = adsPolicyProvider.getPolicy()
            if (!policy.isHotInterstitialRoute(route)) return

            val interstitialPlacement = AdPlacement.INTERSTITIAL_NAV_BREAK
            if (policy.shouldUseAggressiveInterstitialPreload(context.packageName) &&
                policy.isInterstitialPlacementEnabled(interstitialPlacement) &&
                !policy.isBlockedContext(route)
            ) {
                interstitialAdManager.loadAd(
                    context,
                    AppAdUnitIds.resolvePlacement(
                        context,
                        interstitialPlacement,
                        BuildConfig.USE_TEST_ADS,
                    ),
                    interstitialPlacement,
                    route = route,
                    loadReason = PreloadReason.ROUTE_HINT.analyticsValue,
                )
            }

            val appOpenPlacement = AdPlacement.APP_OPEN_RESUME
            if (policy.shouldUseAggressiveAppOpenPreload(context.packageName) &&
                policy.isAppOpenPlacementEnabled(appOpenPlacement) &&
                !policy.isBlockedContext(route)
            ) {
                appOpenAdManager.loadAd(
                    AppAdUnitIds.resolvePlacement(
                        context,
                        appOpenPlacement,
                        BuildConfig.USE_TEST_ADS,
                    ),
                    appOpenPlacement,
                    loadReason = PreloadReason.ROUTE_HINT.analyticsValue,
                )
            }
        }

        private fun logAdAfterEngagement(
            adFormat: AdFormat,
            placement: AdPlacement,
            adUnitId: String,
            route: String?,
        ) {
            val context = adSessionContext
            val now = System.currentTimeMillis()
            val sessionDurationSeconds = (
                (now - context.sessionStartedAtMs).coerceAtLeast(0L) /
                    1000L
            )
            adRevenueLogger.logAdAfterEngagement(
                adFormat = adFormat,
                placement = placement,
                adUnitId = adUnitId,
                route = route ?: context.activeRoute,
                sessionDurationSeconds = sessionDurationSeconds,
                verseCountBeforeAd = context.verseReadCount,
                sessionAudioPlayed = context.audioPlayedThisSession,
                sessionContentType = context.contentType,
            )
        }

        private fun isContentInProgress(
            route: String?,
            triggerKind: InterstitialTriggerKind? = null,
        ): Boolean {
            if (triggerKind == InterstitialTriggerKind.AUDIO_PAUSE ||
                triggerKind == InterstitialTriggerKind.AUDIO_STOP
            ) {
                return false
            }
            val normalizedRoute = route?.lowercase().orEmpty()
            return adSessionContext.audioPlayedThisSession &&
                (
                    normalizedRoute.startsWith("content") ||
                        normalizedRoute.startsWith("quran_sura_detail") ||
                        normalizedRoute.startsWith("prayer_detail")
                )
        }

        private fun shouldCollapseInterstitialOpportunity(
            triggerKind: InterstitialTriggerKind,
            route: String?,
        ): Boolean {
            val now = SystemTimeProvider.nowMillis()
            val resolvedRoute = route ?: adSessionContext.activeRoute ?: "unknown"
            val key = "${triggerKind.analyticsValue}::$resolvedRoute"
            val shouldCollapse =
                lastInterstitialIntentKey == key && (now - lastInterstitialIntentAtMs) < 1_500L
            lastInterstitialIntentKey = key
            lastInterstitialIntentAtMs = now
            return shouldCollapse
        }
    }
