package com.parsfilo.contentapp.performance

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

/**
 * Single-iteration device acceptance for the exact journey used by BaselineProfileGenerator.
 * Unlike profile collection, this deliberately skips repeated compilation and stabilization.
 */
class JourneySmokeTest {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun primaryJourneyOnce() {
        val config = PerformanceConfig.current()
        benchmarkRule.measureRepeated(
            packageName = config.packageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.WARM,
            iterations = 1,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                waitForTag(config, PerformanceTags.APP_ROOT)
            },
            measureBlock = {
                CriticalUserJourneys.runFromRoot(this, config)
            },
        )
    }
}
