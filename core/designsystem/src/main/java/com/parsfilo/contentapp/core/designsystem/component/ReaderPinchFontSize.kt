package com.parsfilo.contentapp.core.designsystem.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

/**
 * Scale only the reading text, preserving reflow and the user's existing scroll position.
 * The single-finger scroll path is deliberately ignored and never consumes a pointer.
 */
@Composable
fun Modifier.readerPinchFontSize(
    fontSize: Int,
    fontSizeRange: IntRange,
    onPreview: (Int) -> Unit,
    onCommitted: (Int) -> Unit,
): Modifier {
    val latestFontSize = rememberUpdatedState(fontSize)
    val latestPreview = rememberUpdatedState(onPreview)
    val latestCommitted = rememberUpdatedState(onCommitted)

    return pointerInput(fontSizeRange.first, fontSizeRange.last) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val initialFontSize = latestFontSize.value
            var cumulativeZoom = 1f
            var previewFontSize = initialFontSize
            var usedTwoFingers = false
            do {
                val event = awaitPointerEvent()
                // Preserve LazyColumn's one-finger scroll and native long-press selection.
                if (event.changes.count { it.pressed } >= 2) {
                    val zoom = event.calculateZoom()
                    if (zoom.isFinite() && zoom > 0f && zoom != 1f) {
                        usedTwoFingers = true
                        cumulativeZoom *= zoom
                        val nextSize = scaledReaderFontSize(
                            initialSize = initialFontSize,
                            zoomFactor = cumulativeZoom,
                            minSize = fontSizeRange.first,
                            maxSize = fontSizeRange.last,
                        )
                        if (nextSize != previewFontSize) {
                            previewFontSize = nextSize
                            latestPreview.value(nextSize)
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
            // Persist once at the end of the gesture, not on every pointer frame.
            if (usedTwoFingers && previewFontSize != initialFontSize) {
                latestCommitted.value(previewFontSize)
            }
        }
    }
}

fun scaledReaderFontSize(
    initialSize: Int,
    zoomFactor: Float,
    minSize: Int,
    maxSize: Int,
): Int {
    require(minSize <= maxSize)
    if (!zoomFactor.isFinite() || zoomFactor <= 0f) {
        return initialSize.coerceIn(minSize, maxSize)
    }
    return (initialSize * zoomFactor).roundToInt().coerceIn(minSize, maxSize)
}
