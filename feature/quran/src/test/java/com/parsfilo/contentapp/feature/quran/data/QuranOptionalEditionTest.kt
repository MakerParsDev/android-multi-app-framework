package com.parsfilo.contentapp.feature.quran.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.SocketTimeoutException

class QuranOptionalEditionTest {
    @Test
    fun `optional timeout returns empty edition without failing core sync`() = runTest {
        val edition = loadOptionalQuranEdition("eng-abdelhaleem", 1) {
            throw SocketTimeoutException("timed out")
        }
        assertThat(edition).isEmpty()
    }

    @Test
    fun `optional edition keeps a successful verse map`() = runTest {
        val expected = mapOf(1 to "EN1", 2 to "EN2")
        val edition = loadOptionalQuranEdition("eng-abdelhaleem", 1) { expected }
        assertThat(edition).isEqualTo(expected)
    }

    private fun cachedVerse(number: Int, latin: String, english: String, german: String) =
        com.parsfilo.contentapp.core.database.model.quran.QuranAyahEntity(
            suraNumber = 1,
            ayahNumber = number,
            arabic = "AR$number",
            latin = latin,
            turkish = "TR$number",
            english = english,
            german = german,
        )

    @Test
    fun missingOptionalEditionsAreEligibleForLaterRetry() {
        assertThat(cachedVerse(1, "", "EN", "DE").needsOptionalEditionRefresh()).isTrue()
        assertThat(cachedVerse(2, "LAT", "EN", "DE").needsOptionalEditionRefresh()).isFalse()
    }

    @Test
    fun optionalRetryFillsGapsByVerseWithoutChangingValidatedCoreContent() {
        val original = listOf(cachedVerse(1, "", "", "DE1"), cachedVerse(2, "LAT2", "EN2", "DE2"))
        val updated = mergeOptionalEditionContent(
            original,
            latin = mapOf(1 to "LAT1", 2 to "NEW_LAT2", 99 to "INVALID"),
            english = mapOf(1 to "EN1"),
            german = emptyMap(),
        )
        assertThat(updated.map { it.arabic }).containsExactly("AR1", "AR2").inOrder()
        assertThat(updated.map { it.turkish }).containsExactly("TR1", "TR2").inOrder()
        assertThat(updated[0].latin).isEqualTo("LAT1")
        assertThat(updated[0].english).isEqualTo("EN1")
        assertThat(updated[0].german).isEqualTo("DE1")
        assertThat(updated[1].latin).isEqualTo("LAT2")
        assertThat(updated[1].ayahNumber).isEqualTo(2)
    }

    @Test
    fun forcedReloadPreservesCachedOptionalTextIfNetworkEditionFails() {
        val cached = listOf(cachedVerse(1, "LAT1", "EN1", "DE1"))
        val fresh = listOf(cachedVerse(1, "", "", ""))
        val repaired = preserveCachedOptionalEditionContent(fresh, cached)
        assertThat(repaired).isEqualTo(cached)
    }

    @Test
    fun `optional edition propagates coroutine cancellation`() {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                loadOptionalQuranEdition("deu-aburidamuhammad", 1) {
                    throw CancellationException("cancelled")
                }
            }
        }
    }
}
