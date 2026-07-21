package com.parsfilo.contentapp.feature.ads

import android.os.Bundle
import com.google.android.gms.ads.AdValue
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.firebase.AnalyticsEventName
import com.parsfilo.contentapp.core.firebase.AnalyticsParamKey
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdRevenueLoggerTest {

    private val appAnalytics = mockk<AppAnalytics>(relaxed = true)
    private val preferencesDataSource = mockk<PreferencesDataSource>(relaxed = true)
    private val logger = AdRevenueLogger(appAnalytics, preferencesDataSource)

    @Test
    fun `show intent logs GA4 event and placement runtime counter`() {
        logger.logShowIntent(
            adFormat = AdFormat.REWARDED,
            placement = AdPlacement.REWARDED_DEFAULT,
            route = "rewards",
            trigger = "user_action",
            adReady = true,
        )

        verify {
            appAnalytics.logEvent(
                AnalyticsEventName.AD_SHOW_INTENT,
                withArg { bundle ->
                    requireNotNull(bundle)
                    assertThat(bundle.getString(AnalyticsParamKey.AD_FORMAT))
                        .isEqualTo(AdFormat.REWARDED.analyticsValue)
                    assertThat(bundle.getString(AnalyticsParamKey.PLACEMENT))
                        .isEqualTo(AdPlacement.REWARDED_DEFAULT.analyticsValue)
                    assertThat(bundle.getString(AnalyticsParamKey.SHOW_TRIGGER)).isEqualTo("user_action")
                    assertThat(bundle.getLong(AnalyticsParamKey.AD_READY)).isEqualTo(1L)
                },
            )
        }
        coVerify(timeout = 1_000L) {
            preferencesDataSource.recordAdRuntimeEvent(
                match {
                    it.format == AdFormat.REWARDED.analyticsValue &&
                        it.placement == AdPlacement.REWARDED_DEFAULT.analyticsValue &&
                        it.event == "show_intent" &&
                        it.suppressReason == null
                },
            )
        }
    }

    @Test
    fun `load failure records classified placement suppression`() {
        logger.logFailedToLoad(
            adFormat = AdFormat.INTERSTITIAL,
            placement = AdPlacement.INTERSTITIAL_NAV_BREAK,
            adUnitId = "interstitial-unit",
            errorCode = 3,
            errorMessage = "no fill",
        )

        coVerify(timeout = 1_000L) {
            preferencesDataSource.recordAdRuntimeEvent(
                match {
                    it.format == AdFormat.INTERSTITIAL.analyticsValue &&
                        it.placement == AdPlacement.INTERSTITIAL_NAV_BREAK.analyticsValue &&
                        it.event == "load_failed" &&
                        it.suppressReason == AdSuppressReason.LOAD_FAILURE.analyticsValue
                },
            )
        }
    }

    @Test
    fun `paid event logs revenue and response metadata without user identifiers`() {
        val adValue = mockk<AdValue> {
            every { valueMicros } returns 123_456L
            every { currencyCode } returns "USD"
            every { precisionType } returns AdValue.PrecisionType.PRECISE
        }

        logger.logPaidEvent(
            AdPaidEventContext(
                adUnitId = "ca-app-pub-3312485084079132/1234567890",
                adFormat = AdFormat.REWARDED,
                placement = AdPlacement.REWARDED_DEFAULT,
                route = "content",
                adValue = adValue,
                responseMeta = AdResponseMeta(
                    responseId = "response-id",
                    mediationAdapterClassName = "com.google.ads.Adapter",
                    loadedAdapterName = "Google",
                    networkName = "Google",
                ),
            ),
        )

        verify {
            appAnalytics.logEvent(
                AnalyticsEventName.AD_PAID_EVENT,
                withArg { bundle -> assertPaidEventBundle(bundle) },
            )
        }
    }

    @Test
    fun `failed show logs bounded error fields`() {
        logger.logFailedToShow(
            adFormat = AdFormat.REWARDED,
            placement = AdPlacement.REWARDED_DEFAULT,
            adUnitId = "rewarded-unit",
            errorCode = 3,
            errorMessage = "no fill",
            route = "rewards",
        )

        verify {
            appAnalytics.logEvent(
                AnalyticsEventName.AD_FAILED_TO_SHOW,
                withArg { bundle ->
                    requireNotNull(bundle)
                    assertThat(bundle.getString(AnalyticsParamKey.AD_FORMAT))
                        .isEqualTo(AdFormat.REWARDED.analyticsValue)
                    assertThat(bundle.getString(AnalyticsParamKey.PLACEMENT))
                        .isEqualTo(AdPlacement.REWARDED_DEFAULT.analyticsValue)
                    assertThat(bundle.getString(AnalyticsParamKey.AD_UNIT_ID))
                        .isEqualTo("rewarded-unit")
                    assertThat(bundle.getLong(AnalyticsParamKey.ERROR_CODE)).isEqualTo(3L)
                    assertThat(bundle.getString(AnalyticsParamKey.ERROR_MESSAGE))
                        .isEqualTo("no fill")
                },
            )
        }
    }

    private fun assertPaidEventBundle(bundle: Bundle?) {
        requireNotNull(bundle)
        assertThat(bundle.getString(AnalyticsParamKey.AD_FORMAT))
            .isEqualTo(AdFormat.REWARDED.analyticsValue)
        assertThat(bundle.getString(AnalyticsParamKey.PLACEMENT))
            .isEqualTo(AdPlacement.REWARDED_DEFAULT.analyticsValue)
        assertThat(bundle.getString(AnalyticsParamKey.AD_UNIT_ID))
            .isEqualTo("ca-app-pub-3312485084079132/1234567890")
        assertThat(bundle.getLong(AnalyticsParamKey.VALUE_MICROS)).isEqualTo(123_456L)
        assertThat(bundle.getDouble(AnalyticsParamKey.AD_VALUE)).isWithin(0.000001).of(0.123456)
        assertThat(bundle.getDouble(AnalyticsParamKey.VALUE)).isWithin(0.000001).of(0.123456)
        assertThat(bundle.getString(AnalyticsParamKey.CURRENCY)).isEqualTo("USD")
        assertThat(bundle.getLong(AnalyticsParamKey.PRECISION))
            .isEqualTo(AdValue.PrecisionType.PRECISE.toLong())
        assertThat(bundle.getString(AnalyticsParamKey.RESPONSE_ID)).isEqualTo("response-id")
        assertThat(bundle.getString(AnalyticsParamKey.MEDIATION_ADAPTER))
            .isEqualTo("com.google.ads.Adapter")
        assertThat(bundle.getString(AnalyticsParamKey.LOADED_ADAPTER_NAME)).isEqualTo("Google")
        assertThat(bundle.getString(AnalyticsParamKey.NETWORK)).isEqualTo("Google")
        assertThat(bundle.getString(AnalyticsParamKey.ROUTE)).isEqualTo("content")
    }
}
