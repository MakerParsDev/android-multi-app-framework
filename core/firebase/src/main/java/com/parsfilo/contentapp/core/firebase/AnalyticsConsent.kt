package com.parsfilo.contentapp.core.firebase

import android.os.Bundle

fun AppAnalytics.logConsentFlowStarted(trigger: String) {
    logEvent(
        AnalyticsEventName.CONSENT_FLOW_STARTED,
        Bundle().apply {
            putString(AnalyticsParamKey.CONSENT_TRIGGER, trigger)
        },
    )
}

fun AppAnalytics.logConsentGranted(trigger: String, umpFormShown: Boolean, ageGateResult: String) {
    setUserProperty(AnalyticsUserPropertyKey.CONSENT_STATUS, "granted")
    setDefaultEventParameter(AnalyticsParamKey.CONSENT_STATE, "granted")
    logEvent(
        AnalyticsEventName.CONSENT_GRANTED,
        Bundle().apply {
            putString(AnalyticsParamKey.CONSENT_STATUS, "granted")
            putString(AnalyticsParamKey.CONSENT_TRIGGER, trigger)
            putString(AnalyticsParamKey.AGE_GATE_RESULT, ageGateResult)
            putLong(AnalyticsParamKey.UMP_FORM_SHOWN, if (umpFormShown) 1L else 0L)
        },
    )
}

fun AppAnalytics.logConsentDenied(trigger: String, umpFormShown: Boolean, ageGateResult: String) {
    setUserProperty(AnalyticsUserPropertyKey.CONSENT_STATUS, "denied")
    setDefaultEventParameter(AnalyticsParamKey.CONSENT_STATE, "denied")
    logEvent(
        AnalyticsEventName.CONSENT_DENIED,
        Bundle().apply {
            putString(AnalyticsParamKey.CONSENT_STATUS, "denied")
            putString(AnalyticsParamKey.CONSENT_TRIGGER, trigger)
            putString(AnalyticsParamKey.AGE_GATE_RESULT, ageGateResult)
            putLong(AnalyticsParamKey.UMP_FORM_SHOWN, if (umpFormShown) 1L else 0L)
        },
    )
}

fun AppAnalytics.logConsentNotRequired(trigger: String, ageGateResult: String) {
    setUserProperty(AnalyticsUserPropertyKey.CONSENT_STATUS, "not_required")
    setDefaultEventParameter(AnalyticsParamKey.CONSENT_STATE, "not_required")
    logEvent(
        AnalyticsEventName.CONSENT_NOT_REQUIRED,
        Bundle().apply {
            putString(AnalyticsParamKey.CONSENT_STATUS, "not_required")
            putString(AnalyticsParamKey.CONSENT_TRIGGER, trigger)
            putString(AnalyticsParamKey.AGE_GATE_RESULT, ageGateResult)
        },
    )
}

fun AppAnalytics.logConsentError(trigger: String, message: String, ageGateResult: String) {
    setUserProperty(AnalyticsUserPropertyKey.CONSENT_STATUS, "error")
    setDefaultEventParameter(AnalyticsParamKey.CONSENT_STATE, "error")
    logEvent(
        AnalyticsEventName.CONSENT_ERROR,
        Bundle().apply {
            putString(AnalyticsParamKey.CONSENT_STATUS, "error")
            putString(AnalyticsParamKey.CONSENT_TRIGGER, trigger)
            putString(AnalyticsParamKey.ERROR_MESSAGE, message)
            putString(AnalyticsParamKey.AGE_GATE_RESULT, ageGateResult)
        },
    )
}

fun AppAnalytics.logConsentMissing(trigger: String, ageGateResult: String) {
    setUserProperty(AnalyticsUserPropertyKey.CONSENT_STATUS, "missing")
    setDefaultEventParameter(AnalyticsParamKey.CONSENT_STATE, "missing")
    logEvent(
        AnalyticsEventName.CONSENT_REFRESHED,
        Bundle().apply {
            putString(AnalyticsParamKey.CONSENT_STATUS, "missing")
            putString(AnalyticsParamKey.CONSENT_TRIGGER, trigger)
            putString(AnalyticsParamKey.AGE_GATE_RESULT, ageGateResult)
        },
    )
}

fun AppAnalytics.logConsentRefreshed(trigger: String, consentStatus: String, ageGateResult: String) {
    setUserProperty(AnalyticsUserPropertyKey.CONSENT_STATUS, consentStatus)
    setDefaultEventParameter(AnalyticsParamKey.CONSENT_STATE, consentStatus)
    logEvent(
        AnalyticsEventName.CONSENT_REFRESHED,
        Bundle().apply {
            putString(AnalyticsParamKey.CONSENT_STATUS, consentStatus)
            putString(AnalyticsParamKey.CONSENT_TRIGGER, trigger)
            putString(AnalyticsParamKey.AGE_GATE_RESULT, ageGateResult)
        },
    )
}

fun AppAnalytics.setConsentStatusProperty(status: String) {
    setUserProperty(AnalyticsUserPropertyKey.CONSENT_STATUS, status)
    setDefaultEventParameter(AnalyticsParamKey.CONSENT_STATE, status)
}

fun AppAnalytics.logPrivacyOptionsOpened(required: Boolean) {
    logEvent(
        AnalyticsEventName.PRIVACY_OPTIONS_OPENED,
        Bundle().apply {
            putLong(AnalyticsParamKey.UMP_FORM_SHOWN, if (required) 1L else 0L)
        },
    )
}

fun AppAnalytics.logConsentDebugResult(
    packageName: String,
    result: String,
    requestGeography: String,
    canRequestAds: Boolean,
    privacyOptionsRequired: Boolean,
) {
    logEvent(
        AnalyticsEventName.CONSENT_DEBUG_RESULT,
        Bundle().apply {
            putString(AnalyticsParamKey.PACKAGE_NAME, packageName)
            putString(AnalyticsParamKey.UMP_RESULT, result)
            putString(AnalyticsParamKey.REQUEST_GEO, requestGeography)
            putLong(AnalyticsParamKey.CAN_REQUEST_ADS, if (canRequestAds) 1L else 0L)
            putLong(
                AnalyticsParamKey.PRIVACY_OPTIONS_REQUIRED,
                if (privacyOptionsRequired) 1L else 0L,
            )
        },
    )
}

fun AppAnalytics.logAgeGateCompleted(result: String) {
    setUserProperty(AnalyticsUserPropertyKey.AGE_GATE_STATUS, result)
    logEvent(
        AnalyticsEventName.AGE_GATE_COMPLETED,
        Bundle().apply {
            putString(AnalyticsParamKey.AGE_GATE_RESULT, result)
        },
    )
}
