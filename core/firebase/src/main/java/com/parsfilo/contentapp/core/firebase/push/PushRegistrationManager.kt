package com.parsfilo.contentapp.core.firebase.push

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.parsfilo.contentapp.core.common.logging.sanitizeLogMessage
import com.parsfilo.contentapp.core.common.logging.toPrivacySafeThrowable
import com.parsfilo.contentapp.core.common.network.AppDispatchers
import com.parsfilo.contentapp.core.common.network.Dispatcher
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

@Singleton
class PushRegistrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseMessaging: FirebaseMessaging,
    private val preferencesDataSource: PreferencesDataSource,
    private val pushRegistrationSender: PushRegistrationSender,
    private val crashlytics: FirebaseCrashlytics,
    @Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun subscribeToTopics(topics: List<String>) = withContext(ioDispatcher) {
        topics.distinct().forEach { topic ->
            if (topic.isBlank()) return@forEach
            runCatching {
                firebaseMessaging.subscribeToTopicSuspend(topic)
            }.onSuccess {
                Timber.i("FCM topic subscribed: %s", topic)
            }.onFailure { throwable ->
                Timber.w(throwable, "FCM topic subscribe failed: %s", topic)
            }
        }
    }

    suspend fun syncRegistration(
        reason: String,
        tokenOverride: String? = null,
        scheduleRetryOnFailure: Boolean = true,
    ): Boolean =
        withContext(ioDispatcher) {
            runCatching {
                val installationId = preferencesDataSource.getOrCreateInstallationId()
                val preferences = preferencesDataSource.userData.first()
                val adRuntimeTelemetry = preferences.adRuntimeTelemetry
                val attemptTimestamp = System.currentTimeMillis()
                val rawToken = tokenOverride ?: fetchFcmTokenWithRetry()
                val fcmToken = rawToken ?: run {
                    preferencesDataSource.markPushSyncFailure(
                        reason = "token_fetch_failed:$reason",
                        token = "",
                        timestamp = attemptTimestamp,
                    )
                    val message = "Push registration skipped: could not fetch FCM token (reason=$reason)"
                    Timber.w(message)
                    crashlytics.log(sanitizeLogMessage(message))
                    scheduleRetryIfNeeded(
                        reason = reason,
                        scheduleRetryOnFailure = scheduleRetryOnFailure,
                    )
                    return@runCatching false
                }
                preferencesDataSource.markPushSyncAttempt(
                    token = fcmToken,
                    timestamp = attemptTimestamp,
                )
                val payload = PushRegistrationPayload(
                    installationId = installationId,
                    fcmToken = fcmToken,
                    packageName = context.packageName,
                    locale = Locale.getDefault().toLanguageTag(),
                    timezone = TimeZone.getDefault().id,
                    notificationsEnabled = preferences.notificationsEnabled,
                    appVersion = readAppVersion(context),
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    reason = reason,
                    syncedAtEpochMs = attemptTimestamp,
                    tokenHash = fcmToken.sha256(),
                    hasToken = fcmToken.isNotBlank(),
                    lastAttemptAtEpochMs = attemptTimestamp,
                    lastSuccessAtEpochMs = attemptTimestamp,
                    adRuntimeWindowStartAtEpochMs = adRuntimeTelemetry.windowStartAt,
                    adRuntimeLastUpdatedAtEpochMs = adRuntimeTelemetry.lastUpdatedAt,
                    adRuntimeFunnelCounts = adRuntimeTelemetry.funnelCountsByFormat,
                    adRuntimeSuppressReasonCounts = adRuntimeTelemetry.suppressReasonCounts,
                )
                val sent = pushRegistrationSender.send(payload)
                if (sent) {
                    preferencesDataSource.setPushSyncMeta(
                        token = fcmToken,
                        timestamp = payload.syncedAtEpochMs,
                    )
                }
                if (!sent) {
                    preferencesDataSource.markPushSyncFailure(
                        reason = "send_failed:$reason",
                        token = fcmToken,
                        timestamp = attemptTimestamp,
                    )
                    val message = "Push registration send returned false (reason=$reason)"
                    Timber.w(message)
                    crashlytics.log(sanitizeLogMessage(message))
                    scheduleRetryIfNeeded(
                        reason = reason,
                        scheduleRetryOnFailure = scheduleRetryOnFailure,
                    )
                }
                sent
            }.getOrElse { throwable ->
                Timber.w(throwable, "Push registration sync failed.")
                preferencesDataSource.markPushSyncFailure(
                    reason = "exception:${throwable::class.simpleName}:$reason",
                    token = tokenOverride.orEmpty(),
                    timestamp = System.currentTimeMillis(),
                )
                crashlytics.log(sanitizeLogMessage("Push registration sync failed (reason=$reason): ${throwable::class.simpleName}"))
                crashlytics.recordException(throwable.toPrivacySafeThrowable())
                scheduleRetryIfNeeded(
                    reason = reason,
                    scheduleRetryOnFailure = scheduleRetryOnFailure,
                )
                false
            }
        }

    private fun scheduleRetryIfNeeded(
        reason: String,
        scheduleRetryOnFailure: Boolean,
    ) {
        if (!scheduleRetryOnFailure) return
        val retryReason = "retry_after_$reason"
        Timber.w("Push registration scheduling immediate retry reason=%s", retryReason)
        PushRegistrationSyncWorker.scheduleImmediate(
            context = context,
            reason = retryReason,
        )
    }

    private suspend fun fetchFcmTokenWithRetry(): String? {
        TOKEN_FETCH_RETRY_DELAYS_MS.forEachIndexed { index, retryDelayMs ->
            runCatching { firebaseMessaging.fetchTokenSuspend() }
                .onSuccess { token ->
                    if (token.isNotBlank()) {
                        return token
                    }
                    Timber.w("FCM token fetch returned blank token (attempt=%d).", index + 1)
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.w(throwable, "Unable to fetch FCM token (attempt=%d).", index + 1)
                }

            if (index < TOKEN_FETCH_RETRY_DELAYS_MS.lastIndex) {
                val jitter = Random.nextLong(from = 0L, until = TOKEN_FETCH_MAX_JITTER_MS + 1)
                delay(retryDelayMs + jitter)
            }
        }
        return null
    }
}

internal fun String.sha256(): String {
    if (isBlank()) return ""
    return MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it) }
}

internal fun readAppVersion(context: Context): String {
    return runCatching {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        pi.versionName ?: PackageInfoCompat.getLongVersionCode(pi).toString()
    }.getOrElse { "unknown" }
}

internal suspend fun FirebaseMessaging.fetchTokenSuspend(): String =
    suspendCancellableCoroutine { continuation ->
        token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(
                    task.exception ?: IllegalStateException("FCM token request failed")
                )
            }
        }
    }

internal suspend fun FirebaseMessaging.subscribeToTopicSuspend(topic: String): Unit =
    suspendCancellableCoroutine { continuation ->
        subscribeToTopic(topic).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(
                    task.exception ?: IllegalStateException("FCM topic subscribe failed: $topic")
                )
            }
        }
    }

private val TOKEN_FETCH_RETRY_DELAYS_MS = listOf(1_000L, 3_000L, 7_000L)
private const val TOKEN_FETCH_MAX_JITTER_MS = 400L
