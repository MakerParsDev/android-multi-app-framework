package com.parsfilo.contentapp.feature.qibla

import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class QiblaViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(QiblaUiState())
    val uiState: StateFlow<QiblaUiState> = _uiState

    fun onPermissionStateChanged(granted: Boolean) {
        _uiState.update { current ->
            recalculateDerived(
                current.copy(
                    hasLocationPermission = granted,
                    locationStatus = if (granted) current.locationStatus else QiblaLocationStatus.FALLBACK_DEFAULT,
                    latitude = if (granted) current.latitude else null,
                    longitude = if (granted) current.longitude else null,
                    locationAccuracyMeters = if (granted) current.locationAccuracyMeters else null,
                    geomagneticDeclination = if (granted) current.geomagneticDeclination else 0f,
                    qiblaBearing = if (granted) current.qiblaBearing else QiblaMath.qiblaBearingDegrees(DEFAULT_LAT, DEFAULT_LON),
                    isLocationRefreshing = if (granted) current.isLocationRefreshing else false,
                ),
            )
        }
    }

    fun requestLocationRefresh() {
        _uiState.update { current ->
            current.copy(
                isLocationRefreshing = true,
                refreshRequestNonce = current.refreshRequestNonce + 1,
            )
        }
    }

    fun onLocationResolved(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        geomagneticDeclination: Float,
    ) {
        _uiState.update { current ->
            recalculateDerived(
                current.copy(
                    qiblaBearing = QiblaMath.qiblaBearingDegrees(latitude, longitude),
                    locationStatus = QiblaLocationStatus.LIVE,
                    latitude = latitude,
                    longitude = longitude,
                    locationAccuracyMeters = accuracyMeters.roundToInt(),
                    geomagneticDeclination = geomagneticDeclination,
                    isLocationRefreshing = false,
                ),
            )
        }
    }

    fun onLocationUnavailable() {
        _uiState.update { current ->
            recalculateDerived(
                current.copy(
                    locationStatus = if (current.hasLocationPermission) {
                        QiblaLocationStatus.UNAVAILABLE
                    } else {
                        QiblaLocationStatus.FALLBACK_DEFAULT
                    },
                    qiblaBearing = QiblaMath.qiblaBearingDegrees(DEFAULT_LAT, DEFAULT_LON),
                    latitude = null,
                    longitude = null,
                    locationAccuracyMeters = null,
                    geomagneticDeclination = 0f,
                    isLocationRefreshing = false,
                ),
            )
        }
    }

    fun onSensorSourceResolved(source: OrientationSensorSource) {
        _uiState.update { current -> recalculateDerived(current.copy(sensorSource = source)) }
    }

    fun onSensorAccuracyChanged(accuracy: Int) {
        _uiState.update { current -> recalculateDerived(current.copy(sensorAccuracy = accuracy)) }
    }

    fun onHeadingUpdated(headingTrueNorth: Float) {
        if (!headingTrueNorth.isFinite()) return
        _uiState.update { current ->
            recalculateDerived(current.copy(headingTrueNorth = QiblaMath.normalize360(headingTrueNorth)))
        }
    }

    private fun recalculateDerived(state: QiblaUiState): QiblaUiState {
        val relative = QiblaMath.relativeClockwiseDegrees(state.headingTrueNorth, state.qiblaBearing)
        val signed = QiblaMath.shortestSignedAngleDegrees(state.headingTrueNorth, state.qiblaBearing)
        val confidence = computeConfidenceScore(state)
        return state.copy(
            relativeToQibla = relative,
            signedDeltaToQibla = signed,
            confidenceScore = confidence,
            confidenceLevel = when {
                confidence >= 85 -> QiblaConfidenceLevel.EXCELLENT
                confidence >= 70 -> QiblaConfidenceLevel.GOOD
                confidence >= 50 -> QiblaConfidenceLevel.FAIR
                else -> QiblaConfidenceLevel.LOW
            },
        )
    }

    private fun computeConfidenceScore(state: QiblaUiState): Int {
        var score = when (state.sensorSource) {
            OrientationSensorSource.ROTATION_VECTOR -> 88
            OrientationSensorSource.GEOMAGNETIC_ROTATION_VECTOR -> 79
            OrientationSensorSource.ACCELEROMETER_MAGNETOMETER -> 68
            OrientationSensorSource.UNAVAILABLE -> 20
        }

        score += when (state.sensorAccuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 7
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 0
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> -15
            SensorManager.SENSOR_STATUS_UNRELIABLE -> -28
            else -> -8
        }

        val locationPenalty = when {
            !state.hasLocationPermission -> 20
            state.locationStatus != QiblaLocationStatus.LIVE -> 14
            else -> 0
        }
        score -= locationPenalty

        val locationAccuracyPenalty = when {
            state.locationAccuracyMeters == null -> 10
            state.locationAccuracyMeters <= 20 -> 0
            state.locationAccuracyMeters <= 50 -> 4
            state.locationAccuracyMeters <= 100 -> 10
            else -> 18
        }
        score -= locationAccuracyPenalty

        return score.coerceIn(0, 100)
    }
}
