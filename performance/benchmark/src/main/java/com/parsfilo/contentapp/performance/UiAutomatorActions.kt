package com.parsfilo.contentapp.performance

import android.graphics.Point
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

private const val READY_TIMEOUT_MS = 15_000L

internal fun MacrobenchmarkScope.waitForTag(config: PerformanceConfig, tag: String) {
    check(device.wait(Until.hasObject(By.res(config.packageName, tag)), READY_TIMEOUT_MS)) {
        "Timed out waiting for tag=$tag flavor=${config.flavor} package=${config.packageName}"
    }
}

internal fun MacrobenchmarkScope.clickTag(config: PerformanceConfig, tag: String) {
    waitForTag(config, tag)
    requireNotNull(device.findObject(By.res(config.packageName, tag))) {
        "Missing object after wait: $tag"
    }.click()
}

internal fun MacrobenchmarkScope.scrollTag(config: PerformanceConfig, tag: String) {
    waitForTag(config, tag)
    val objectUnderTest = requireNotNull(device.findObject(By.res(config.packageName, tag)))
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
