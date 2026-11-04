package com.parsfilo.contentapp.feature.ads.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.ads.nativead.NativeAd
import com.parsfilo.contentapp.feature.ads.AdGateChecker
import com.parsfilo.contentapp.feature.ads.AdPlacement
import com.parsfilo.contentapp.feature.ads.AdsConsentRuntimeState
import com.parsfilo.contentapp.feature.ads.NativeAdManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NativeAdViewModel @Inject constructor(
    private val nativeAdManager: NativeAdManager,
    private val adGateChecker: AdGateChecker,
) : ViewModel() {
    private val currentPlacement = MutableStateFlow(AdPlacement.NATIVE_DEFAULT)

    // The default is only a placeholder until a route selects its actual slot.
    // Consuming preloaded inventory here wastes an ad when the first route
    // immediately switches to NATIVE_FEED_HOME or NATIVE_FEED_CONTENT.
    private var placementSelected = false

    private val _nativeAdState = MutableStateFlow<NativeAd?>(null)
    val nativeAdState: StateFlow<NativeAd?> = _nativeAdState.asStateFlow()

    init {
        // Only consume from the pool once the navigation route has selected
        // a real placement and UI does not already hold an ad.
        viewModelScope.launch {
            nativeAdManager.adLoadedFlow.collect {
                refreshAdIfAvailable()
            }
        }

        viewModelScope.launch {
            combine(
                AdsConsentRuntimeState.canRequestAds,
                adGateChecker.shouldShowAds,
            ) { canRequestAds, shouldShowAds ->
                canRequestAds && shouldShowAds
            }.collect { eligible ->
                if (!eligible) {
                    clearCurrentNativeAd()
                }
            }
        }
    }

    private fun refreshAdIfAvailable() {
        if (!placementSelected || _nativeAdState.value != null) return
        _nativeAdState.value = nativeAdManager.getNativeAd(currentPlacement.value)
    }

    private fun clearCurrentNativeAd() {
        val previous = _nativeAdState.value ?: return
        _nativeAdState.value = null
        previous.destroy()
    }

    fun setPlacement(placement: AdPlacement) {
        if (placementSelected && currentPlacement.value == placement) return
        clearCurrentNativeAd()
        currentPlacement.value = placement
        placementSelected = true
        refreshAdIfAvailable()
    }

    override fun onCleared() {
        super.onCleared()
        // Start destroying the ad when ViewModel is cleared/screen destroyed
        clearCurrentNativeAd()
    }
}
