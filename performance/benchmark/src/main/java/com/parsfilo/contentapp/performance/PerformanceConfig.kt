package com.parsfilo.contentapp.performance

import androidx.test.platform.app.InstrumentationRegistry

internal enum class PerformanceFamily {
    AUDIO_CONTENT,
    QURAN,
    MIRACLES,
    PRAYER_TIMES,
    QIBLA,
    COUNTER;

    companion object {
        fun from(raw: String): PerformanceFamily =
            when (raw) {
                "content", "esma", "prayer_library" -> AUDIO_CONTENT
                "quran" -> QURAN
                "miracles" -> MIRACLES
                "prayer_times" -> PRAYER_TIMES
                "qibla" -> QIBLA
                "zikir_counter" -> COUNTER
                else -> throw IllegalArgumentException("Unsupported performance family: $raw")
            }
    }
}

internal data class PerformanceConfig(
    val packageName: String,
    val flavor: String,
    val family: PerformanceFamily,
    val iterations: Int,
) {
    companion object {
        fun current(): PerformanceConfig {
            val args = InstrumentationRegistry.getArguments()
            val flavor =
                requireNotNull(args.getString("performanceFlavor")) {
                    "Missing performanceFlavor instrumentation argument"
                }
            val family =
                requireNotNull(args.getString("performanceFamily")) {
                    "Missing performanceFamily instrumentation argument"
                }
            val packageName =
                requireNotNull(args.getString("performancePackage")) {
                    "Missing performancePackage instrumentation argument"
                }
            return PerformanceConfig(
                packageName = packageName,
                flavor = flavor,
                family = PerformanceFamily.from(family),
                iterations = parseIterations(args.getString("benchmarkIterations")),
            )
        }
    }
}

internal fun parseIterations(raw: String?): Int =
    raw?.toIntOrNull()?.coerceIn(5, 30) ?: 10
