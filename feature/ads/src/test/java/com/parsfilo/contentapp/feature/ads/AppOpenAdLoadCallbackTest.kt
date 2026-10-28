package com.parsfilo.contentapp.feature.ads

import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Drives the actual AppOpenAd SDK callbacks without a network request or live AdMob traffic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppOpenAdLoadCallbackTest {
    private val logger = mockk<AdRevenueLogger>(relaxed = true)
    private val policy = mockk<AdsPolicyConfig> {
        every { isAppOpenPlacementEnabled(any()) } returns true
        every { demandAwarePreloadEnabled } returns false
    }
    private val provider = mockk<AdsPolicyProvider> {
        every { getPolicy() } returns policy
    }
    private val callbacks = mutableListOf<AppOpenAd.AppOpenAdLoadCallback>()
    private lateinit var manager: AppOpenAdManager

    @Before
    fun setUp() {
        AdsConsentRuntimeState.update(true)
        mockkStatic(AppOpenAd::class)
        every {
            AppOpenAd.load(
                any<Context>(),
                any<String>(),
                any<AdRequest>(),
                any<AppOpenAd.AppOpenAdLoadCallback>(),
            )
        } answers {
            callbacks.add(arg<AppOpenAd.AppOpenAdLoadCallback>(3))
        }
        manager = AppOpenAdManager(
            context = RuntimeEnvironment.getApplication(),
            preferencesDataSource = mockk<PreferencesDataSource>(relaxed = true),
            adRevenueLogger = logger,
            adsPolicyProvider = provider,
            fullScreenShowCoordinator = mockk(relaxed = true),
            adPreloadRequestBudget = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        AdsConsentRuntimeState.update(false)
        unmockkStatic(AppOpenAd::class)
    }

    @Test
    fun currentSuccessCanPopulateReadyInventory() {
        load()
        callbacks.single().onAdLoaded(ad())

        assertThat(manager.isAdReady()).isTrue()
        verify(exactly = 1) {
            logger.logLoaded(
                AdFormat.APP_OPEN, AdPlacement.APP_OPEN_DEFAULT, "appopen-test", null,
                match { it >= 0L }, "test-adapter",
            )
        }
    }

    @Test
    fun callbackAfterClearCannotRepopulateInventoryOrCountAFill() {
        load()
        manager.clearAd()
        callbacks.single().onAdLoaded(ad())

        assertThat(manager.isAdReady()).isFalse()
        verify(exactly = 0) {
            logger.logLoaded(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun lateResponseAfterConsentRevocationIsRejected() {
        load()
        AdsConsentRuntimeState.update(false)
        callbacks.single().onAdLoaded(ad())

        assertThat(manager.isAdReady()).isFalse()
        verify(exactly = 0) {
            logger.logLoaded(any(), any(), any(), any(), any(), any())
        }

        AdsConsentRuntimeState.update(true)
        load()
        assertThat(callbacks).hasSize(2)
    }

    @Test
    fun oldFailureCannotCancelOrThrottleTheNewLoad() {
        load()
        val oldCallback = callbacks.single()
        manager.clearAd()
        load()

        oldCallback.onAdFailedToLoad(failure())
        load()
        assertThat(callbacks).hasSize(2)
        verify(exactly = 0) {
            logger.logFailedToLoad(any(), any(), any(), any(), any(), any(), any(), any())
        }

        callbacks[1].onAdLoaded(ad())
        assertThat(manager.isAdReady()).isTrue()
    }

    @Test
    fun activeFailureStillRecordsAnError() {
        load()
        callbacks.single().onAdFailedToLoad(failure())

        assertThat(manager.isAdReady()).isFalse()
        verify(exactly = 1) {
            logger.logFailedToLoad(
                AdFormat.APP_OPEN, AdPlacement.APP_OPEN_DEFAULT, "appopen-test",
                2, "timeout", "com.google.android.gms.ads", null, any(),
            )
        }
    }

    @Test
    fun noConsentDoesNotMakeSdkRequest() {
        AdsConsentRuntimeState.update(false)
        load()
        assertThat(callbacks).isEmpty()
    }

    private fun load() {
        manager.loadAd("appopen-test")
    }

    private fun ad(): AppOpenAd {
        val info = mockk<ResponseInfo> {
            every { responseId } returns "test-response"
            every { mediationAdapterClassName } returns "test-adapter"
        }
        return mockk(relaxed = true) {
            every { adUnitId } returns "appopen-test"
            every { responseInfo } returns info
        }
    }

    private fun failure() = mockk<LoadAdError> {
        every { code } returns 2
        every { message } returns "timeout"
        every { domain } returns "com.google.android.gms.ads"
    }
}
