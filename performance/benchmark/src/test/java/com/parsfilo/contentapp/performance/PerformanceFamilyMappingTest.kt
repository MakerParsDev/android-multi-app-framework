package com.parsfilo.contentapp.performance

import org.junit.Assert.assertEquals
import org.junit.Test

class PerformanceFamilyMappingTest {

    @Test
    fun `content family maps to AUDIO_CONTENT`() {
        assertEquals(PerformanceFamily.AUDIO_CONTENT, PerformanceFamily.from("content"))
    }

    @Test
    fun `prayer_library family maps to AUDIO_CONTENT`() {
        assertEquals(PerformanceFamily.AUDIO_CONTENT, PerformanceFamily.from("prayer_library"))
    }

    @Test
    fun `esma family maps to ESMA`() {
        assertEquals(PerformanceFamily.ESMA, PerformanceFamily.from("esma"))
    }

    @Test
    fun `miracles family maps to MIRACLES`() {
        assertEquals(PerformanceFamily.MIRACLES, PerformanceFamily.from("miracles"))
    }

    @Test
    fun `quran family maps to QURAN`() {
        assertEquals(PerformanceFamily.QURAN, PerformanceFamily.from("quran"))
    }

    @Test
    fun `prayer_times family maps to PRAYER_TIMES`() {
        assertEquals(PerformanceFamily.PRAYER_TIMES, PerformanceFamily.from("prayer_times"))
    }

    @Test
    fun `qibla family maps to QIBLA`() {
        assertEquals(PerformanceFamily.QIBLA, PerformanceFamily.from("qibla"))
    }

    @Test
    fun `zikir_counter family maps to COUNTER`() {
        assertEquals(PerformanceFamily.COUNTER, PerformanceFamily.from("zikir_counter"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown family throws exception`() {
        PerformanceFamily.from("unknown_family")
    }
}