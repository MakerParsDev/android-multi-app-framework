package com.parsfilo.contentapp.core.common.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacySafeLogTest {
    @Test
    fun `production log policy suppresses info and forwards warning and error`() {
        assertFalse(shouldForwardToProductionCrashReporting(priority = 4))
        assertTrue(shouldForwardToProductionCrashReporting(priority = 5))
        assertTrue(shouldForwardToProductionCrashReporting(priority = 6))
    }

    @Test
    fun `sanitizer redacts email urls identifiers coordinates and file paths`() {
        val raw =
            "contact=user@example.com installationId=device-123 lat=41.0123 lon=28.9876 " +
                "url=https://example.com/path?token=secret cached at /data/user/0/app/cache/audio.mp3"

        val sanitized = sanitizeLogMessage(raw)

        assertFalse(sanitized.contains("user@example.com"))
        assertFalse(sanitized.contains("device-123"))
        assertFalse(sanitized.contains("41.0123"))
        assertFalse(sanitized.contains("28.9876"))
        assertFalse(sanitized.contains("https://example.com"))
        assertFalse(sanitized.contains("/data/user/0/app/cache/audio.mp3"))
        assertTrue(sanitized.contains(REDACTED_EMAIL))
        assertTrue(sanitized.contains(REDACTED_VALUE))
        assertTrue(sanitized.contains(REDACTED_URL))
        assertTrue(sanitized.contains(REDACTED_PATH))
    }

    @Test
    fun `sanitizer redacts quoted json sensitive values`() {
        val raw = "{\"fcmToken\":\"token-value\",\"displayName\":\"Sensitive Name\"}"

        val sanitized = sanitizeLogMessage(raw)

        assertFalse(sanitized.contains("token-value"))
        assertFalse(sanitized.contains("Sensitive Name"))
        assertEquals(2, Regex(Regex.escape(REDACTED_VALUE)).findAll(sanitized).count())
    }

    @Test
    fun `privacy safe throwable preserves sanitized cause types and stack traces`() {
        val cause = IllegalArgumentException("sensitive cause info")
        cause.stackTrace = arrayOf(StackTraceElement("Cause", "load", "Cause.kt", 7))
        val original = IllegalStateException("user@example.com token=secret", cause)
        original.stackTrace = arrayOf(StackTraceElement("Sample", "run", "Sample.kt", 42))

        val sanitized = original.toPrivacySafeThrowable()

        assertFalse(sanitized.message.orEmpty().contains("user@example.com"))
        assertFalse(sanitized.message.orEmpty().contains("secret"))
        assertEquals(original.stackTrace.toList(), sanitized.stackTrace.toList())
        assertTrue(sanitized.message.orEmpty().contains("IllegalStateException"))

        val sanitizedCause = sanitized.cause
        assertTrue(sanitizedCause != null)
        assertTrue(sanitizedCause?.message.orEmpty().contains("IllegalArgumentException"))
        assertFalse(sanitizedCause?.message.orEmpty().contains("sensitive cause info"))
        assertEquals(cause.stackTrace.toList(), sanitizedCause?.stackTrace?.toList())
    }
}
