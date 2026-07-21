package com.parsfilo.contentapp.feature.prayertimes.data

import com.parsfilo.contentapp.core.common.logging.toPrivacySafeThrowable
import com.parsfilo.contentapp.core.common.network.AppDispatchers
import com.parsfilo.contentapp.core.common.network.Dispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * Process-wide reverse-geocoding policy for the public Nominatim service.
 *
 * Coordinates are reduced to roughly 110 m precision before leaving the device.
 * Results are held only in process memory: fresh for 24 hours and eligible as an
 * outage-only fallback for seven days. Concurrent lookups for the same rounded
 * location share one request.
 */
@Singleton
class NominatimReverseGeocoder private constructor(
    private val transport: NominatimTransport,
    private val nowMillis: () -> Long,
    private val sleep: suspend (Long) -> Unit,
    private val requestIdFactory: () -> String,
    private val fetchScope: CoroutineScope,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) {
    @Inject
    constructor(
        transport: NominatimHttpTransport,
        @Dispatcher(AppDispatchers.IO) ioDispatcher: CoroutineDispatcher,
    ) : this(
        transport = transport,
        nowMillis = System::currentTimeMillis,
        sleep = { delay(it) },
        requestIdFactory = { "geo-${REQUEST_SEQUENCE.incrementAndGet().toString(16)}" },
        fetchScope = CoroutineScope(SupervisorJob() + ioDispatcher),
        marker = Unit,
    )

    internal constructor(
        transport: NominatimTransport,
        nowMillis: () -> Long,
        sleep: suspend (Long) -> Unit,
        requestIdFactory: () -> String,
        fetchScope: CoroutineScope,
    ) : this(
        transport = transport,
        nowMillis = nowMillis,
        sleep = sleep,
        requestIdFactory = requestIdFactory,
        fetchScope = fetchScope,
        marker = Unit,
    )

    private val stateMutex = Mutex()
    private val throttleMutex = Mutex()
    private val cache = object : LinkedHashMap<ApproximateLocationKey, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<ApproximateLocationKey, CacheEntry>?,
        ): Boolean = size > NOMINATIM_MAX_CACHE_ENTRIES
    }
    private val inFlight = mutableMapOf<ApproximateLocationKey, CompletableDeferred<PrayerAddressCandidate?>>()
    private var lastRequestStartedAtMillis: Long? = null

    suspend fun reverse(
        latitude: Double,
        longitude: Double,
    ): PrayerAddressCandidate? {
        val key = ApproximateLocationKey.from(latitude, longitude) ?: return null
        val decision = stateMutex.withLock {
            val now = nowMillis()
            val cached = cache[key]
            if (cached != null && cached.ageMillis(now) <= NOMINATIM_FRESH_CACHE_TTL_MS) {
                LookupDecision.Cached(cached.candidate)
            } else {
                val existing = inFlight[key]
                if (existing != null) {
                    LookupDecision.Await(existing)
                } else {
                    val deferred = CompletableDeferred<PrayerAddressCandidate?>()
                    inFlight[key] = deferred
                    LookupDecision.Fetch(
                        deferred = deferred,
                        staleCandidate = cached
                            ?.takeIf { it.ageMillis(now) <= NOMINATIM_STALE_FALLBACK_TTL_MS }
                            ?.candidate,
                    )
                }
            }
        }

        return when (decision) {
            is LookupDecision.Cached -> decision.candidate
            is LookupDecision.Await -> decision.deferred.await()
            is LookupDecision.Fetch -> {
                fetchScope.launch { fetchAndPublish(key, decision) }
                decision.deferred.await()
            }
        }
    }

    private suspend fun fetchAndPublish(
        key: ApproximateLocationKey,
        decision: LookupDecision.Fetch,
    ) {
        try {
            val result = runCatching {
                val fetchResult = fetchWithRetry(key)
                val resolved = when (fetchResult) {
                    is FetchResult.Success -> fetchResult.candidate
                    FetchResult.Failure -> decision.staleCandidate
                }
                if (fetchResult is FetchResult.Success && fetchResult.candidate != null) {
                    stateMutex.withLock {
                        cache[key] = CacheEntry(fetchResult.candidate, nowMillis())
                    }
                }
                resolved
            }
            result.fold(
                onSuccess = decision.deferred::complete,
                onFailure = decision.deferred::completeExceptionally,
            )
        } finally {
            stateMutex.withLock {
                if (inFlight[key] === decision.deferred) {
                    inFlight.remove(key)
                }
            }
        }
    }

    private suspend fun fetchWithRetry(key: ApproximateLocationKey): FetchResult {
        val requestId = requestIdFactory()
        repeat(NOMINATIM_MAX_ATTEMPTS) { attempt ->
            awaitGlobalThrottle()
            val response = try {
                transport.reverse(key.latitude, key.longitude)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                logFailure(
                    requestId = requestId,
                    attempt = attempt + 1,
                    statusCode = null,
                    outcome = "io_error",
                    cause = error.toPrivacySafeThrowable(),
                )
                if (attempt < NOMINATIM_MAX_ATTEMPTS - 1) {
                    sleep(NOMINATIM_RETRY_BACKOFF_MS)
                    return@repeat
                }
                return FetchResult.Failure
            }

            if (response.statusCode == 200) {
                val candidate = try {
                    parseCandidate(response.body.orEmpty())
                } catch (_: JSONException) {
                    logFailure(requestId, attempt + 1, 200, "invalid_json")
                    if (attempt < NOMINATIM_MAX_ATTEMPTS - 1) {
                        sleep(NOMINATIM_RETRY_BACKOFF_MS)
                        return@repeat
                    }
                    return FetchResult.Failure
                }
                return FetchResult.Success(candidate)
            }

            val retryable = response.statusCode == 429 || response.statusCode in 500..599
            logFailure(
                requestId = requestId,
                attempt = attempt + 1,
                statusCode = response.statusCode,
                outcome = if (response.statusCode == 429) "rate_limited" else "http_error",
            )
            if (retryable && attempt < NOMINATIM_MAX_ATTEMPTS - 1) {
                val retryDelay = response.retryAfterMillis
                    ?.coerceIn(NOMINATIM_MIN_REQUEST_INTERVAL_MS, NOMINATIM_MAX_RETRY_AFTER_MS)
                    ?: NOMINATIM_RETRY_BACKOFF_MS
                sleep(retryDelay)
                return@repeat
            }
            return FetchResult.Failure
        }
        return FetchResult.Failure
    }

    private suspend fun awaitGlobalThrottle() {
        throttleMutex.withLock {
            val now = nowMillis()
            val previous = lastRequestStartedAtMillis
            if (previous != null) {
                val waitMillis = (previous + NOMINATIM_MIN_REQUEST_INTERVAL_MS - now).coerceAtLeast(0L)
                if (waitMillis > 0L) sleep(waitMillis)
            }
            lastRequestStartedAtMillis = nowMillis()
        }
    }

    private fun logFailure(
        requestId: String,
        attempt: Int,
        statusCode: Int?,
        outcome: String,
        cause: Throwable? = null,
    ) {
        Timber.w(
            cause,
            "Nominatim request failed endpoint=%s requestId=%s attempt=%d status=%s outcome=%s",
            NOMINATIM_ENDPOINT_HOST,
            requestId,
            attempt,
            statusCode?.toString() ?: "none",
            outcome,
        )
    }

    private fun parseCandidate(body: String): PrayerAddressCandidate? {
        val address = JSONObject(body).optJSONObject("address") ?: return null
        val country = address.optString("country").ifBlank { return null }
        val city = address.optString("state").ifBlank { address.optString("city") }
        val district = address.optString("county").ifBlank { address.optString("suburb") }
        return PrayerAddressCandidate(country = country, city = city, district = district)
    }

    private sealed interface LookupDecision {
        data class Cached(val candidate: PrayerAddressCandidate) : LookupDecision

        data class Await(val deferred: CompletableDeferred<PrayerAddressCandidate?>) : LookupDecision

        data class Fetch(
            val deferred: CompletableDeferred<PrayerAddressCandidate?>,
            val staleCandidate: PrayerAddressCandidate?,
        ) : LookupDecision
    }

    private sealed interface FetchResult {
        data class Success(val candidate: PrayerAddressCandidate?) : FetchResult

        data object Failure : FetchResult
    }

    private data class CacheEntry(
        val candidate: PrayerAddressCandidate,
        val storedAtMillis: Long,
    ) {
        fun ageMillis(nowMillis: Long): Long = (nowMillis - storedAtMillis).coerceAtLeast(0L)
    }

    private data class ApproximateLocationKey(
        val latitudeE3: Int,
        val longitudeE3: Int,
    ) {
        val latitude: Double get() = latitudeE3.toDouble() / NOMINATIM_COORDINATE_SCALE
        val longitude: Double get() = longitudeE3.toDouble() / NOMINATIM_COORDINATE_SCALE

        companion object {
            fun from(latitude: Double, longitude: Double): ApproximateLocationKey? {
                if (!latitude.isFinite() || !longitude.isFinite()) return null
                if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
                return ApproximateLocationKey(
                    latitudeE3 = (latitude * NOMINATIM_COORDINATE_SCALE).roundToInt(),
                    longitudeE3 = (longitude * NOMINATIM_COORDINATE_SCALE).roundToInt(),
                )
            }
        }
    }

    private companion object {
        val REQUEST_SEQUENCE = AtomicLong()
    }
}

interface NominatimTransport {
    suspend fun reverse(latitude: Double, longitude: Double): NominatimHttpResult
}

data class NominatimHttpResult(
    val statusCode: Int,
    val body: String?,
    val retryAfterMillis: Long?,
)

class NominatimHttpTransport @Inject constructor() : NominatimTransport {
    private var callFactory: Call.Factory = defaultClient()
    private var endpoint: HttpUrl = NOMINATIM_ENDPOINT.toHttpUrl()

    internal constructor(
        callFactory: Call.Factory,
        endpoint: HttpUrl,
    ) : this() {
        this.callFactory = callFactory
        this.endpoint = endpoint
    }

    override suspend fun reverse(latitude: Double, longitude: Double): NominatimHttpResult {
        val url = endpoint.newBuilder()
            .addQueryParameter("format", "json")
            .addQueryParameter("lat", latitude.toString())
            .addQueryParameter("lon", longitude.toString())
            .addQueryParameter("zoom", NOMINATIM_ZOOM_LEVEL.toString())
            .addQueryParameter("addressdetails", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NOMINATIM_USER_AGENT)
            .header("Accept-Language", "tr,en")
            .get()
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = callFactory.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, error: IOException) {
                        continuation.resumeWithException(error)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            runCatching { response.toNominatimResult() }.fold(
                                onSuccess = { result ->
                                    continuation.resume(result) { _, _, _ -> }
                                },
                                onFailure = continuation::resumeWithException,
                            )
                        }
                    }
                },
            )
        }
    }

    private fun Response.toNominatimResult(): NominatimHttpResult = NominatimHttpResult(
        statusCode = code,
        body = if (isSuccessful) body?.string() else null,
        retryAfterMillis = header("Retry-After")
            ?.trim()
            ?.toLongOrNull()
            ?.coerceIn(1L, NOMINATIM_MAX_RETRY_AFTER_SECONDS)
            ?.times(1_000L),
    )

    private companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(NOMINATIM_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(NOMINATIM_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }
}

internal const val NOMINATIM_FRESH_CACHE_TTL_MS = 24L * 60 * 60 * 1_000
internal const val NOMINATIM_STALE_FALLBACK_TTL_MS = 7L * 24 * 60 * 60 * 1_000
private const val NOMINATIM_MAX_CACHE_ENTRIES = 32
private const val NOMINATIM_MAX_ATTEMPTS = 2
private const val NOMINATIM_MIN_REQUEST_INTERVAL_MS = 1_000L
private const val NOMINATIM_RETRY_BACKOFF_MS = 1_000L
private const val NOMINATIM_MAX_RETRY_AFTER_SECONDS = 60L
private const val NOMINATIM_MAX_RETRY_AFTER_MS = NOMINATIM_MAX_RETRY_AFTER_SECONDS * 1_000L
private const val NOMINATIM_COORDINATE_SCALE = 1_000
private const val NOMINATIM_CONNECT_TIMEOUT_MS = 10_000L
private const val NOMINATIM_READ_TIMEOUT_MS = 10_000L
private const val NOMINATIM_ZOOM_LEVEL = 10
private const val NOMINATIM_ENDPOINT = "https://nominatim.openstreetmap.org/reverse"
private const val NOMINATIM_ENDPOINT_HOST = "nominatim.openstreetmap.org"
private const val NOMINATIM_USER_AGENT =
    "ParsfiloContentApp/1.0 (Android; +https://parsfilo.com/privacy)"
