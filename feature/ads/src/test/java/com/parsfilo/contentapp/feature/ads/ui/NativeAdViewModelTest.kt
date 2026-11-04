package com.parsfilo.contentapp.feature.ads.ui

import android.os.Looper
import com.google.android.gms.ads.nativead.NativeAd
import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.feature.ads.AdGateChecker
import com.parsfilo.contentapp.feature.ads.AdPlacement
import com.parsfilo.contentapp.feature.ads.AdsConsentRuntimeState
import com.parsfilo.contentapp.feature.ads.NativeAdManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Routes own placement selection; no SDK traffic is needed for these tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NativeAdViewModelTest {
    private val manager = mockk<NativeAdManager>()
    private val gate = mockk<AdGateChecker>()

    @Before
    fun setUp() {
        AdsConsentRuntimeState.update(true)
        every { manager.adLoadedFlow } returns MutableSharedFlow(extraBufferCapacity = 1)
        every { manager.getNativeAd(any()) } returns null
        every { gate.shouldShowAds } returns MutableStateFlow(true)
    }

    @After
    fun tearDown() {
        AdsConsentRuntimeState.update(false)
    }

    @Test
    fun initialPlaceholderMustNotConsumeNativeInventoryBeforeRouteSelection() {
        val vm = NativeAdViewModel(manager, gate)

        assertThat(vm.nativeAdState.value).isNull()
        verify(exactly = 0) { manager.getNativeAd(any()) }

        vm.setPlacement(AdPlacement.NATIVE_FEED_HOME)
        verify(exactly = 1) { manager.getNativeAd(AdPlacement.NATIVE_FEED_HOME) }
        verify(exactly = 0) { manager.getNativeAd(AdPlacement.NATIVE_DEFAULT) }
    }

    @Test
    fun preloadEventBeforeRouteSelectionDoesNotConsumeDefaultInventory() {
        val loadedEvents = MutableSharedFlow<Unit>(replay = 1)
        every { manager.adLoadedFlow } returns loadedEvents
        assertThat(loadedEvents.tryEmit(Unit)).isTrue()

        val vm = NativeAdViewModel(manager, gate)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) { manager.getNativeAd(any()) }
        vm.setPlacement(AdPlacement.NATIVE_FEED_CONTENT)
        verify(exactly = 1) { manager.getNativeAd(AdPlacement.NATIVE_FEED_CONTENT) }
    }

    @Test
    fun repeatedSamePlacementDoesNotConsumeAdditionalAds() {
        val vm = NativeAdViewModel(manager, gate)
        vm.setPlacement(AdPlacement.NATIVE_FEED_HOME)
        vm.setPlacement(AdPlacement.NATIVE_FEED_HOME)

        verify(exactly = 1) { manager.getNativeAd(AdPlacement.NATIVE_FEED_HOME) }
    }

    @Test
    fun changingPlacementDestroysUnusedAdAndRequestsExactNewPlacement() {
        val nativeAd = mockk<NativeAd>(relaxed = true)
        every { manager.getNativeAd(AdPlacement.NATIVE_FEED_HOME) } returns nativeAd
        val vm = NativeAdViewModel(manager, gate)

        vm.setPlacement(AdPlacement.NATIVE_FEED_HOME)
        assertThat(vm.nativeAdState.value).isSameInstanceAs(nativeAd)

        vm.setPlacement(AdPlacement.NATIVE_FEED_CONTENT)
        verify(exactly = 1) { nativeAd.destroy() }
        verify(exactly = 1) { manager.getNativeAd(AdPlacement.NATIVE_FEED_CONTENT) }
        assertThat(vm.nativeAdState.value).isNull()
    }

    @Test
    fun explicitDefaultPlacementStillAcquiresFromPool() {
        val vm = NativeAdViewModel(manager, gate)

        vm.setPlacement(AdPlacement.NATIVE_DEFAULT)

        verify(exactly = 1) { manager.getNativeAd(AdPlacement.NATIVE_DEFAULT) }
    }
}
