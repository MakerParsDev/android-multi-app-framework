package com.parsfilo.contentapp.performance

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

class FrameBenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun primaryJourneyFrames() {
        val config = PerformanceConfig.current()
        benchmarkRule.measureRepeated(
            packageName = config.packageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = config.iterations,
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
