package com.parsfilo.contentapp.performance

import androidx.benchmark.macro.MacrobenchmarkScope

internal object CriticalUserJourneys {
    fun startup(scope: MacrobenchmarkScope, config: PerformanceConfig) {
        scope.launchRoot(config)
        scope.waitForTag(config, PerformanceTags.PRIMARY_NAVIGATION)
    }

    fun run(scope: MacrobenchmarkScope, config: PerformanceConfig) {
        startup(scope, config)
        runFromRoot(scope, config)
    }

    fun runFromRoot(scope: MacrobenchmarkScope, config: PerformanceConfig) {
        when (config.family) {
            PerformanceFamily.AUDIO_CONTENT -> audioContent(scope, config)
            PerformanceFamily.QURAN -> quran(scope, config)
            PerformanceFamily.MIRACLES -> miracles(scope, config)
            PerformanceFamily.PRAYER_TIMES -> scope.waitForTag(config, PerformanceTags.PRAYER_TIMES_READY)
            PerformanceFamily.QIBLA -> scope.waitForTag(config, PerformanceTags.QIBLA_READY)
            PerformanceFamily.COUNTER -> counter(scope, config)
        }
    }

    private fun audioContent(scope: MacrobenchmarkScope, config: PerformanceConfig) {
        scope.waitForTag(config, PerformanceTags.CONTENT_LIST)
        scope.scrollTag(config, PerformanceTags.CONTENT_LIST)
        scope.clickTag(config, PerformanceTags.CONTENT_FIRST_ITEM)
        scope.waitForTag(config, PerformanceTags.CONTENT_DETAIL)
        scope.clickTag(config, PerformanceTags.AUDIO_PLAY_PAUSE)
        scope.device.waitForIdle()
        scope.clickTag(config, PerformanceTags.AUDIO_PLAY_PAUSE)
        scope.device.pressBack()
    }

    private fun quran(scope: MacrobenchmarkScope, config: PerformanceConfig) {
        scope.waitForTag(config, PerformanceTags.QURAN_LIST)
        scope.scrollTag(config, PerformanceTags.QURAN_LIST)
        scope.clickTag(config, PerformanceTags.QURAN_FIRST_ITEM)
        scope.waitForTag(config, PerformanceTags.QURAN_DETAIL)
        scope.device.pressBack()
    }

    private fun miracles(scope: MacrobenchmarkScope, config: PerformanceConfig) {
        scope.waitForTag(config, PerformanceTags.MIRACLES_LIST)
        scope.scrollTag(config, PerformanceTags.MIRACLES_LIST)
        scope.clickTag(config, PerformanceTags.MIRACLES_FIRST_ITEM)
        scope.waitForTag(config, PerformanceTags.MIRACLES_DETAIL)
        scope.device.pressBack()
    }

    private fun counter(scope: MacrobenchmarkScope, config: PerformanceConfig) {
        scope.waitForTag(config, PerformanceTags.COUNTER_ROOT)
        repeat(3) { scope.clickTag(config, PerformanceTags.COUNTER_INCREMENT) }
        scope.waitForTag(config, PerformanceTags.COUNTER_VALUE)
    }
}
