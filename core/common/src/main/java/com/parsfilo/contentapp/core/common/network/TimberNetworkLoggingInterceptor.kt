package com.parsfilo.contentapp.core.common.network

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Lightweight OkHttp logging interceptor for debug observability.
 * Logs only metadata (method/url/status/duration) to avoid payload leaks.
 */
class TimberNetworkLoggingInterceptor(
    private val source: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startNs = System.nanoTime()
        val method = request.method
        val host = request.url.host
        Timber.d(
            "[Net][%s] -> %s %s",
            source,
            method,
            host,
        )

        return runCatching {
            chain.proceed(request)
        }.fold(
            onSuccess = { response ->
                val durationMs = ((System.nanoTime() - startNs) / 1_000_000.0).roundToInt()
                Timber.d(
                    "[Net][%s] <- %s %s code=%d success=%s durationMs=%d",
                    source,
                    method,
                    host,
                    response.code,
                    response.isSuccessful,
                    durationMs,
                )
                response
            },
            onFailure = { error ->
                val durationMs = ((System.nanoTime() - startNs) / 1_000_000.0).roundToInt()
                Timber.w(
                    error,
                    "[Net][%s] xx %s %s durationMs=%d",
                    source,
                    method,
                    host,
                    durationMs,
                )
                throw error
            },
        )
    }
}
