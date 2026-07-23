package com.parsfilo.contentapp.performance

import android.graphics.Point
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.onElementOrNull

private const val READY_TIMEOUT_MS = 15_000L
private const val COMPOSE_TEST_TAG_EXTRA = "androidx.compose.ui.semantics.testTag"

private fun MacrobenchmarkScope.findTag(
    tag: String,
    timeoutMs: Long = READY_TIMEOUT_MS,
): UiObject2? =
    device.onElementOrNull(timeoutMs = timeoutMs) {
        extras.getString(COMPOSE_TEST_TAG_EXTRA) == tag || viewIdResourceName == tag
    }

internal fun MacrobenchmarkScope.waitForTag(
    config: PerformanceConfig,
    tag: String,
): UiObject2 =
    checkNotNull(findTag(tag)) {
        "Timed out waiting for tag=$tag flavor=${config.flavor} package=${config.packageName}"
    }

internal fun MacrobenchmarkScope.clickTag(config: PerformanceConfig, tag: String) {
    waitForTag(config, tag).click()
}

internal fun MacrobenchmarkScope.scrollTag(config: PerformanceConfig, tag: String) {
    val objectUnderTest = waitForTag(config, tag)
    val bounds = objectUnderTest.visibleBounds
    val centerX = bounds.centerX()
    val start = Point(centerX, bounds.bottom - bounds.height() / 5)
    val end = Point(centerX, bounds.top + bounds.height() / 5)
    device.swipe(start.x, start.y, end.x, end.y, 12)
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.launchRoot(config: PerformanceConfig) {
    pressHome()
    startActivityAndWait()
    waitForTag(config, PerformanceTags.APP_ROOT)
}
