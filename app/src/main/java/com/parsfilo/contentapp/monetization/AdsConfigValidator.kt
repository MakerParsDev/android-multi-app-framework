package com.parsfilo.contentapp.monetization

import android.content.Context
import com.parsfilo.contentapp.BuildConfig
import com.parsfilo.contentapp.feature.ads.AdPlacement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdsConfigValidator
    @Inject
    constructor() {
        fun validateOrThrow(
            context: Context,
            useTestAds: Boolean,
        ) {
            val ids = AppAdUnitIds.resolve(context, useTestAds)
            validateResolvedIds(
                ids = ids,
                isDebugBuild = BuildConfig.DEBUG,
                useTestAds = useTestAds,
            )

            AppAdUnitIds.resolvePlacement(context, AdPlacement.APP_OPEN_RESUME, useTestAds)
            AppAdUnitIds.resolvePlacement(context, AdPlacement.INTERSTITIAL_NAV_BREAK, useTestAds)
            AppAdUnitIds.resolvePlacement(context, AdPlacement.NATIVE_FEED_HOME, useTestAds)
            AppAdUnitIds.resolvePlacement(
                context,
                AdPlacement.REWARDED_INTERSTITIAL_HISTORY_UNLOCK,
                useTestAds,
            )
        }

        internal fun validateResolvedIds(
            ids: AppAdUnitIds.Ids,
            isDebugBuild: Boolean,
            useTestAds: Boolean,
        ) {
            require(ids.banner.isNotBlank()) { "Missing banner ad unit id" }
            require(ids.interstitial.isNotBlank()) { "Missing interstitial ad unit id" }
            require(ids.native.isNotBlank()) { "Missing native ad unit id" }
            require(ids.rewarded.isNotBlank()) { "Missing rewarded ad unit id" }
            require(
                ids.rewardedInterstitial.isNotBlank(),
            ) { "Missing rewarded interstitial ad unit id" }
            require(ids.appOpen.isNotBlank()) { "Missing app open ad unit id" }

            val usesGoogleTestIds = AppAdUnitIds.run { ids.usesGoogleTestIds() }
            check(!(isDebugBuild && !useTestAds && !usesGoogleTestIds)) {
                "Debug build must not resolve to production ad unit ids"
            }
            check(!(useTestAds && !usesGoogleTestIds)) {
                "Test-ads build resolved to non-test ad unit ids"
            }
            check(!(!useTestAds && usesGoogleTestIds)) {
                "Release/configured production build resolved to Google test ids"
            }
        }
    }
