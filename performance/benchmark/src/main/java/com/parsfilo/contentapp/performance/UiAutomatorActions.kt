package com.parsfilo.contentapp.performance

import android.Manifest
import android.graphics.Point
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.onElementOrNull
import java.io.ByteArrayOutputStream

private const val READY_TIMEOUT_MS = 15_000L
private const val OPTIONAL_TIMEOUT_MS = 2_000L
private const val CLICK_RETRIES = 3
private const val HIERARCHY_DIAGNOSTIC_LIMIT = 8_000
private const val COMPOSE_TEST_TAG_EXTRA = "androidx.compose.ui.semantics.testTag"

private fun MacrobenchmarkScope.findTag(
    tag: String,
    timeoutMs: Long = READY_TIMEOUT_MS,
): UiObject2? =
    device.onElementOrNull(timeoutMs = timeoutMs) {
        extras.getString(COMPOSE_TEST_TAG_EXTRA) == tag || viewIdResourceName == tag
    }

private fun MacrobenchmarkScope.accessibilityHierarchy(): String =
    runCatching {
        ByteArrayOutputStream().use { output ->
            device.dumpWindowHierarchy(output)
            output.toString(Charsets.UTF_8.name()).take(HIERARCHY_DIAGNOSTIC_LIMIT)
        }
    }.getOrElse { error ->
        "<unavailable: ${error::class.java.simpleName}: ${error.message}>"
    }

internal fun MacrobenchmarkScope.waitForTag(
    config: PerformanceConfig,
    tag: String,
): UiObject2 {
    val matchedNode = findTag(tag)
    checkNotNull(matchedNode) {
        buildString {
            append("Timed out waiting for tag=$tag flavor=${config.flavor} package=${config.packageName}")
            append("\nAccessibility hierarchy:\n")
            append(accessibilityHierarchy())
        }
    }
    return matchedNode
}

private fun MacrobenchmarkScope.clickNodeWithRetry(
    config: PerformanceConfig,
    tag: String,
    findNode: () -> UiObject2?,
): Boolean {
    var staleFailure: StaleObjectException? = null
    repeat(CLICK_RETRIES) {
        val node = findNode() ?: return false
        try {
            val bounds = node.visibleBounds
            check(device.click(bounds.centerX(), bounds.centerY())) {
                "Device rejected click for tag=$tag flavor=${config.flavor}"
            }
            device.waitForIdle()
            return true
        } catch (error: StaleObjectException) {
            staleFailure = error
            device.waitForIdle()
        }
    }
    throw IllegalStateException(
        "Unable to click tag=$tag flavor=${config.flavor} after $CLICK_RETRIES attempts\n" +
            "Accessibility hierarchy:\n${accessibilityHierarchy()}",
        staleFailure,
    )
}

internal fun MacrobenchmarkScope.scrollUntilTag(
    config: PerformanceConfig,
    tag: String,
    containerTag: String,
) {
    // Scroll the actual content rather than the full-screen Scaffold. Swipes
    // against the Scaffold can land on non-scrollable controls and do nothing.
    repeat(6) {
        if (findTag(tag, timeoutMs = 500L) != null) return
        val container = waitForTag(config, containerTag)
        val scrollable = container.findObject(By.scrollable(true))
            ?: device.findObject(By.scrollable(true))
        checkNotNull(scrollable) {
            "No scrollable content for tag=$tag flavor=${config.flavor}"
        }
        if (!scrollable.scroll(Direction.DOWN, 0.85f)) {
            waitForTag(config, tag)
        }
        device.waitForIdle()
    }
    // Preserve the actionable hierarchy diagnostic instead of silently
    // ignoring a genuinely missing control.
    waitForTag(config, tag)
}

internal fun MacrobenchmarkScope.clickTag(config: PerformanceConfig, tag: String) {
    check(clickNodeWithRetry(config, tag) { waitForTag(config, tag) })
}

internal fun MacrobenchmarkScope.clickTagIfPresent(
    config: PerformanceConfig,
    tag: String,
): Boolean = clickNodeWithRetry(config, tag) { findTag(tag, OPTIONAL_TIMEOUT_MS) }

internal fun MacrobenchmarkScope.scrollTag(config: PerformanceConfig, tag: String) {
    val objectUnderTest = waitForTag(config, tag)
    val bounds = objectUnderTest.visibleBounds
    val centerX = bounds.centerX()
    val start = Point(centerX, bounds.bottom - bounds.height() / 5)
    val end = Point(centerX, bounds.top + bounds.height() / 5)
    device.swipe(start.x, start.y, end.x, end.y, 12)
    device.waitForIdle()
}

private fun MacrobenchmarkScope.grantLocationIfRequired(config: PerformanceConfig) {
    if (config.family !in setOf(PerformanceFamily.QIBLA, PerformanceFamily.PRAYER_TIMES)) return
    for (permission in listOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )) {
        device.executeShellCommand("pm grant ${config.packageName} $permission")
    }
}

internal fun MacrobenchmarkScope.launchRoot(config: PerformanceConfig) {
    pressHome()
    grantLocationIfRequired(config)
    startActivityAndWait()
    waitForTag(config, PerformanceTags.APP_ROOT)
}
