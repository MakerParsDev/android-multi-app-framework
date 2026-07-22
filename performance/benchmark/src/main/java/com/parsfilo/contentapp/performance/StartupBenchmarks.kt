package com.parsfilo.contentapp.performance

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

class StartupBenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldBaselineProfile() = run(StartupMode.COLD, profiled())

    @Test
    fun coldNoProfile() = run(StartupMode.COLD, CompilationMode.None())

    @Test
    fun warmBaselineProfile() = run(StartupMode.WARM, profiled())

    @Test
    fun warmNoProfile() = run(StartupMode.WARM, CompilationMode.None())

    private fun profiled(): CompilationMode =
        CompilationMode.Partial(BaselineProfileMode.Require)

    private fun run(startupMode: StartupMode, compilationMode: CompilationMode) {
        val config = PerformanceConfig.current()
        benchmarkRule.measureRepeated(
            packageName = config.packageName,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = startupMode,
            iterations = config.iterations,
            setupBlock = { pressHome() },
            measureBlock = {
                startActivityAndWait()
                waitForTag(config, PerformanceTags.APP_ROOT)
            },
        )
    }
}
