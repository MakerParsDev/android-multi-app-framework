package com.parsfilo.contentapp.feature.qibla

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QiblaMathTest {

    @Test
    fun qiblaBearing_isWithinCompassRange() {
        val bearing = QiblaMath.qiblaBearingDegrees(latitude = 41.0082, longitude = 28.9784)
        assertThat(bearing).isAtLeast(0f)
        assertThat(bearing).isLessThan(360f)
    }

    @Test
    fun qiblaBearing_istanbul_isCloseToKnownDirection() {
        val bearing = QiblaMath.qiblaBearingDegrees(latitude = 41.0082, longitude = 28.9784)
        assertThat(bearing).isAtLeast(149f)
        assertThat(bearing).isAtMost(154f)
    }

    @Test
    fun shortestSignedAngle_wrapsAcrossZero() {
        val delta = QiblaMath.shortestSignedAngleDegrees(fromDegrees = 350f, toDegrees = 10f)
        assertThat(delta).isWithin(0.001f).of(20f)
    }

    @Test
    fun smoothDegrees_handlesWrapCorrectly() {
        val smoothed = QiblaMath.smoothDegrees(current = 350f, target = 10f, alpha = 0.5f)
        assertThat(smoothed).isWithin(0.001f).of(0f)
    }

    @Test
    fun relativeClockwiseDegrees_returnsClockwiseDelta() {
        val relative = QiblaMath.relativeClockwiseDegrees(currentHeading = 100f, targetBearing = 80f)
        assertThat(relative).isWithin(0.001f).of(340f)
    }
}
