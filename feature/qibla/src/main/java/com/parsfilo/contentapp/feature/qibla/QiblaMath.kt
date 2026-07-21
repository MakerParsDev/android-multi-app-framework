package com.parsfilo.contentapp.feature.qibla

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object QiblaMath {
    private const val KAABA_LAT = 21.4225
    private const val KAABA_LON = 39.8262

    fun qiblaBearingDegrees(latitude: Double, longitude: Double): Float {
        val latRad = Math.toRadians(latitude)
        val lonRad = Math.toRadians(longitude)
        val kaabaLatRad = Math.toRadians(KAABA_LAT)
        val kaabaLonRad = Math.toRadians(KAABA_LON)

        val deltaLon = kaabaLonRad - lonRad
        val y = sin(deltaLon)
        val x = cos(latRad) * sin(kaabaLatRad) - sin(latRad) * cos(kaabaLatRad) * cos(deltaLon)
        val bearing = Math.toDegrees(atan2(y, x))
        return normalize360(bearing).toFloat()
    }

    fun normalize360(value: Double): Double {
        if (!value.isFinite()) return 0.0
        val normalized = value % 360.0
        return if (normalized < 0.0) normalized + 360.0 else normalized
    }

    fun normalize360(value: Float): Float {
        if (!value.isFinite()) return 0f
        return normalize360(value.toDouble()).toFloat()
    }

    /**
     * Returns the signed shortest angular delta to rotate [fromDegrees] into [toDegrees], in range [-180, 180].
     */
    fun shortestSignedAngleDegrees(fromDegrees: Float, toDegrees: Float): Float {
        if (!fromDegrees.isFinite() || !toDegrees.isFinite()) return 0f
        var delta = (toDegrees - fromDegrees) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    /**
     * Smooths heading transition while preserving wrap-around behavior at 0/360 boundaries.
     */
    fun smoothDegrees(current: Float, target: Float, alpha: Float): Float {
        if (!current.isFinite() || !target.isFinite()) return 0f
        if (!alpha.isFinite()) return normalize360(target)
        if (alpha <= 0f) return normalize360(current)
        if (alpha >= 1f) return normalize360(target)
        val delta = shortestSignedAngleDegrees(current, target)
        return normalize360(current + (delta * alpha))
    }

    /**
     * Relative clockwise angle where 0 means "straight ahead", 90 means "turn right".
     */
    fun relativeClockwiseDegrees(currentHeading: Float, targetBearing: Float): Float {
        if (!currentHeading.isFinite() || !targetBearing.isFinite()) return 0f
        return normalize360(targetBearing - currentHeading)
    }
}
