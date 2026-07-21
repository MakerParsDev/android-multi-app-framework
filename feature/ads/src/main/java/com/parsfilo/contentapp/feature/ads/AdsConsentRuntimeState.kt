package com.parsfilo.contentapp.feature.ads

import com.parsfilo.contentapp.feature.ads.AdsConsentRuntimeState.canRequestAds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AdsPrivacyState {
    data object Unknown : AdsPrivacyState

    data class Gathering(
        val ageGateStatus: AdAgeGateStatus,
    ) : AdsPrivacyState

    data class CanRequestAds(
        val consentStatus: ConsentStatus,
        val privacyOptionsRequired: Boolean,
        val ageGateStatus: AdAgeGateStatus,
    ) : AdsPrivacyState

    data class DeniedOrLimited(
        val consentStatus: ConsentStatus,
        val privacyOptionsRequired: Boolean,
        val ageGateStatus: AdAgeGateStatus,
    ) : AdsPrivacyState
}

fun AdsPrivacyState.canRequestAds(): Boolean = this is AdsPrivacyState.CanRequestAds

fun AdsPrivacyState.suppressReasonWhenBlocked(nowMillis: Long = SystemTimeProvider.nowMillis()): AdSuppressReason =
    when (this) {
        is AdsPrivacyState.DeniedOrLimited ->
            when (val status = consentStatus) {
                is ConsentStatus.Error ->
                    if (status.retryEligibleAtMillis > nowMillis) {
                        AdSuppressReason.CONSENT_RETRY_BACKOFF
                    } else {
                        AdSuppressReason.CONSENT_ERROR
                    }
                ConsentStatus.Missing -> AdSuppressReason.CONSENT_MISSING
                ConsentStatus.Denied,
                ConsentStatus.Required,
                -> AdSuppressReason.NO_CONSENT
                else -> AdSuppressReason.PRIVACY_LIMITED
            }

        AdsPrivacyState.Unknown,
        is AdsPrivacyState.Gathering,
        -> AdSuppressReason.NO_CONSENT

        is AdsPrivacyState.CanRequestAds -> AdSuppressReason.NO_CONSENT
    }

/**
 * Process-level ad serving gate.
 *
 * Default is false to avoid sending ad requests before UMP completes.
 * State remains backwards-compatible via [canRequestAds].
 */
object AdsConsentRuntimeState {
    private val _state = MutableStateFlow<AdsPrivacyState>(AdsPrivacyState.Unknown)
    val state: StateFlow<AdsPrivacyState> = _state.asStateFlow()

    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    fun update(state: AdsPrivacyState) {
        _state.value = state
        _canRequestAds.value = state.canRequestAds()
    }

    fun update(canRequestAds: Boolean) {
        update(
            if (canRequestAds) {
                AdsPrivacyState.CanRequestAds(
                    consentStatus = ConsentStatus.Obtained,
                    privacyOptionsRequired = false,
                    ageGateStatus = AdAgeGateStatus.UNKNOWN,
                )
            } else {
                AdsPrivacyState.DeniedOrLimited(
                    consentStatus = ConsentStatus.Denied,
                    privacyOptionsRequired = false,
                    ageGateStatus = AdAgeGateStatus.UNKNOWN,
                )
            },
        )
    }
}
