package com.parsfilo.contentapp.feature.ads

import com.google.android.gms.ads.nativead.NativeAd
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Exercising the real pool lifecycle without sending any AdMob network calls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NativeAdManagerInventoryTest {
    private val logger = mockk<AdRevenueLogger>(relaxed = true)
    private val gate = mockk<AdGateChecker> {
        every { shouldShowAds } returns emptyFlow()
    }
    private val policy = mockk<AdsPolicyConfig> {
        every { nativeTtlMs } returns 30 * 60_000L
        every { nativeExactPlacementOnly } returns false
        every { demandAwarePreloadEnabled } returns true
        every { isNativePlacementEnabled(any()) } returns true
    }
    private val provider = mockk<AdsPolicyProvider> {
        every { getPolicy() } returns policy
    }
    private lateinit var manager: NativeAdManager

    @Before
    fun setUp() {
        AdsConsentRuntimeState.update(true)
        manager =
            NativeAdManager(
                RuntimeEnvironment.getApplication(),
                logger,
                gate,
                provider,
                mockk(relaxed = true),
                mockk(relaxed = true),
            )
    }

    @After
    fun tearDown() {
        AdsConsentRuntimeState.update(false)
    }

    @Test
    fun resetDestroysUnusedNativeInventoryAndEmitsBoundedDiscard() {
        val ad = mockk<NativeAd>(relaxed = true)
        addPooled(ad, loadedAt = SystemTimeProvider.nowMillis(), placement = AdPlacement.NATIVE_FEED_HOME)

        manager.destroyAds()

        verify(exactly = 1) { ad.destroy() }
        verify(exactly = 1) {
            logger.logNativeInventoryDiscarded(
                AdPlacement.NATIVE_FEED_HOME,
                "test-native",
                NativeAdDiscardReason.RESET,
            )
        }
        assertThat(manager.getNativeAd(AdPlacement.NATIVE_FEED_HOME)).isNull()
    }

    @Test
    fun expiredInventoryIsDestroyedAndAttributedToTtl() {
        val ad = mockk<NativeAd>(relaxed = true)
        addPooled(ad, loadedAt = SystemTimeProvider.nowMillis() - 40 * 60_000L)

        assertThat(manager.getNativeAd(AdPlacement.NATIVE_FEED_HOME)).isNull()

        verify(exactly = 1) { ad.destroy() }
        verify(exactly = 1) {
            logger.logNativeInventoryDiscarded(
                AdPlacement.NATIVE_FEED_HOME,
                "test-native",
                NativeAdDiscardReason.EXPIRED,
            )
        }
    }

    @Test
    fun handedOffNativeIsNeverCountedAsDiscarded() {
        val ad = mockk<NativeAd>(relaxed = true)
        addPooled(ad, loadedAt = SystemTimeProvider.nowMillis())

        assertThat(manager.getNativeAd(AdPlacement.NATIVE_FEED_HOME)).isSameInstanceAs(ad)
        manager.destroyAds()

        verify(exactly = 0) { ad.destroy() }
        verify(exactly = 0) { logger.logNativeInventoryDiscarded(any(), any(), any()) }
        verify(exactly = 1) {
            logger.logServed(
                AdFormat.NATIVE,
                AdPlacement.NATIVE_FEED_HOME,
                "test-native",
            )
        }
    }

    @Test
    fun consentRevocationDiscardsNativeWithGateClosedReason() {
        val ad = mockk<NativeAd>(relaxed = true)
        addPooled(ad, loadedAt = SystemTimeProvider.nowMillis())
        AdsConsentRuntimeState.update(false)

        manager.destroyAds()

        verify(exactly = 1) { ad.destroy() }
        verify(exactly = 1) {
            logger.logNativeInventoryDiscarded(
                AdPlacement.NATIVE_FEED_HOME,
                "test-native",
                NativeAdDiscardReason.GATE_CLOSED,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun addPooled(
        ad: NativeAd,
        loadedAt: Long,
        placement: AdPlacement = AdPlacement.NATIVE_FEED_HOME,
    ) {
        // PooledNativeAd is deliberately private in production. A confined
        // test-only reflective fixture verifies the SDK ad lifecycle.
        val type = Class.forName("com.parsfilo.contentapp.feature.ads.PooledNativeAd")
        val constructor = type.declaredConstructors.single().apply { isAccessible = true }
        val pooled = constructor.newInstance(ad, loadedAt, placement, "test-native")
        val field = NativeAdManager::class.java.getDeclaredField("nativeAds").apply { isAccessible = true }
        val cache = field.get(manager) as MutableList<Any>
        cache.add(pooled)
    }
}
