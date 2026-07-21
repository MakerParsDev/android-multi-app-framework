package com.parsfilo.contentapp.feature.ads

import android.os.Bundle
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo
import com.parsfilo.contentapp.core.datastore.AdRuntimeTelemetryEvent
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.firebase.AnalyticsEventName
import com.parsfilo.contentapp.core.firebase.AnalyticsParamKey
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class AdResponseMeta(
    val responseId: String?,
    val mediationAdapterClassName: String?,
    val loadedAdapterName: String?,
    val networkName: String?,
)

data class AdPaidEventContext(
    val adUnitId: String,
    val adFormat: AdFormat,
    val placement: AdPlacement,
    val route: String?,
    val adValue: AdValue,
    val responseMeta: AdResponseMeta,
)

data class AdShowDiagnosticContext(
    val isLoading: Boolean? = null,
    val currentAdUnitId: String? = null,
    val timeSinceLastLoadStartMs: Long? = null,
    val backoffNextAllowedAtMs: Long? = null,
)

@Singleton
class AdRevenueLogger @Inject constructor(
    private val appAnalytics: AppAnalytics,
    private val preferencesDataSource: PreferencesDataSource,
) {
    private val telemetryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun logShowIntent(
        adFormat: AdFormat,
        placement: AdPlacement,
        route: String? = null,
        trigger: String? = null,
        adReady: Boolean? = null,
    ) {
        Timber.tag("timber_log").d(
            "show_intent format=%s placement=%s route=%s trigger=%s adReady=%s canRequestAds=%s",
            adFormat.analyticsValue,
            placement.analyticsValue,
            route ?: "unknown",
            trigger ?: "unknown",
            adReady?.toString() ?: "n/a",
            AdsConsentRuntimeState.canRequestAds.value,
        )
        appAnalytics.logEvent(
            AnalyticsEventName.AD_SHOW_INTENT,
            adEventBundle(
                adFormat = adFormat,
                placement = placement,
                adUnitId = "unknown",
                route = route,
            ).apply {
                if (!trigger.isNullOrBlank()) putString(AnalyticsParamKey.SHOW_TRIGGER, trigger)
                adReady?.let { putLong(AnalyticsParamKey.AD_READY, if (it) 1L else 0L) }
            },
        )
        recordRuntimeTelemetry(adFormat, placement, "show_intent")
    }

    fun logShowBlocked(
        adFormat: AdFormat,
        placement: AdPlacement,
        suppressReason: AdSuppressReason,
        route: String? = null,
        trigger: String? = null,
        diagnostics: AdShowDiagnosticContext = AdShowDiagnosticContext(),
    ) {
        Timber.tag("timber_log").d(
            "show_blocked format=%s placement=%s route=%s trigger=%s reason=%s isLoading=%s adUnit=%s timeSinceLoadMs=%s nextBackoffMs=%s",
            adFormat.analyticsValue,
            placement.analyticsValue,
            route ?: "unknown",
            trigger ?: "unknown",
            suppressReason.analyticsValue,
            diagnostics.isLoading?.toString() ?: "n/a",
            diagnostics.currentAdUnitId ?: "unknown",
            diagnostics.timeSinceLastLoadStartMs?.toString() ?: "n/a",
            diagnostics.backoffNextAllowedAtMs?.toString() ?: "n/a",
        )
        appAnalytics.logEvent(
            AnalyticsEventName.AD_SHOW_BLOCKED,
            adEventBundle(
                adFormat = adFormat,
                placement = placement,
                adUnitId = diagnostics.currentAdUnitId ?: "unknown",
                route = route,
            ).apply {
                putString(AnalyticsParamKey.SUPPRESS_REASON, suppressReason.analyticsValue)
                if (!trigger.isNullOrBlank()) putString(AnalyticsParamKey.SHOW_TRIGGER, trigger)
                diagnostics.isLoading?.let { putLong(AnalyticsParamKey.IS_LOADING, if (it) 1L else 0L) }
                diagnostics.timeSinceLastLoadStartMs?.let {
                    putLong(AnalyticsParamKey.TIME_SINCE_LOAD_MS, it)
                }
                diagnostics.backoffNextAllowedAtMs?.let {
                    putLong(AnalyticsParamKey.BACKOFF_NEXT_MS, it)
                }
            },
        )
        recordRuntimeTelemetry(adFormat, placement, "show_blocked", suppressReason.analyticsValue)
    }

    fun logShowNotLoaded(
        adFormat: AdFormat,
        placement: AdPlacement,
        route: String? = null,
        trigger: String? = null,
        diagnostics: AdShowDiagnosticContext = AdShowDiagnosticContext(),
    ) {
        Timber.tag("timber_log").d(
            "show_not_loaded format=%s placement=%s route=%s trigger=%s isLoading=%s adUnit=%s timeSinceLoadMs=%s nextBackoffMs=%s",
            adFormat.analyticsValue,
            placement.analyticsValue,
            route ?: "unknown",
            trigger ?: "unknown",
            diagnostics.isLoading?.toString() ?: "n/a",
            diagnostics.currentAdUnitId ?: "unknown",
            diagnostics.timeSinceLastLoadStartMs?.toString() ?: "n/a",
            diagnostics.backoffNextAllowedAtMs?.toString() ?: "n/a",
        )
        appAnalytics.logEvent(
            AnalyticsEventName.AD_SHOW_NOT_LOADED,
            adEventBundle(
                adFormat = adFormat,
                placement = placement,
                adUnitId = diagnostics.currentAdUnitId ?: "unknown",
                route = route,
            ).apply {
                if (!trigger.isNullOrBlank()) putString(AnalyticsParamKey.SHOW_TRIGGER, trigger)
                diagnostics.isLoading?.let { putLong(AnalyticsParamKey.IS_LOADING, if (it) 1L else 0L) }
                diagnostics.timeSinceLastLoadStartMs?.let {
                    putLong(AnalyticsParamKey.TIME_SINCE_LOAD_MS, it)
                }
                diagnostics.backoffNextAllowedAtMs?.let {
                    putLong(AnalyticsParamKey.BACKOFF_NEXT_MS, it)
                }
            },
        )
        recordRuntimeTelemetry(adFormat, placement, "show_not_loaded", AdSuppressReason.NOT_LOADED.analyticsValue)
    }

    fun logPreloadRequested(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        route: String? = null,
        reason: String,
    ) {
        Timber.tag("timber_log").d(
            "preload_requested format=%s placement=%s route=%s reason=%s adUnit=%s",
            adFormat.analyticsValue,
            placement.analyticsValue,
            route ?: "unknown",
            reason,
            adUnitId,
        )
        appAnalytics.logEvent(
            AnalyticsEventName.AD_PRELOAD_REQUESTED,
            adEventBundle(adFormat, placement, adUnitId, route).apply {
                putString(AnalyticsParamKey.PRELOAD_REASON, reason)
            },
        )
    }

    fun logShowStarted(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        route: String? = null,
        trigger: String? = null,
    ) {
        Timber.tag("timber_log").d(
            "show_started format=%s placement=%s route=%s trigger=%s adUnit=%s",
            adFormat.analyticsValue,
            placement.analyticsValue,
            route ?: "unknown",
            trigger ?: "unknown",
            adUnitId,
        )
        recordRuntimeTelemetry(adFormat, placement, "show_started")
    }

    fun logShowImpression(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        route: String? = null,
        trigger: String? = null,
    ) {
        Timber.tag("timber_log").d(
            "show_impression format=%s placement=%s route=%s trigger=%s adUnit=%s",
            adFormat.analyticsValue,
            placement.analyticsValue,
            route ?: "unknown",
            trigger ?: "unknown",
            adUnitId,
        )
        recordRuntimeTelemetry(adFormat, placement, "show_impression")
    }

    fun logShowDismissed(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        route: String? = null,
        trigger: String? = null,
    ) {
        Timber.tag("timber_log").d(
            "show_dismissed format=%s placement=%s route=%s trigger=%s adUnit=%s",
            adFormat.analyticsValue,
            placement.analyticsValue,
            route ?: "unknown",
            trigger ?: "unknown",
            adUnitId,
        )
        recordRuntimeTelemetry(adFormat, placement, "show_dismissed")
    }

    fun logShowFailed(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        route: String? = null,
        trigger: String? = null,
        errorCode: Int? = null,
        errorMessage: String? = null,
    ) {
        Timber.tag("timber_log").d(
            "show_failed format=%s placement=%s route=%s trigger=%s adUnit=%s code=%s msg=%s",
            adFormat.analyticsValue,
            placement.analyticsValue,
            route ?: "unknown",
            trigger ?: "unknown",
            adUnitId,
            errorCode?.toString() ?: "n/a",
            errorMessage ?: "n/a",
        )
        recordRuntimeTelemetry(adFormat, placement, "show_failed")
    }

    fun logRequest(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        route: String? = null,
    ) {
        ConsentFunnelDebugDashboard.onAdRequestSent(
            adFormat = adFormat,
            placement = placement,
            canRequestAds = AdsConsentRuntimeState.canRequestAds.value,
        )
        appAnalytics.logEvent(
            AnalyticsEventName.AD_REQUEST_SENT,
            adEventBundle(adFormat, placement, adUnitId, route),
        )
        recordRuntimeTelemetry(adFormat, placement, "request_sent")
    }

    fun logLoaded(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        route: String? = null,
        fillLatencyMs: Long? = null,
        adapterName: String? = null,
    ) {
        appAnalytics.logEvent(
            AnalyticsEventName.AD_LOADED,
            adEventBundle(adFormat, placement, adUnitId, route).apply {
                if (fillLatencyMs != null) putLong(AnalyticsParamKey.FILL_LATENCY_MS, fillLatencyMs)
                if (!adapterName.isNullOrBlank()) putString(AnalyticsParamKey.ADAPTER_NAME, adapterName)
            },
        )
        recordRuntimeTelemetry(adFormat, placement, "loaded")
    }

    fun logFailedToLoad(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        errorCode: Int,
        errorMessage: String?,
        route: String? = null,
        backoffAttempt: Int? = null,
    ) {
        appAnalytics.logEvent(
            AnalyticsEventName.AD_FAILED_TO_LOAD,
            adEventBundle(adFormat, placement, adUnitId, route).apply {
                putLong(AnalyticsParamKey.ERROR_CODE, errorCode.toLong())
                if (!errorMessage.isNullOrBlank()) putString(AnalyticsParamKey.ERROR_MESSAGE, errorMessage)
                if (backoffAttempt != null) putLong(AnalyticsParamKey.BACKOFF_ATTEMPT, backoffAttempt.toLong())
            },
        )
        recordRuntimeTelemetry(
            adFormat = adFormat,
            placement = placement,
            event = "load_failed",
            suppressReason = AdSuppressReason.LOAD_FAILURE.analyticsValue,
        )
    }

    fun logSuppressed(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        suppressReason: String,
        route: String? = null,
    ) {
        appAnalytics.logEvent(
            AnalyticsEventName.AD_SUPPRESSED,
            adEventBundle(adFormat, placement, adUnitId, route).apply {
                putString(AnalyticsParamKey.SUPPRESS_REASON, suppressReason)
            },
        )
    }

    fun logSuppressed(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        suppressReason: AdSuppressReason,
        route: String? = null,
    ) = logSuppressed(
        adFormat = adFormat,
        placement = placement,
        adUnitId = adUnitId,
        suppressReason = suppressReason.analyticsValue,
        route = route,
    )

    fun logFailedToShow(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        errorCode: Int? = null,
        errorMessage: String? = null,
        route: String? = null,
    ) {
        appAnalytics.logEvent(
            AnalyticsEventName.AD_FAILED_TO_SHOW,
            adEventBundle(adFormat, placement, adUnitId, route).apply {
                if (errorCode != null) putLong(AnalyticsParamKey.ERROR_CODE, errorCode.toLong())
                if (!errorMessage.isNullOrBlank()) putString(AnalyticsParamKey.ERROR_MESSAGE, errorMessage)
            },
        )
    }

    fun logDismissed(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        route: String? = null,
    ) {
        appAnalytics.logEvent(
            AnalyticsEventName.AD_DISMISSED,
            adEventBundle(adFormat, placement, adUnitId, route),
        )
    }

    fun logRewardedInterstitialIntroShown(
        placement: AdPlacement,
        adUnitId: String,
        route: String?,
    ) {
        appAnalytics.logEvent(
            AnalyticsEventName.REWARDED_INTRO_SHOWN,
            adEventBundle(AdFormat.REWARDED_INTERSTITIAL, placement, adUnitId, route),
        )
    }

    fun logRewardedInterstitialIntroSkipped(
        placement: AdPlacement,
        adUnitId: String,
        route: String?,
    ) {
        appAnalytics.logEvent(
            AnalyticsEventName.REWARDED_INTRO_SKIPPED,
            adEventBundle(AdFormat.REWARDED_INTERSTITIAL, placement, adUnitId, route),
        )
    }

    fun logRewardedInterstitialIntroConfirmed(
        placement: AdPlacement,
        adUnitId: String,
        route: String?,
    ) {
        appAnalytics.logEvent(
            AnalyticsEventName.REWARDED_INTRO_CONFIRMED,
            adEventBundle(AdFormat.REWARDED_INTERSTITIAL, placement, adUnitId, route),
        )
    }

    fun logAdAfterEngagement(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        route: String?,
        sessionDurationSeconds: Long,
        verseCountBeforeAd: Int,
        sessionAudioPlayed: Boolean,
        sessionContentType: String?,
    ) {
        appAnalytics.logEvent(
            AnalyticsEventName.AD_AFTER_ENGAGEMENT,
            adEventBundle(adFormat, placement, adUnitId, route).apply {
                putLong(AnalyticsParamKey.SESSION_DURATION_S, sessionDurationSeconds)
                putLong(AnalyticsParamKey.VERSE_COUNT_BEFORE_AD, verseCountBeforeAd.toLong())
                putLong(AnalyticsParamKey.SESSION_AUDIO_PLAYED, if (sessionAudioPlayed) 1L else 0L)
                if (!sessionContentType.isNullOrBlank()) {
                    putString(AnalyticsParamKey.SESSION_CONTENT_TYPE, sessionContentType)
                }
            },
        )
    }

    fun logPaidEvent(context: AdPaidEventContext) {
        val adValue = context.adValue
        if (adValue.valueMicros == 0L) {
            Timber.d(
                "Ad paid event micros=0 (common for test ads / some networks) format=%s placement=%s",
                context.adFormat.analyticsValue,
                context.placement.analyticsValue,
            )
        }
        Timber.i(
            "Ad paid event format=%s placement=%s micros=%d currency=%s precision=%d responseId=%s adapter=%s",
            context.adFormat.analyticsValue,
            context.placement.analyticsValue,
            adValue.valueMicros,
            adValue.currencyCode,
            adValue.precisionType,
            context.responseMeta.responseId,
            context.responseMeta.mediationAdapterClassName,
        )

        recordRuntimeTelemetry(context.adFormat, context.placement, "paid_event")
        appAnalytics.logEvent(
            AnalyticsEventName.AD_PAID_EVENT,
            Bundle().apply {
                putString(AnalyticsParamKey.AD_FORMAT, context.adFormat.analyticsValue)
                putString(AnalyticsParamKey.PLACEMENT, context.placement.analyticsValue)
                putString(AnalyticsParamKey.AD_UNIT_ID, context.adUnitId)
                putLong(AnalyticsParamKey.VALUE_MICROS, adValue.valueMicros)
                putDouble(AnalyticsParamKey.AD_VALUE, adValue.valueMicros / 1_000_000.0)
                putDouble(AnalyticsParamKey.VALUE, adValue.valueMicros / 1_000_000.0)
                putString(AnalyticsParamKey.CURRENCY, adValue.currencyCode)
                putLong(AnalyticsParamKey.PRECISION, adValue.precisionType.toLong())
                putString(AnalyticsParamKey.RESPONSE_ID, context.responseMeta.responseId)
                putString(AnalyticsParamKey.MEDIATION_ADAPTER, context.responseMeta.mediationAdapterClassName)
                putString(AnalyticsParamKey.LOADED_ADAPTER_NAME, context.responseMeta.loadedAdapterName)
                putString(AnalyticsParamKey.NETWORK, context.responseMeta.networkName)
                putString(AnalyticsParamKey.ROUTE, context.route ?: "unknown")
            },
        )
    }

    fun logImpression(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        route: String? = null,
    ) {
        appAnalytics.logEvent(
            AnalyticsEventName.AD_IMPRESSION,
            adEventBundle(adFormat, placement, adUnitId, route),
        )
    }

    fun logClick(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        route: String? = null,
    ) {
        appAnalytics.logEvent(
            AnalyticsEventName.AD_CLICK,
            adEventBundle(adFormat, placement, adUnitId, route),
        )
    }

    fun logServed(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        route: String? = null,
    ) {
        appAnalytics.logEvent(
            AnalyticsEventName.AD_SERVED,
            adEventBundle(adFormat, placement, adUnitId, route),
        )
    }

    fun extractResponseMeta(responseInfo: ResponseInfo?): AdResponseMeta {
        val adapterInfo = responseInfo?.loadedAdapterResponseInfo
        return AdResponseMeta(
            responseId = responseInfo?.responseId,
            mediationAdapterClassName = responseInfo?.mediationAdapterClassName,
            loadedAdapterName = adapterInfo?.adSourceName,
            networkName = adapterInfo?.adSourceName,
        )
    }

    private fun adEventBundle(
        adFormat: AdFormat,
        placement: AdPlacement,
        adUnitId: String,
        route: String?,
    ): Bundle =
        Bundle().apply {
            putString(AnalyticsParamKey.AD_FORMAT, adFormat.analyticsValue)
            putString(AnalyticsParamKey.PLACEMENT, placement.analyticsValue)
            putString(AnalyticsParamKey.AD_UNIT_ID, adUnitId)
            putString(AnalyticsParamKey.ROUTE, route ?: "unknown")
        }

    private fun recordRuntimeTelemetry(
        adFormat: AdFormat,
        placement: AdPlacement,
        event: String,
        suppressReason: String? = null,
    ) {
        telemetryScope.launch {
            preferencesDataSource.recordAdRuntimeEvent(
                AdRuntimeTelemetryEvent(
                    format = adFormat.analyticsValue,
                    placement = placement.analyticsValue,
                    event = event,
                    suppressReason = suppressReason,
                ),
            )
        }
    }
}
