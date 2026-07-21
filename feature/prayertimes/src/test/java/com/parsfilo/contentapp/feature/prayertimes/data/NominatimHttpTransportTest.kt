package com.parsfilo.contentapp.feature.prayertimes.data

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

class NominatimHttpTransportTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `request uses privacy-rounded coordinates and identifying headers`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val transport = NominatimHttpTransport(
            callFactory = OkHttpClient(),
            endpoint = server.url("/reverse"),
        )

        val response = transport.reverse(latitude = 41.012, longitude = 28.988)
        val request = server.takeRequest()

        assertThat(response.statusCode).isEqualTo(200)
        assertThat(request.requestUrl?.encodedPath).isEqualTo("/reverse")
        assertThat(request.requestUrl?.queryParameter("lat")).isEqualTo("41.012")
        assertThat(request.requestUrl?.queryParameter("lon")).isEqualTo("28.988")
        assertThat(request.requestUrl?.queryParameter("zoom")).isEqualTo("10")
        assertThat(request.getHeader("User-Agent")).contains("ParsfiloContentApp")
        assertThat(request.getHeader("User-Agent")).contains("parsfilo.com/privacy")
        assertThat(request.getHeader("Accept-Language")).isEqualTo("tr,en")
    }

    @Test
    fun `cancelling coroutine cancels active OkHttp call`() = runTest {
        val callFactory = mockk<Call.Factory>()
        val call = mockk<Call>(relaxed = true)
        var callback: Callback? = null
        every { callFactory.newCall(any()) } returns call
        every { call.enqueue(any()) } answers {
            callback = firstArg()
            Unit
        }
        val transport = NominatimHttpTransport(
            callFactory = callFactory,
            endpoint = server.url("/reverse"),
        )

        val request = async { transport.reverse(latitude = 41.012, longitude = 28.988) }
        yield()
        assertThat(callback).isNotNull()

        request.cancel()
        request.join()
        callback?.onFailure(call, IOException("Canceled"))

        verify(exactly = 1) { call.cancel() }
    }

    @Test
    fun `429 response exposes bounded retry-after without retaining response body`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "2")
                .setBody("sensitive upstream body"),
        )
        val transport = NominatimHttpTransport(
            callFactory = OkHttpClient(),
            endpoint = server.url("/reverse"),
        )

        val response = transport.reverse(latitude = 41.012, longitude = 28.988)

        assertThat(response.statusCode).isEqualTo(429)
        assertThat(response.retryAfterMillis).isEqualTo(2_000L)
        assertThat(response.body).isNull()
    }
}
