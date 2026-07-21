package com.parsfilo.contentapp.core.firebase.config

import android.content.Context
import android.content.pm.ApplicationInfo
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import com.parsfilo.contentapp.core.firebase.RuntimeFailure
import com.parsfilo.contentapp.core.firebase.RuntimeObservability
import com.parsfilo.contentapp.core.firebase.RuntimeSignal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class RemoteConfigManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfig: FirebaseRemoteConfig,
    private val runtimeObservability: RuntimeObservability,
) {
    private val settingsApplied = AtomicBoolean(false)
    private val localDefaults = ConcurrentHashMap<String, Any>()

    fun applyClientSettingsIfNeeded() {
        if (settingsApplied.getAndSet(true)) return

        val minimumFetchIntervalSeconds =
            if (isDebugBuild()) DEBUG_MIN_FETCH_INTERVAL_SECONDS else RELEASE_MIN_FETCH_INTERVAL_SECONDS

        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(minimumFetchIntervalSeconds)
                .build(),
        )
        Timber.d(
            "Remote Config settings applied (minFetchIntervalSeconds=%s)",
            minimumFetchIntervalSeconds,
        )
    }

    fun setDefaults(defaults: Map<String, Any>) {
        applyClientSettingsIfNeeded()
        localDefaults.putAll(defaults)
        remoteConfig.setDefaultsAsync(defaults)
    }

    suspend fun fetchAndActivate(): Boolean =
        suspendCancellableCoroutine { continuation ->
            applyClientSettingsIfNeeded()
            remoteConfig.fetchAndActivate()
                .addOnSuccessListener { activated ->
                    if (continuation.isActive) {
                        continuation.resume(activated)
                    }
                }
                .addOnFailureListener { throwable ->
                    reportFetchFailure("suspend", throwable)
                    if (continuation.isActive) {
                        continuation.resumeWithException(throwable)
                    }
                }
        }

    fun fetchAndActivateAsync(
        onSuccess: (Boolean) -> Unit = {},
        onFailure: (Throwable) -> Unit = {},
    ) {
        applyClientSettingsIfNeeded()
        remoteConfig.fetchAndActivate()
            .addOnSuccessListener { activated -> onSuccess(activated) }
            .addOnFailureListener { throwable ->
                reportFetchFailure("async", throwable)
                onFailure(throwable)
            }
    }

    private fun reportFetchFailure(operation: String, throwable: Throwable) {
        runtimeObservability.recordFailure(
            RuntimeFailure(
                signal = RuntimeSignal.REMOTE_CONFIG_FETCH,
                code = "FETCH_AND_ACTIVATE_FAILED",
                message = throwable.message ?: throwable::class.java.simpleName,
                cause = throwable,
                attributes = mapOf("operation" to operation),
            ),
        )
    }

    fun getLong(key: String): Long {
        return getLongOrNull(key) ?: 0L
    }

    fun getString(key: String): String {
        return getStringOrNull(key).orEmpty()
    }

    fun getLongOrNull(key: String): Long? {
        val value = remoteConfig.getValue(key)
        return if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) {
            coerceToLong(localDefaults[key])
        } else {
            value.rawStringOrNull()?.toLongOrNull() ?: coerceToLong(localDefaults[key])
        }
    }

    fun getStringOrNull(key: String): String? {
        val value = remoteConfig.getValue(key)
        return if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) {
            coerceToString(localDefaults[key])
        } else {
            value.rawStringOrNull() ?: coerceToString(localDefaults[key])
        }
    }

    fun getBooleanOrNull(key: String): Boolean? {
        val value = remoteConfig.getValue(key)
        return if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) {
            coerceToBoolean(localDefaults[key])
        } else {
            parseBooleanString(value.rawStringOrNull()) ?: coerceToBoolean(localDefaults[key])
        }
    }

    fun getAll(keys: List<String>): Map<String, String> =
        keys.associateWith { key -> getString(key) }

    private fun isDebugBuild(): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun coerceToLong(value: Any?): Long? =
        when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Short -> value.toLong()
            is Byte -> value.toLong()
            is Double -> value.toLong()
            is Float -> value.toLong()
            is String -> value.toLongOrNull()
            is Boolean -> if (value) 1L else 0L
            else -> null
        }

    private fun coerceToBoolean(value: Any?): Boolean? =
        when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> parseBooleanString(value)
            else -> null
        }

    private fun parseBooleanString(value: String?): Boolean? =
        when (value?.trim()?.lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> null
        }

    private fun coerceToString(value: Any?): String? =
        when (value) {
            null -> null
            is String -> value
            else -> value.toString()
        }

    private fun FirebaseRemoteConfigValue.rawStringOrNull(): String? =
        runCatching { asByteArray().toString(Charsets.UTF_8).trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }

    private companion object {
        private const val DEBUG_MIN_FETCH_INTERVAL_SECONDS = 60L
        private const val RELEASE_MIN_FETCH_INTERVAL_SECONDS = 21_600L
    }
}
