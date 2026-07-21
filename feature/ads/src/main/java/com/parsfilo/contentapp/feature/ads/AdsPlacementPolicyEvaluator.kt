package com.parsfilo.contentapp.feature.ads

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class AdRequestContext(
    val format: AdFormat,
    val placement: AdPlacement,
    val route: String?,
    val screenRoute: String? = route,
    val privacyState: AdsPrivacyState,
    val isPremium: Boolean,
    val isRewardedAdFree: Boolean,
    val sessionCount: Int,
    val lastShownAtMs: Long?,
    val resumeGapMs: Long?,
    val isColdStart: Boolean = false,
    val contentInProgress: Boolean,
    val appOpenTriggerReason: AppOpenTriggerReason? = null,
    val interstitialTriggerKind: InterstitialTriggerKind? = null,
)

sealed interface AdEligibility {
    data object Allowed : AdEligibility

    data class Blocked(val reason: AdSuppressReason) : AdEligibility
}

@Singleton
class AdsPlacementPolicyEvaluator @Inject constructor(
    private val adsPolicyProvider: AdsPolicyProvider,
) {
    private fun effectiveCooldownMs(value: Long): Long = if (BuildConfig.DEBUG) 0L else value

    fun evaluateInterstitial(context: AdRequestContext): AdEligibility {
        val policy = adsPolicyProvider.getPolicy()
        if (!policy.isInterstitialPlacementEnabled(context.placement)) {
            return blocked("interstitial", AdSuppressReason.PLACEMENT_DISABLED, context)
        }
        if (context.privacyState !is AdsPrivacyState.CanRequestAds) {
            return blocked("interstitial", context.privacyState.suppressReasonWhenBlocked(), context)
        }
        if (context.isPremium) return blocked("interstitial", AdSuppressReason.PREMIUM, context)
        if (context.isRewardedAdFree) return blocked("interstitial", AdSuppressReason.REWARDED_FREE, context)
        if (context.contentInProgress) return blocked("interstitial", AdSuppressReason.CONTENT_IN_PROGRESS, context)
        if (matchesContext(policy.interstitialRouteBlocklist, context.screenRoute, context.route, context.interstitialTriggerKind?.analyticsValue)) {
            return blocked("interstitial", AdSuppressReason.ROUTE_BLOCKED, context)
        }
        val lastShownAtMs = context.lastShownAtMs
        if (lastShownAtMs != null) {
            val cooldownMs = effectiveCooldownMs(policy.interstitialFrequencyCapMs)
            if (SystemTimeProvider.nowMillis() - lastShownAtMs < cooldownMs) {
                return blocked("interstitial", AdSuppressReason.COOLDOWN, context)
            }
        }
        if (context.sessionCount >= policy.interstitialMaxPerSession) {
            return blocked("interstitial", AdSuppressReason.SESSION_CAP, context)
        }
        return allowed("interstitial", context)
    }

    fun evaluateAppOpen(context: AdRequestContext): AdEligibility {
        val policy = adsPolicyProvider.getPolicy()
        if (!policy.isAppOpenPlacementEnabled(context.placement)) {
            return blocked("app_open", AdSuppressReason.PLACEMENT_DISABLED, context)
        }
        if (context.privacyState !is AdsPrivacyState.CanRequestAds) {
            return blocked("app_open", context.privacyState.suppressReasonWhenBlocked(), context)
        }
        if (context.isPremium) return blocked("app_open", AdSuppressReason.PREMIUM, context)
        if (context.isRewardedAdFree) return blocked("app_open", AdSuppressReason.REWARDED_FREE, context)
        if (context.contentInProgress) return blocked("app_open", AdSuppressReason.CONTENT_IN_PROGRESS, context)
        if (matchesContext(policy.appOpenRouteBlocklist, context.screenRoute, context.route, context.appOpenTriggerReason?.analyticsValue)) {
            return blocked("app_open", AdSuppressReason.ROUTE_BLOCKED, context)
        }
        if (context.isColdStart) {
            return blocked("app_open", AdSuppressReason.COLD_START, context)
        }
        val resumeGapMs = effectiveCooldownMs(policy.appOpenResumeGapMs)
        if ((context.resumeGapMs ?: Long.MAX_VALUE) < resumeGapMs) {
            return blocked("app_open", AdSuppressReason.RESUME_SPAM, context)
        }
        val lastShownAtMs = context.lastShownAtMs
        val appOpenCooldownMs = effectiveCooldownMs(policy.appOpenCooldownMs)
        if (lastShownAtMs != null && SystemTimeProvider.nowMillis() - lastShownAtMs < appOpenCooldownMs) {
            return blocked("app_open", AdSuppressReason.COOLDOWN, context)
        }
        val appOpenMaxPerSession = if (BuildConfig.DEBUG) Int.MAX_VALUE else policy.appOpenMaxPerSession
        if (context.sessionCount >= appOpenMaxPerSession) {
            return blocked("app_open", AdSuppressReason.SESSION_CAP, context)
        }
        return allowed("app_open", context)
    }

    fun evaluateRewarded(context: AdRequestContext): AdEligibility {
        val policy = adsPolicyProvider.getPolicy()
        if (!policy.isRewardedPlacementEnabled(context.placement)) {
            return blocked("rewarded", AdSuppressReason.PLACEMENT_DISABLED, context)
        }
        if (context.privacyState !is AdsPrivacyState.CanRequestAds) {
            return blocked("rewarded", context.privacyState.suppressReasonWhenBlocked(), context)
        }
        if (context.isPremium) return blocked("rewarded", AdSuppressReason.PREMIUM, context)
        if (context.isRewardedAdFree) return blocked("rewarded", AdSuppressReason.REWARDED_FREE, context)
        if (context.sessionCount >= policy.rewardedMaxPerSession) {
            return blocked("rewarded", AdSuppressReason.SESSION_CAP, context)
        }
        return allowed("rewarded", context)
    }

    fun evaluateRewardedInterstitial(context: AdRequestContext): AdEligibility {
        val policy = adsPolicyProvider.getPolicy()
        if (!policy.isRewardedInterstitialPlacementEnabled(context.placement)) {
            return blocked("rewarded_interstitial", AdSuppressReason.PLACEMENT_DISABLED, context)
        }
        if (context.privacyState !is AdsPrivacyState.CanRequestAds) {
            return blocked(
                "rewarded_interstitial",
                context.privacyState.suppressReasonWhenBlocked(),
                context,
            )
        }
        if (context.isPremium) return blocked("rewarded_interstitial", AdSuppressReason.PREMIUM, context)
        if (context.isRewardedAdFree) return blocked("rewarded_interstitial", AdSuppressReason.REWARDED_FREE, context)
        if (context.contentInProgress) return blocked("rewarded_interstitial", AdSuppressReason.CONTENT_IN_PROGRESS, context)
        val lastShownAtMs = context.lastShownAtMs
        val minIntervalMs = effectiveCooldownMs(policy.rewardedInterstitialMinIntervalMs)
        if (lastShownAtMs != null && SystemTimeProvider.nowMillis() - lastShownAtMs < minIntervalMs) {
            return blocked("rewarded_interstitial", AdSuppressReason.COOLDOWN, context)
        }
        if (context.sessionCount >= policy.rewardedInterstitialMaxPerSession) {
            return blocked("rewarded_interstitial", AdSuppressReason.SESSION_CAP, context)
        }
        return allowed("rewarded_interstitial", context)
    }

    private fun blocked(
        gate: String,
        reason: AdSuppressReason,
        context: AdRequestContext,
    ): AdEligibility.Blocked {
        Timber.d(
            "Ad eligibility blocked gate=%s format=%s placement=%s route=%s reason=%s sessionCount=%d contentInProgress=%s premium=%s rewardedFree=%s",
            gate,
            context.format.analyticsValue,
            context.placement.analyticsValue,
            context.route,
            reason.analyticsValue,
            context.sessionCount,
            context.contentInProgress,
            context.isPremium,
            context.isRewardedAdFree,
        )
        return AdEligibility.Blocked(reason)
    }

    private fun allowed(gate: String, context: AdRequestContext): AdEligibility.Allowed {
        Timber.d(
            "Ad eligibility allowed gate=%s format=%s placement=%s route=%s sessionCount=%d",
            gate,
            context.format.analyticsValue,
            context.placement.analyticsValue,
            context.route,
            context.sessionCount,
        )
        return AdEligibility.Allowed
    }

    private fun matchesContext(
        blocklist: Set<String>,
        vararg values: String?,
    ): Boolean =
        values
            .asSequence()
            .filterNotNull()
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .any { candidate ->
                blocklist.any { blocked ->
                    candidate == blocked || candidate.startsWith("$blocked/")
                }
            }
}
