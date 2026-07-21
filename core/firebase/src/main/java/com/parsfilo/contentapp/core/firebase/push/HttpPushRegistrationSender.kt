package com.parsfilo.contentapp.core.firebase.push

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.parsfilo.contentapp.core.common.logging.sanitizeLogMessage
import com.parsfilo.contentapp.core.common.logging.toPrivacySafeThrowable
import com.parsfilo.contentapp.core.common.network.AppDispatchers
import com.parsfilo.contentapp.core.common.network.Dispatcher
import com.parsfilo.contentapp.core.common.network.TimberNetworkLoggingInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class HttpPushRegistrationSender @Inject constructor(
    @Named(PUSH_REGISTRATION_URL) private val pushRegistrationUrl: String,
    @Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
    private val firebaseAppCheck: FirebaseAppCheck,
    private val crashlytics: FirebaseCrashlytics,
) : PushRegistrationSender {
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .addInterceptor(TimberNetworkLoggingInterceptor("push_registration"))
            .build()
    }

    override suspend fun send(payload: PushRegistrationPayload): Boolean = withContext(ioDispatcher) {
        if (pushRegistrationUrl.isBlank()) {
            Timber.d("Push registration URL is empty, skipping sync.")
            return@withContext false
        }

        val appCheckToken =
            runCatching { firebaseAppCheck.getAppCheckToken(false).await().token.orEmpty() }
                .getOrElse { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.w(throwable, "Push registration skipped: unable to read App Check token")
                    ""
                }
        if (appCheckToken.isBlank()) {
            reportFinalFailureToCrashlytics(
                attempt = 0,
                result = PushSendResult(
                    successful = false,
                    throwable = IllegalStateException("App Check token unavailable"),
                ),
            )
            return@withContext false
        }

        var attempt = 0

        while (true) {
            currentCoroutineContext().ensureActive()
            val result = runAttempt(payload, appCheckToken)
            if (result.successful) {
                return@withContext true
            }

            val shouldRetry =
                shouldRetryPushRegistration(
                    attempt = attempt,
                    statusCode = result.statusCode,
                    throwable = result.throwable,
                )

            logAttemptFailure(attempt = attempt, result = result, willRetry = shouldRetry)
            if (!shouldRetry) {
                reportFinalFailureToCrashlytics(attempt = attempt, result = result)
                return@withContext false
            }

            val retryDelay = nextRetryDelayMillis()
            Timber.w("Retrying push registration in %d ms (attempt=%d).", retryDelay, attempt + 2)
            delay(retryDelay)
            attempt += 1
        }

        error("Unreachable")
    }

    private fun runAttempt(
        payload: PushRegistrationPayload,
        appCheckToken: String,
    ): PushSendResult {
        val request = buildPushRegistrationRequest(
            url = pushRegistrationUrl.toHttpUrl(),
            payload = payload,
            appCheckToken = appCheckToken,
        )

        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val statusCode = response.code
                val bodySnippet = readBodySnippet(response.body.string())
                Timber.i("Push registration HTTP status=%d", statusCode)
                if (statusCode in 200..299) {
                    PushSendResult(successful = true)
                } else {
                    PushSendResult(
                        successful = false,
                        statusCode = statusCode,
                        responseBodySnippet = bodySnippet,
                    )
                }
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) {
                throw throwable
            }
            PushSendResult(successful = false, throwable = throwable)
        }
    }

    private fun logAttemptFailure(attempt: Int, result: PushSendResult, willRetry: Boolean) {
        val responseSnippet = result.responseBodySnippet?.let(::maskSensitivePayload)
        when {
            result.throwable != null -> {
                Timber.w(
                    result.throwable,
                    "Push registration attempt=%d failed (willRetry=%s).",
                    attempt + 1,
                    willRetry,
                )
            }

            result.statusCode != null -> {
                Timber.w(
                    "Push registration attempt=%d failed: status=%d, willRetry=%s, response=%s",
                    attempt + 1,
                    result.statusCode,
                    willRetry,
                    responseSnippet ?: "<empty>",
                )
            }

            else -> {
                Timber.w(
                    "Push registration attempt=%d failed with unknown state (willRetry=%s).",
                    attempt + 1,
                    willRetry,
                )
            }
        }
    }

    private fun reportFinalFailureToCrashlytics(attempt: Int, result: PushSendResult) {
        val responseSnippet = result.responseBodySnippet?.let(::maskSensitivePayload)
        val message =
            buildString {
                append("Push registration failed after ${attempt + 1} attempt(s)")
                result.statusCode?.let { append(" status=$it") }
                result.throwable?.let { append(" error=${it::class.simpleName}") }
                if (!responseSnippet.isNullOrBlank()) {
                    append(" response=$responseSnippet")
                }
            }

        crashlytics.log(sanitizeLogMessage(message))
        val failure =
            result.throwable ?: IllegalStateException(
                message,
            )
        crashlytics.recordException(failure.toPrivacySafeThrowable())
    }
}

const val PUSH_REGISTRATION_URL = "push_registration_url"
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 15_000
private const val MAX_ERROR_BODY_CHARS = 2_048
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private data class PushSendResult(
    val successful: Boolean,
    val statusCode: Int? = null,
    val responseBodySnippet: String? = null,
    val throwable: Throwable? = null,
)

internal fun readBodySnippet(raw: String?): String? = raw?.take(MAX_ERROR_BODY_CHARS)?.ifBlank { null }

private fun maskSensitivePayload(raw: String): String {
    val keyValueRegex = Regex("""(?i)("(installationId|fcmToken|token|pushId)"\s*:\s*")[^"]*(")""")
    val queryRegex = Regex("""(?i)\b(installationId|fcmToken|token|pushId)=([^&\s]+)""")

    val redactedKeyValue = keyValueRegex.replace(raw) { matchResult ->
        "${matchResult.groupValues[1]}***${matchResult.groupValues[3]}"
    }
    return queryRegex.replace(redactedKeyValue) { matchResult ->
        "${matchResult.groupValues[1]}=***"
    }
}

internal fun buildPushRegistrationRequest(
    url: HttpUrl,
    payload: PushRegistrationPayload,
    appCheckToken: String,
): Request {
    require(appCheckToken.isNotBlank()) { "App Check token is required" }
    return Request.Builder()
        .url(url)
        .post(payload.toJson().toString().toRequestBody(JSON_MEDIA_TYPE))
        .header("Accept", "application/json")
        .header("X-Firebase-AppCheck", appCheckToken)
        .build()
}

internal fun PushRegistrationPayload.toJson(): JSONObject = JSONObject().apply {
    put("installationId", installationId)
    put("fcmToken", fcmToken)
    put("packageName", packageName)
    put("locale", locale)
    put("timezone", timezone)
    put("notificationsEnabled", notificationsEnabled)
    put("appVersion", appVersion)
    put("deviceModel", deviceModel)
    put("reason", reason)
    put("syncedAtEpochMs", syncedAtEpochMs)
    put("tokenHash", tokenHash)
    put("hasToken", hasToken)
    put("lastAttemptAtEpochMs", lastAttemptAtEpochMs)
    lastSuccessAtEpochMs?.let { put("lastSuccessAtEpochMs", it) }
    lastFailureReason?.let { put("lastFailureReason", it) }
    adRuntimeWindowStartAtEpochMs?.let { put("adRuntimeWindowStartAtEpochMs", it) }
    adRuntimeLastUpdatedAtEpochMs?.let { put("adRuntimeLastUpdatedAtEpochMs", it) }
    put("adRuntimeFunnelCounts", adRuntimeFunnelCounts.toJsonObject())
    put("adRuntimeSuppressReasonCounts", adRuntimeSuppressReasonCounts.toJsonObject())
}

private fun Map<String, *>.toJsonObject(): JSONObject = JSONObject().apply {
    for ((key, value) in this@toJsonObject) {
        put(
            key,
            when (value) {
                is Map<*, *> -> value.entries.associate { (nestedKey, nestedValue) ->
                    nestedKey.toString() to nestedValue
                }.toJsonObject()
                else -> value
            },
        )
    }
}
