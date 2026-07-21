package com.parsfilo.contentapp.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RemoteUpdateConfigMapperTest {
    @Test
    fun `tr locale prefers turkish text`() {
        val result =
            resolveLocalizedRemoteText(
                languageCode = "tr",
                trValue = "Türkçe",
                enValue = "English",
                fallback = "Fallback",
            )

        assertEquals("Türkçe", result)
    }

    @Test
    fun `non-tr locale prefers english text`() {
        val result =
            resolveLocalizedRemoteText(
                languageCode = "en",
                trValue = "Türkçe",
                enValue = "English",
                fallback = "Fallback",
            )

        assertEquals("English", result)
    }

    @Test
    fun `blank remote values fall back safely`() {
        val result =
            resolveLocalizedRemoteText(
                languageCode = "tr",
                trValue = "   ",
                enValue = "",
                fallback = "Fallback",
            )

        assertEquals("Fallback", result)
    }

    @Test
    fun `blank preferred text falls back to secondary language`() {
        val result =
            resolveLocalizedRemoteText(
                languageCode = "tr",
                trValue = "   ",
                enValue = "  English fallback  ",
                fallback = "Fallback",
            )

        assertEquals("English fallback", result)
    }

    @Test
    fun `negative remote version code is coerced to one`() {
        assertEquals(1L, coerceRemoteVersionCode(-5))
    }

    @Test
    fun `zero remote version code is coerced to one`() {
        assertEquals(1L, coerceRemoteVersionCode(0))
    }

    @Test
    fun `positive remote version code is preserved`() {
        assertEquals(42L, coerceRemoteVersionCode(42))
    }

    @Test
    fun `remote config key registry contains safe defaults for every key`() {
        val defaults = RemoteUpdateConfigKeys.defaults(currentVersionCode = 42)
        assertEquals(RemoteUpdateConfigKeys.allKeys.toSet(), defaults.keys)
        assertFalse(defaults.getValue(RemoteUpdateConfigKeys.UPDATES_ENABLED) as Boolean)
        assertEquals(42L, defaults.getValue(RemoteUpdateConfigKeys.MIN_SUPPORTED_VERSION_CODE))
        assertEquals(42L, defaults.getValue(RemoteUpdateConfigKeys.LATEST_VERSION_CODE))
    }
}
