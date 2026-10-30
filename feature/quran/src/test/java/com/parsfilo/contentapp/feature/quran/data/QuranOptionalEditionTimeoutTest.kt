package com.parsfilo.contentapp.feature.quran.data

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.database.model.quran.QuranAyahEntity
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class QuranOptionalEditionTimeoutTest {
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
    fun optionalCallHasBoundedTotalDeadlineWithoutChangingCoreClientDeadline() {
        val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
        val request = Request.Builder().url(server.url("/arabic")).build()
        val required = newQuranEditionCall(client, request, timeoutMillis = null)
        val optional = newQuranEditionCall(client, request, timeoutMillis = OPTIONAL_EDITION_TIMEOUT_MS)

        assertThat(required.timeout().timeoutNanos()).isEqualTo(0L)
        assertThat(optional.timeout().timeoutNanos()).isEqualTo(TimeUnit.SECONDS.toNanos(8L))
        assertThat(client.readTimeoutMillis).isEqualTo(30_000)
    }

    @Test
    fun slowOptionalEditionTimesOutWithoutErasingCachedCoreVerses() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeadersDelay(300, TimeUnit.MILLISECONDS)
                .setBody("{}"),
        )
        val request = Request.Builder().url(server.url("/slow-optional")).build()
        val result = loadOptionalQuranEdition("eng-abdelhaleem", 1) {
            newQuranEditionCall(OkHttpClient(), request, timeoutMillis = 80)
                .execute().use { response ->
                    response.body.string()
                    emptyMap()
                }
        }
        assertThat(result).isEmpty()

        val cached = listOf(
            QuranAyahEntity(
                suraNumber = 1,
                ayahNumber = 1,
                arabic = "Arabic 1",
                latin = "Cached Latin",
                turkish = "Turkish 1",
                english = "",
                german = "Cached German",
            ),
        )
        val healed = mergeOptionalEditionContent(cached, emptyMap(), result, emptyMap())
        assertThat(healed).containsExactlyElementsIn(cached)
        assertThat(healed.single().needsOptionalEditionRefresh()).isTrue()
    }

    @Test
    fun laterSuccessfulRetryFillsMissingOptionalWithoutChangingCore() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("English 1"))
        val request = Request.Builder().url(server.url("/optional-retry")).build()
        val retry = loadOptionalQuranEdition("eng-abdelhaleem", 1) {
            newQuranEditionCall(OkHttpClient(), request, timeoutMillis = 1_000)
                .execute().use { response -> mapOf(1 to response.body.string()) }
        }
        val existing = listOf(
            QuranAyahEntity(1, 1, "Arabic 1", "Cached Latin", "Turkish 1", "", "Cached German"),
        )
        val updated = mergeOptionalEditionContent(existing, emptyMap(), retry, emptyMap())

        assertThat(updated.single().english).isEqualTo("English 1")
        assertThat(updated.single().arabic).isEqualTo("Arabic 1")
        assertThat(updated.single().turkish).isEqualTo("Turkish 1")
        assertThat(updated.single().latin).isEqualTo("Cached Latin")
        assertThat(updated.single().german).isEqualTo("Cached German")
        assertThat(updated.single().needsOptionalEditionRefresh()).isFalse()
    }

    @Test
    fun timedOutCoreRequestIsNotSilentlyAcceptedAsMissingArabic() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeadersDelay(300, TimeUnit.MILLISECONDS)
                .setBody("{}"),
        )
        val request = Request.Builder().url(server.url("/required")).build()
        assertThrows(IOException::class.java) {
            newQuranEditionCall(OkHttpClient(), request, timeoutMillis = 80)
                .execute().close()
        }
        assertThrows(IllegalStateException::class.java) {
            buildValidatedSuraAyahEntities(
                1,
                1,
                emptyMap(),
                mapOf(1 to "TR"),
                emptyMap(),
                emptyMap(),
                emptyMap(),
            )
        }
    }
}
