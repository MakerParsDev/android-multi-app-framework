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
