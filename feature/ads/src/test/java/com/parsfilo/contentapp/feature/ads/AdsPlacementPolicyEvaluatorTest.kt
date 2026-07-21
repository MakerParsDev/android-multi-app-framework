package com.parsfilo.contentapp.feature.ads

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class AdsPlacementPolicyEvaluatorTest {
    private val adsPolicyProvider = mockk<AdsPolicyProvider>()
    private lateinit var evaluator: AdsPlacementPolicyEvaluator

    @Before
    fun setUp() {
        every { adsPolicyProvider.getPolicy() } returns defaultPolicy()
        evaluator = AdsPlacementPolicyEvaluator(adsPolicyProvider)
    }

    @Test
    fun `interstitial blocks when consent is unavailable`() {
        val result = evaluator.evaluateInterstitial(
            baseContext(
                format = AdFormat.INTERSTITIAL,
                placement = AdPlacement.INTERSTITIAL_NAV_BREAK,
                privacyState = AdsPrivacyState.Gathering(AdAgeGateStatus.AGE_16_OR_OVER),
            ),
        )

        assertThat(result).isEqualTo(AdEligibility.Blocked(AdSuppressReason.NO_CONSENT))
    }

    @Test
    fun `interstitial blocks missing consent separately`() {
        val result = evaluator.evaluateInterstitial(
            baseContext(
                format = AdFormat.INTERSTITIAL,
                placement = AdPlacement.INTERSTITIAL_NAV_BREAK,
                privacyState = AdsPrivacyState.DeniedOrLimited(
                    consentStatus = ConsentStatus.Missing,
                    privacyOptionsRequired = false,
                    ageGateStatus = AdAgeGateStatus.AGE_16_OR_OVER,
                ),
            ),
        )

        assertThat(result).isEqualTo(AdEligibility.Blocked(AdSuppressReason.CONSENT_MISSING))
    }

    @Test
    fun `interstitial blocks consent retry backoff separately`() {
        val result = evaluator.evaluateInterstitial(
            baseContext(
                format = AdFormat.INTERSTITIAL,
                placement = AdPlacement.INTERSTITIAL_NAV_BREAK,
                privacyState = AdsPrivacyState.DeniedOrLimited(
                    consentStatus = ConsentStatus.Error(
                        message = "timeout",
                        retryEligibleAtMillis = Long.MAX_VALUE,
                    ),
                    privacyOptionsRequired = false,
                    ageGateStatus = AdAgeGateStatus.AGE_16_OR_OVER,
                ),
            ),
        )

        assertThat(result).isEqualTo(AdEligibility.Blocked(AdSuppressReason.CONSENT_RETRY_BACKOFF))
    }

    @Test
    fun `interstitial blocks premium users`() {
        val result = evaluator.evaluateInterstitial(
            baseContext(
                format = AdFormat.INTERSTITIAL,
                placement = AdPlacement.INTERSTITIAL_NAV_BREAK,
                isPremium = true,
            ),
        )

        assertThat(result).isEqualTo(AdEligibility.Blocked(AdSuppressReason.PREMIUM))
    }

    @Test
    fun `interstitial blocks route blacklist`() {
        every { adsPolicyProvider.getPolicy() } returns defaultPolicy(
            interstitialRouteBlocklist = setOf(InterstitialTriggerKind.MODE_SWITCH.analyticsValue),
        )

        val result = evaluator.evaluateInterstitial(
            baseContext(
                format = AdFormat.INTERSTITIAL,
                placement = AdPlacement.INTERSTITIAL_NAV_BREAK,
                route = "content/2",
                interstitialTriggerKind = InterstitialTriggerKind.MODE_SWITCH,
            ),
        )

        assertThat(result).isEqualTo(AdEligibility.Blocked(AdSuppressReason.ROUTE_BLOCKED))
    }

    @Test
    fun `app open blocks short resume spam`() {
        val result = evaluator.evaluateAppOpen(
            baseContext(
                format = AdFormat.APP_OPEN,
                placement = AdPlacement.APP_OPEN_RESUME,
                resumeGapMs = 5_000L,
            ),
        )

        if (BuildConfig.DEBUG) {
            assertThat(result).isEqualTo(AdEligibility.Allowed)
        } else {
            assertThat(result).isEqualTo(AdEligibility.Blocked(AdSuppressReason.RESUME_SPAM))
        }
    }

    @Test
    fun `app open blocks cold start`() {
        val result = evaluator.evaluateAppOpen(
            baseContext(
                format = AdFormat.APP_OPEN,
                placement = AdPlacement.APP_OPEN_RESUME,
                isColdStart = true,
                resumeGapMs = 120_000L,
            ),
        )

        assertThat(result).isEqualTo(AdEligibility.Blocked(AdSuppressReason.COLD_START))
    }

    @Test
    fun `rewarded interstitial blocks session cap`() {
        val result = evaluator.evaluateRewardedInterstitial(
            baseContext(
                format = AdFormat.REWARDED_INTERSTITIAL,
                placement = AdPlacement.REWARDED_INTERSTITIAL_HISTORY_UNLOCK,
                sessionCount = 1,
            ),
        )

        assertThat(result).isEqualTo(AdEligibility.Blocked(AdSuppressReason.SESSION_CAP))
    }

    @Test
    fun `app open allows safe eligible request`() {
        val result = evaluator.evaluateAppOpen(
            baseContext(
                format = AdFormat.APP_OPEN,
                placement = AdPlacement.APP_OPEN_RESUME,
                resumeGapMs = 120_000L,
            ),
        )

        assertThat(result).isEqualTo(AdEligibility.Allowed)
    }

    private fun baseContext(
        format: AdFormat,
        placement: AdPlacement,
        route: String? = null,
        screenRoute: String? = route,
        privacyState: AdsPrivacyState = AdsPrivacyState.CanRequestAds(
            consentStatus = ConsentStatus.Obtained,
            privacyOptionsRequired = false,
            ageGateStatus = AdAgeGateStatus.AGE_16_OR_OVER,
        ),
        isPremium: Boolean = false,
        isRewardedAdFree: Boolean = false,
        sessionCount: Int = 0,
        lastShownAtMs: Long? = null,
        resumeGapMs: Long? = null,
        isColdStart: Boolean = false,
        contentInProgress: Boolean = false,
        appOpenTriggerReason: AppOpenTriggerReason? = null,
        interstitialTriggerKind: InterstitialTriggerKind? = null,
    ): AdRequestContext = AdRequestContext(
        format = format,
        placement = placement,
        route = route,
        screenRoute = screenRoute,
        privacyState = privacyState,
        isPremium = isPremium,
        isRewardedAdFree = isRewardedAdFree,
        sessionCount = sessionCount,
        lastShownAtMs = lastShownAtMs,
        resumeGapMs = resumeGapMs,
        isColdStart = isColdStart,
        contentInProgress = contentInProgress,
        appOpenTriggerReason = appOpenTriggerReason,
        interstitialTriggerKind = interstitialTriggerKind,
    )

    private fun defaultPolicy(
        interstitialRouteBlocklist: Set<String> = emptySet(),
    ): AdsPolicyConfig = AdsPolicyConfig(
        interstitialFrequencyCapMs = 120_000L,
        interstitialRelaxedFrequencyCapMs = 300_000L,
        interstitialRelaxedPackages = emptySet(),
        appOpenCooldownMs = 300_000L,
        appOpenResumeGapMs = 45_000L,
        appOpenMaxPerSession = 1,
        interstitialMaxPerSession = 2,
        rewardedMaxPerSession = 2,
        rewardedInterstitialMinIntervalMs = 300_000L,
        rewardedInterstitialMaxPerSession = 1,
        rewardedInterstitialIntroRequired = true,
        appOpenEnabled = true,
        interstitialEnabled = true,
        bannerEnabled = true,
        nativeEnabled = true,
        rewardedEnabled = true,
        rewardedInterstitialEnabled = true,
        appOpenPlacementsDisabled = emptySet(),
        interstitialPlacementsDisabled = emptySet(),
        bannerPlacementsDisabled = emptySet(),
        nativePlacementsDisabled = emptySet(),
        rewardedPlacementsDisabled = emptySet(),
        rewardedInterstitialPlacementsDisabled = emptySet(),
        appOpenRouteBlocklist = emptySet(),
        interstitialRouteBlocklist = interstitialRouteBlocklist,
        nativePoolMax = 1,
        nativeTtlMs = 30_000L,
        nativeExactPlacementOnly = false,
    )
}
