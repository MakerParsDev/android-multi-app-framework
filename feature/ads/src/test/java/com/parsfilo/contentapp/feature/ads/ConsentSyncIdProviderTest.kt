package com.parsfilo.contentapp.feature.ads

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConsentSyncIdProviderTest {

    @Test
    fun `validator accepts UUID style app set id`() {
        assertThat(
            ConsentSyncIdValidator.isValid("123e4567-e89b-12d3-a456-426614174000"),
        ).isTrue()
    }

    @Test
    fun `validator accepts documented character set within length limits`() {
        assertThat(
            ConsentSyncIdValidator.isValid("12JD92JD8078S8J29SDOAKC0EF230337+/=$,{}"),
        ).isTrue()
    }

    @Test
    fun `validator rejects blank and too short identifiers`() {
        assertThat(ConsentSyncIdValidator.isValid("")).isFalse()
        assertThat(ConsentSyncIdValidator.isValid("short-sync-id")).isFalse()
    }

    @Test
    fun `validator rejects overly long identifiers`() {
        val tooLong = "a".repeat(151)

        assertThat(ConsentSyncIdValidator.isValid(tooLong)).isFalse()
    }

    @Test
    fun `validator trims surrounding whitespace and rejects embedded whitespace or disallowed characters`() {
        assertThat(
            ConsentSyncIdValidator.isValid("123e4567-e89b-12d3-a456-426614174000 "),
        ).isTrue()
        assertThat(
            ConsentSyncIdValidator.isValid("123e4567-e89b-12d3-a456-426614174000!"),
        ).isFalse()
        assertThat(
            ConsentSyncIdValidator.isValid("123e4567 e89b 12d3 a456 426614174000"),
        ).isFalse()
    }
}
