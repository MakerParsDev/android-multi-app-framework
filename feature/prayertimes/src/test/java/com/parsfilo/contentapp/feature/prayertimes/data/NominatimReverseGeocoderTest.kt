package com.parsfilo.contentapp.feature.prayertimes.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import java.io.IOException

class NominatimReverseGeocoderTest {
    @Test
    fun `nearby coordinates share rounded cache key and one network request`() = runTest {
        val clock = FakeNominatimClock()
        val transport = ScriptedNominatimTransport(clock).apply {
            enqueue(successResponse())
        }
        val geocoder = testGeocoder(transport, clock, backgroundScope)

        val first = geocoder.reverse(latitude = 41.01234, longitude = 28.98765)
        val second = geocoder.reverse(latitude = 41.01236, longitude = 28.98764)

        assertThat(first).isEqualTo(PRAYER_ADDRESS)
        assertThat(second).isEqualTo(PRAYER_ADDRESS)
        assertThat(transport.requests).hasSize(1)
        assertThat(transport.requests.single().latitude).isEqualTo(41.012)
        assertThat(transport.requests.single().longitude).isEqualTo(28.988)
    }

    @Test
    fun `different locations are globally throttled to one request per second`() = runTest {
        val clock = FakeNominatimClock()
        val transport = ScriptedNominatimTransport(clock).apply {
            enqueue(successResponse())
            enqueue(successResponse())
        }
        val geocoder = testGeocoder(transport, clock, backgroundScope)

        geocoder.reverse(41.012, 28.988)
        geocoder.reverse(40.990, 29.020)

        assertThat(transport.requests.map { it.startedAtMillis }).containsExactly(0L, 1_000L).inOrder()
        assertThat(clock.delays).containsExactly(1_000L)
    }

    @Test
    fun `429 retry-after is honored before one retry`() = runTest {
        val clock = FakeNominatimClock()
        val transport = ScriptedNominatimTransport(clock).apply {
            enqueue(NominatimHttpResult(statusCode = 429, body = null, retryAfterMillis = 2_000L))
            enqueue(successResponse())
        }
        val geocoder = testGeocoder(transport, clock, backgroundScope)

        val result = geocoder.reverse(41.012, 28.988)

        assertThat(result).isEqualTo(PRAYER_ADDRESS)
        assertThat(transport.requests.map { it.startedAtMillis }).containsExactly(0L, 2_000L).inOrder()
        assertThat(clock.delays).containsExactly(2_000L)
    }

    @Test
    fun `server failure retries once with policy backoff`() = runTest {
        val clock = FakeNominatimClock()
        val transport = ScriptedNominatimTransport(clock).apply {
            enqueue(NominatimHttpResult(statusCode = 503, body = null, retryAfterMillis = null))
            enqueue(successResponse())
        }
        val geocoder = testGeocoder(transport, clock, backgroundScope)

        val result = geocoder.reverse(41.012, 28.988)

        assertThat(result).isEqualTo(PRAYER_ADDRESS)
        assertThat(transport.requests).hasSize(2)
        assertThat(clock.delays).containsExactly(1_000L)
    }

    @Test
    fun `offline failure serves stale cache for seven day fallback window`() = runTest {
        val clock = FakeNominatimClock()
        val transport = ScriptedNominatimTransport(clock).apply {
            enqueue(successResponse())
        }
        val geocoder = testGeocoder(transport, clock, backgroundScope)
        assertThat(geocoder.reverse(41.012, 28.988)).isEqualTo(PRAYER_ADDRESS)
        clock.advanceBy(NOMINATIM_FRESH_CACHE_TTL_MS + 1)
        transport.enqueue(IOException("offline"))
        transport.enqueue(IOException("offline"))

        val result = geocoder.reverse(41.012, 28.988)

        assertThat(result).isEqualTo(PRAYER_ADDRESS)
        assertThat(transport.requests).hasSize(3)
    }

    @Test
    fun `expired stale cache is not returned after seven days`() = runTest {
        val clock = FakeNominatimClock()
        val transport = ScriptedNominatimTransport(clock).apply {
            enqueue(successResponse())
        }
        val geocoder = testGeocoder(transport, clock, backgroundScope)
        assertThat(geocoder.reverse(41.012, 28.988)).isEqualTo(PRAYER_ADDRESS)
        clock.advanceBy(NOMINATIM_STALE_FALLBACK_TTL_MS + 1)
        transport.enqueue(IOException("offline"))
        transport.enqueue(IOException("offline"))

        val result = geocoder.reverse(41.012, 28.988)

        assertThat(result).isNull()
    }

    @Test
    fun `concurrent requests for the same rounded location are coalesced`() = runTest {
        val clock = FakeNominatimClock()
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val transport = object : NominatimTransport {
            var requestCount = 0

            override suspend fun reverse(latitude: Double, longitude: Double): NominatimHttpResult {
                requestCount += 1
                requestStarted.complete(Unit)
                releaseResponse.await()
                return successResponse()
            }
        }
        val geocoder = testGeocoder(transport, clock, backgroundScope)

        val first = async { geocoder.reverse(41.01234, 28.98765) }
        requestStarted.await()
        val second = async { geocoder.reverse(41.01236, 28.98764) }
        yield()
        releaseResponse.complete(Unit)

        assertThat(first.await()).isEqualTo(PRAYER_ADDRESS)
        assertThat(second.await()).isEqualTo(PRAYER_ADDRESS)
        assertThat(transport.requestCount).isEqualTo(1)
    }

    @Test
    fun `cancelling first caller does not cancel shared lookup for another caller`() = runTest {
        val clock = FakeNominatimClock()
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val transport = object : NominatimTransport {
            var requestCount = 0

            override suspend fun reverse(latitude: Double, longitude: Double): NominatimHttpResult {
                requestCount += 1
                requestStarted.complete(Unit)
                releaseResponse.await()
                return successResponse()
            }
        }
        val geocoder = testGeocoder(transport, clock, backgroundScope)

        val first = async { geocoder.reverse(41.01234, 28.98765) }
        requestStarted.await()
        val second = async { geocoder.reverse(41.01236, 28.98764) }
        yield()

        first.cancel()
        releaseResponse.complete(Unit)

        assertThat(first.isCancelled).isTrue()
        assertThat(second.await()).isEqualTo(PRAYER_ADDRESS)
        assertThat(transport.requestCount).isEqualTo(1)
    }

    @Test
    fun `invalid coordinates do not reach third party transport`() = runTest {
        val clock = FakeNominatimClock()
        val transport = ScriptedNominatimTransport(clock)
        val geocoder = testGeocoder(transport, clock, backgroundScope)

        assertThat(geocoder.reverse(Double.NaN, 28.9)).isNull()
        assertThat(geocoder.reverse(91.0, 28.9)).isNull()
        assertThat(transport.requests).isEmpty()
    }

    private fun testGeocoder(
        transport: NominatimTransport,
        clock: FakeNominatimClock,
        fetchScope: CoroutineScope,
    ) = NominatimReverseGeocoder(
        transport = transport,
        nowMillis = clock::nowMillis,
        sleep = clock::sleep,
        requestIdFactory = { "test-request" },
        fetchScope = fetchScope,
    )

    private class FakeNominatimClock {
        private var currentMillis = 0L
        val delays = mutableListOf<Long>()

        fun nowMillis(): Long = currentMillis

        suspend fun sleep(delayMillis: Long) {
            delays += delayMillis
            currentMillis += delayMillis
        }

        fun advanceBy(millis: Long) {
            currentMillis += millis
        }
    }

    private class ScriptedNominatimTransport(
        private val clock: FakeNominatimClock,
    ) : NominatimTransport {
        val requests = mutableListOf<RequestRecord>()
        private val outcomes = ArrayDeque<Any>()

        fun enqueue(outcome: Any) {
            outcomes.addLast(outcome)
        }

        override suspend fun reverse(latitude: Double, longitude: Double): NominatimHttpResult {
            requests += RequestRecord(latitude, longitude, clock.nowMillis())
            return when (val outcome = outcomes.removeFirst()) {
                is NominatimHttpResult -> outcome
                is IOException -> throw outcome
                else -> error("Unsupported test outcome: $outcome")
            }
        }
    }

    private data class RequestRecord(
        val latitude: Double,
        val longitude: Double,
        val startedAtMillis: Long,
    )

    private companion object {
        val PRAYER_ADDRESS = PrayerAddressCandidate(
            country = "Türkiye",
            city = "İstanbul",
            district = "Kadıköy",
        )

        fun successResponse() = NominatimHttpResult(
            statusCode = 200,
            body = """{"address":{"country":"Türkiye","state":"İstanbul","county":"Kadıköy"}}""",
            retryAfterMillis = null,
        )
    }
}
