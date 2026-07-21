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
