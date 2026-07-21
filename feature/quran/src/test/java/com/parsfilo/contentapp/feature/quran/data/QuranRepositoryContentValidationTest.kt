package com.parsfilo.contentapp.feature.quran.data

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class QuranRepositoryContentValidationTest {

    @Test
    fun `arabic and turkish complete with optional empty returns entities`() {
        val result =
            buildValidatedSuraAyahEntities(
                suraNumber = 1,
                expectedAyahCount = 2,
                arabic = mapOf(2 to "AR2", 1 to "AR1"),
                turkish = mapOf(1 to "TR1", 2 to "TR2"),
                latin = emptyMap(),
                english = emptyMap(),
                german = emptyMap(),
            )

        assertThat(result).hasSize(2)
        assertThat(result.map { it.ayahNumber }).containsExactly(1, 2).inOrder()
        assertThat(result[0].arabic).isEqualTo("AR1")
        assertThat(result[0].turkish).isEqualTo("TR1")
        assertThat(result[0].latin).isEmpty()
    }

    @Test
    fun `arabic empty throws`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                buildValidatedSuraAyahEntities(
                    suraNumber = 2,
                    expectedAyahCount = 1,
                    arabic = emptyMap(),
                    turkish = mapOf(1 to "TR1"),
                    latin = emptyMap(),
                    english = emptyMap(),
                    german = emptyMap(),
                )
            }

        assertThat(error).hasMessageThat().contains("Arabic ayah payload missing")
    }

    @Test
    fun `missing turkish ayah for arabic key throws`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                buildValidatedSuraAyahEntities(
                    suraNumber = 3,
                    expectedAyahCount = 2,
                    arabic = mapOf(1 to "AR1", 2 to "AR2"),
                    turkish = mapOf(1 to "TR1"),
                    latin = emptyMap(),
                    english = emptyMap(),
                    german = emptyMap(),
                )
            }

        assertThat(error).hasMessageThat().contains("Turkish ayah payload incomplete")
    }

    @Test
    fun `expected count mismatch throws`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                buildValidatedSuraAyahEntities(
                    suraNumber = 4,
                    expectedAyahCount = 3,
                    arabic = mapOf(1 to "AR1", 2 to "AR2"),
                    turkish = mapOf(1 to "TR1", 2 to "TR2"),
                    latin = emptyMap(),
                    english = emptyMap(),
                    german = emptyMap(),
                )
            }

        assertThat(error).hasMessageThat().contains("expected=3 actual=2")
    }

    @Test
    fun `optional editions may be partial when required complete`() {
        val result =
            buildValidatedSuraAyahEntities(
                suraNumber = 5,
                expectedAyahCount = 2,
                arabic = mapOf(1 to "AR1", 2 to "AR2"),
                turkish = mapOf(1 to "TR1", 2 to "TR2"),
                latin = mapOf(2 to "LAT2"),
                english = mapOf(1 to "EN1"),
                german = emptyMap(),
            )

        assertThat(result).hasSize(2)
        assertThat(result[0].latin).isEmpty()
        assertThat(result[0].english).isEqualTo("EN1")
        assertThat(result[1].latin).isEqualTo("LAT2")
    }

    @Test
    fun `extra optional keys outside arabic canonical set are ignored`() {
        val result =
            buildValidatedSuraAyahEntities(
                suraNumber = 6,
                expectedAyahCount = 2,
                arabic = mapOf(1 to "AR1", 2 to "AR2"),
                turkish = mapOf(1 to "TR1", 2 to "TR2"),
                latin = mapOf(99 to "LAT99"),
                english = emptyMap(),
                german = emptyMap(),
            )

        assertThat(result.map { it.ayahNumber }).containsExactly(1, 2).inOrder()
    }
}
