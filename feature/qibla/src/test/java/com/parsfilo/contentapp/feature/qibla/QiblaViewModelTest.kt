package com.parsfilo.contentapp.feature.qibla

import android.hardware.SensorManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QiblaViewModelTest {

    @Test
    fun confidence_isHigh_whenSensorAndLocationAreGood() {
        val viewModel = QiblaViewModel()
        viewModel.onPermissionStateChanged(true)
        viewModel.onSensorSourceResolved(OrientationSensorSource.ROTATION_VECTOR)
        viewModel.onSensorAccuracyChanged(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        viewModel.onLocationResolved(
            latitude = 41.0082,
            longitude = 28.9784,
            accuracyMeters = 8f,
            geomagneticDeclination = 5f,
        )

        assertThat(viewModel.uiState.value.confidenceScore).isAtLeast(85)
        assertThat(viewModel.uiState.value.confidenceLevel).isEqualTo(QiblaConfidenceLevel.EXCELLENT)
    }

    @Test
    fun confidence_isLow_whenPermissionMissingAndSensorUnreliable() {
        val viewModel = QiblaViewModel()
        viewModel.onPermissionStateChanged(false)
        viewModel.onSensorSourceResolved(OrientationSensorSource.ACCELEROMETER_MAGNETOMETER)
        viewModel.onSensorAccuracyChanged(SensorManager.SENSOR_STATUS_UNRELIABLE)
        viewModel.onLocationUnavailable()

        assertThat(viewModel.uiState.value.confidenceScore).isLessThan(50)
        assertThat(viewModel.uiState.value.confidenceLevel).isEqualTo(QiblaConfidenceLevel.LOW)
    }
}
