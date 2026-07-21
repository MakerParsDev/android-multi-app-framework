package com.parsfilo.contentapp.core.firebase

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advanced Consent Mode keeps the Analytics SDK active while consent flags control storage,
 * identifiers, user data, and personalization.
 */
const val ADVANCED_CONSENT_MODE_COLLECTION_ENABLED: Boolean = true

/**
 * Central analytics gateway.
 *
 * Domain events live in the Analytics*.kt extension files. Every event and parameter must use
 * AnalyticsContract constants so CI can inventory and validate the complete GA4 schema.
 */
@Singleton
class AppAnalytics @Inject constructor(
    internal val analytics: FirebaseAnalytics,
) {
    private val defaultEventParameters = Bundle()

    fun logEvent(name: String, params: Bundle? = null) {
        analytics.logEvent(name, sanitizeAnalyticsBundle(params))
    }

    /**
     * User IDs are limited to application-generated opaque identifiers. Email, phone, account
     * names, purchase tokens, or provider identifiers are not accepted.
     */
    fun setUserId(userId: String?) {
        require(userId == null || isAllowedAnalyticsUserId(userId)) {
            "Analytics user IDs must be null or an opaque anon_* identifier"
        }
        analytics.setUserId(userId)
    }

    fun setUserProperty(name: String, value: String?) {
        require(isAnalyticsParameterAllowed(name)) { "Sensitive analytics user property: $name" }
        analytics.setUserProperty(name, value?.let { sanitizeAnalyticsString(name, it) })
    }

    fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        analytics.setAnalyticsCollectionEnabled(enabled)
    }

    fun setConsent(
        adStorageGranted: Boolean,
        analyticsStorageGranted: Boolean,
        adUserDataGranted: Boolean = adStorageGranted,
        adPersonalizationGranted: Boolean = adStorageGranted,
    ) {
        analytics.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.AD_STORAGE to
                    if (adStorageGranted) {
                        FirebaseAnalytics.ConsentStatus.GRANTED
                    } else {
                        FirebaseAnalytics.ConsentStatus.DENIED
                    },
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to
                    if (analyticsStorageGranted) {
                        FirebaseAnalytics.ConsentStatus.GRANTED
                    } else {
                        FirebaseAnalytics.ConsentStatus.DENIED
                    },
                FirebaseAnalytics.ConsentType.AD_USER_DATA to
                    if (adUserDataGranted) {
                        FirebaseAnalytics.ConsentStatus.GRANTED
                    } else {
                        FirebaseAnalytics.ConsentStatus.DENIED
                    },
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to
                    if (adPersonalizationGranted) {
                        FirebaseAnalytics.ConsentStatus.GRANTED
                    } else {
                        FirebaseAnalytics.ConsentStatus.DENIED
                    },
            ),
        )
    }

    @Synchronized
    fun setDefaultEventParameters(params: Bundle) {
        defaultEventParameters.clear()
        mergeDefaultEventParameters(params)
    }

    @Synchronized
    fun setDefaultEventParameter(name: String, value: String?) {
        defaultEventParameters.remove(name)
        if (value != null) {
            sanitizeAnalyticsString(name, value)?.let { defaultEventParameters.putString(name, it) }
        }
        publishDefaultEventParameters()
    }

    @Synchronized
    fun setDefaultEventParameter(name: String, value: Long) {
        if (isAnalyticsParameterAllowed(name)) {
            defaultEventParameters.putLong(name, value)
        }
        publishDefaultEventParameters()
    }

    @Suppress("DEPRECATION")
    private fun mergeDefaultEventParameters(params: Bundle) {
        val sanitized = sanitizeAnalyticsBundle(params)
        sanitized?.keySet()?.forEach { key ->
            when (val value = sanitized.get(key)) {
                is String -> defaultEventParameters.putString(key, value)
                is Long -> defaultEventParameters.putLong(key, value)
                is Double -> defaultEventParameters.putDouble(key, value)
            }
        }
        publishDefaultEventParameters()
    }

    private fun publishDefaultEventParameters() {
        analytics.setDefaultEventParameters(Bundle(defaultEventParameters))
    }
}
