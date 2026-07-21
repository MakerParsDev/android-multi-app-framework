package com.parsfilo.contentapp.core.firebase.push

import kotlinx.coroutines.CancellationException
import java.io.IOException
import kotlin.random.Random

internal const val MAX_RETRY_COUNT = 1
private const val RETRY_DELAY_MIN_MS = 300L
private const val RETRY_DELAY_MAX_MS = 900L

internal fun shouldRetryPushRegistration(
    attempt: Int,
    statusCode: Int?,
    throwable: Throwable?,
): Boolean {
    if (attempt >= MAX_RETRY_COUNT) return false
    if (throwable is CancellationException) return false
    if (throwable != null) return throwable is IOException
    return statusCode != null && statusCode in 500..599
}

internal fun nextRetryDelayMillis(random: Random = Random.Default): Long =
    random.nextLong(from = RETRY_DELAY_MIN_MS, until = RETRY_DELAY_MAX_MS + 1)
