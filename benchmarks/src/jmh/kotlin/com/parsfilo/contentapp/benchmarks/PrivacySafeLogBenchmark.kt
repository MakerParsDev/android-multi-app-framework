package com.parsfilo.contentapp.benchmarks

import com.parsfilo.contentapp.core.common.logging.sanitizeLogMessage
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

@State(Scope.Benchmark)
open class PrivacySafeLogBenchmark {
    private val plainMessage = "Prayer times refreshed for district 9541 in 182 ms (cache hit = false)"

    private val sensitiveMessage =
        "Sync failed for email=user.name@example.com token=placeholder " +
            "url https://api.example.com/v1/prayer?lat=41.0082&lon=28.9784 " +
            "while writing /data/user/0/com.parsfilo.contentapp/files/cache/prayer.json " +
            "{\"installationId\":\"f1e2d3c4\",\"fcmToken\":'xyz-987'}"

    private val longMessage = (1..20).joinToString(separator = " | ") { sensitiveMessage }

    @Benchmark
    fun sanitizePlain(): String = sanitizeLogMessage(plainMessage)

    @Benchmark
    fun sanitizeSensitive(): String = sanitizeLogMessage(sensitiveMessage)

    @Benchmark
    fun sanitizeLong(): String = sanitizeLogMessage(longMessage)
}
