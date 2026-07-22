package com.parsfilo.contentapp.performance

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupProfile() {
        val config = PerformanceConfig.current()
        baselineProfileRule.collect(
            packageName = config.packageName,
            includeInStartupProfile = true,
            maxIterations = 15,
            stableIterations = 3,
        ) {
            CriticalUserJourneys.startup(this, config)
        }
    }

    @Test
    fun baselineJourneys() {
        val config = PerformanceConfig.current()
        baselineProfileRule.collect(
            packageName = config.packageName,
            includeInStartupProfile = false,
            maxIterations = 15,
            stableIterations = 3,
        ) {
            CriticalUserJourneys.run(this, config)
        }
    }
}
