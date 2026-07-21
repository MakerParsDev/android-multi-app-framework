package com.parsfilo.contentapp.core.common.logging

import java.util.Collections
import java.util.IdentityHashMap

const val REDACTED_EMAIL = "[REDACTED_EMAIL]"
const val REDACTED_VALUE = "[REDACTED]"
const val REDACTED_URL = "[REDACTED_URL]"
const val REDACTED_PATH = "[REDACTED_PATH]"

private const val ANDROID_LOG_WARN_PRIORITY = 5

private val urlPattern = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
private val emailPattern =
    Regex(
        """\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""",
        RegexOption.IGNORE_CASE,
    )
private val sensitiveAssignmentPattern =
    Regex(
        """(?i)(["']?(?:installationId|fcmToken|purchaseToken|idToken|credentialId|email|displayName|lat|lon|latitude|longitude|filePath|absolutePath|remoteUrl|token)["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[^,\s&}\n]+)""",
    )
private val absolutePathPattern = Regex("""(?<![A-Za-z0-9])/(?:[^/\s]+/)+[^/\s]+""")

fun shouldForwardToProductionCrashReporting(priority: Int): Boolean =
    priority >= ANDROID_LOG_WARN_PRIORITY

fun sanitizeLogMessage(raw: String): String =
    raw
        .replace(urlPattern, REDACTED_URL)
        .replace(emailPattern, REDACTED_EMAIL)
        .replace(sensitiveAssignmentPattern) { match ->
            "${match.groupValues[1]}$REDACTED_VALUE"
        }.replace(absolutePathPattern, REDACTED_PATH)

fun Throwable.toPrivacySafeThrowable(): Throwable =
    toPrivacySafeThrowable(
        visited = Collections.newSetFromMap(IdentityHashMap()),
    )

private fun Throwable.toPrivacySafeThrowable(visited: MutableSet<Throwable>): Throwable {
    val sanitizedCause =
        if (visited.add(this)) {
            cause?.toPrivacySafeThrowable(visited)
        } else {
            null
        }
    return PrivacySafeLoggedException(this::class.java.name, sanitizedCause).also { sanitized ->
        sanitized.stackTrace = stackTrace
    }
}

private class PrivacySafeLoggedException(
    errorType: String,
    cause: Throwable?,
) : RuntimeException("Redacted exception: $errorType", cause)
