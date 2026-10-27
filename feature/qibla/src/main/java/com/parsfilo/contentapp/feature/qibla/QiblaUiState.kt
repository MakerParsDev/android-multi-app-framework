package com.parsfilo.contentapp.feature.qibla

import android.hardware.SensorManager

enum class OrientationSensorSource {
    ROTATION_VECTOR,
    GEOMAGNETIC_ROTATION_VECTOR,
    ACCELEROMETER_MAGNETOMETER,
    UNAVAILABLE,
}

enum class QiblaLocationStatus {
    LIVE,
    UNAVAILABLE,
    FALLBACK_DEFAULT,
}

enum class QiblaConfidenceLevel {
    EXCELLENT,
    GOOD,
    FAIR,
    LOW,
}

/** Presentation safety, distinct from sensor confidence or cached bearing mathematics. */
internal enum class QiblaGuidanceStatus {
    PERMISSION_REQUIRED,
    REFRESHING_LOCATION,
    LOCATION_UNVERIFIED,
    SENSOR_UNAVAILABLE,
    READY,
}

data class QiblaUiState(
    val qiblaBearing: Float = QiblaMath.qiblaBearingDegrees(DEFAULT_LAT, DEFAULT_LON),
    val headingTrueNorth: Float = 0f,
    val relativeToQibla: Float = 0f,
    val signedDeltaToQibla: Float = 0f,
    val hasLocationPermission: Boolean = false,
    val isLocationRefreshing: Boolean = false,
    val locationStatus: QiblaLocationStatus = QiblaLocationStatus.FALLBACK_DEFAULT,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Int? = null,
    val geomagneticDeclination: Float = 0f,
    val sensorSource: OrientationSensorSource = OrientationSensorSource.UNAVAILABLE,
    val sensorAccuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE,
    val confidenceScore: Int = 25,
    val confidenceLevel: QiblaConfidenceLevel = QiblaConfidenceLevel.LOW,
    val refreshRequestNonce: Int = 0,
)

internal const val DEFAULT_LAT = 39.0
internal const val DEFAULT_LON = 35.0

/**
 * Never expose the default-coordinate bearing as a current-location instruction.
 * A magnetic sensor confidence score alone cannot validate geographic location.
 */
internal fun QiblaUiState.guidanceStatus(): QiblaGuidanceStatus = when {
    !hasLocationPermission -> QiblaGuidanceStatus.PERMISSION_REQUIRED
    isLocationRefreshing -> QiblaGuidanceStatus.REFRESHING_LOCATION
    locationStatus != QiblaLocationStatus.LIVE ||
        latitude == null ||
        longitude == null ||
        !latitude.isFinite() ||
        !longitude.isFinite() ||
        latitude !in -90.0..90.0 ||
        longitude !in -180.0..180.0 ->
        QiblaGuidanceStatus.LOCATION_UNVERIFIED
    sensorSource == OrientationSensorSource.UNAVAILABLE ||
        sensorAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ->
        QiblaGuidanceStatus.SENSOR_UNAVAILABLE
    else -> QiblaGuidanceStatus.READY
}
