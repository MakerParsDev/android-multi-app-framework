package com.parsfilo.contentapp.feature.qibla

import android.hardware.SensorManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QiblaViewModelTest {

    @Test
    fun noPermissionNeverExposesDefaultCoordinateTurnInstructions() {
        val vm = QiblaViewModel()
        vm.onSensorSourceResolved(OrientationSensorSource.ROTATION_VECTOR)
        vm.onSensorAccuracyChanged(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)

        assertThat(vm.uiState.value.locationStatus).isEqualTo(QiblaLocationStatus.FALLBACK_DEFAULT)
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.PERMISSION_REQUIRED)

        vm.onPermissionStateChanged(false)
        vm.onHeadingUpdated(164f)
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.PERMISSION_REQUIRED)
    }

    @Test
    fun unavailableLiveFixSuppressesFallbackBearingEvenWithHighSensorConfidence() {
        val vm = QiblaViewModel()
        vm.onPermissionStateChanged(true)
        vm.onSensorSourceResolved(OrientationSensorSource.ROTATION_VECTOR)
        vm.onSensorAccuracyChanged(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        vm.onLocationUnavailable()

        assertThat(vm.uiState.value.locationStatus).isEqualTo(QiblaLocationStatus.UNAVAILABLE)
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.LOCATION_UNVERIFIED)
        assertThat(vm.uiState.value.latitude).isNull()
    }

    @Test
    fun refreshThenLiveLocationRestoresGuidanceOnlyWithAvailableSensor() {
        val vm = QiblaViewModel()
        vm.onPermissionStateChanged(true)
        vm.requestLocationRefresh()
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.REFRESHING_LOCATION)

        vm.onLocationResolved(41.0082, 28.9784, 8f, 5f)
        assertThat(vm.uiState.value.locationStatus).isEqualTo(QiblaLocationStatus.LIVE)
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.SENSOR_UNAVAILABLE)

        vm.onSensorSourceResolved(OrientationSensorSource.ROTATION_VECTOR)
        vm.onSensorAccuracyChanged(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.SENSOR_UNAVAILABLE)
        vm.onHeadingUpdated(152f)
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.READY)

        vm.onSensorAccuracyChanged(SensorManager.SENSOR_STATUS_UNRELIABLE)
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.SENSOR_UNAVAILABLE)
        vm.onSensorAccuracyChanged(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM)
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.READY)
    }

    @Test
    fun revokingPermissionOrLosingFixSuppressesPreviousLiveGuidance() {
        val vm = QiblaViewModel()
        vm.onPermissionStateChanged(true)
        vm.onLocationResolved(41.0082, 28.9784, 8f, 5f)
        vm.onSensorSourceResolved(OrientationSensorSource.ROTATION_VECTOR)
        vm.onSensorAccuracyChanged(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        vm.onHeadingUpdated(157f)
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.READY)

        vm.onLocationUnavailable()
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.LOCATION_UNVERIFIED)
        vm.onLocationResolved(40.0, 30.0, 5f, 4f)
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.READY)
        vm.onPermissionStateChanged(false)
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.PERMISSION_REQUIRED)
        assertThat(vm.uiState.value.latitude).isNull()

        vm.onPermissionStateChanged(true)
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.LOCATION_UNVERIFIED)
    }

    @Test
    fun invalidLiveCoordinatesAndRefreshingStateCannotBeUsedAsInstructions() {
        val valid = QiblaUiState(
            hasLocationPermission = true,
            locationStatus = QiblaLocationStatus.LIVE,
            latitude = 41.0,
            longitude = 29.0,
            sensorSource = OrientationSensorSource.ROTATION_VECTOR,
            sensorAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
            hasHeadingSample = true,
        )
        assertThat(valid.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.READY)
        assertThat(valid.copy(latitude = null).guidanceStatus())
            .isEqualTo(QiblaGuidanceStatus.LOCATION_UNVERIFIED)
        assertThat(valid.copy(latitude = 190.0).guidanceStatus())
            .isEqualTo(QiblaGuidanceStatus.LOCATION_UNVERIFIED)
        assertThat(valid.copy(longitude = Double.NaN).guidanceStatus())
            .isEqualTo(QiblaGuidanceStatus.LOCATION_UNVERIFIED)
        assertThat(valid.copy(isLocationRefreshing = true).guidanceStatus())
            .isEqualTo(QiblaGuidanceStatus.REFRESHING_LOCATION)
    }

    @Test
    fun highAccuracyCallbackDoesNotUnlockGuidanceBeforeHeadingSample() {
        val vm = QiblaViewModel()
        vm.onPermissionStateChanged(true)
        vm.onLocationResolved(41.0, 29.0, 5f, 0f)
        vm.onSensorSourceResolved(OrientationSensorSource.ACCELEROMETER_MAGNETOMETER)
        vm.onSensorAccuracyChanged(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        assertThat(vm.uiState.value.hasHeadingSample).isFalse()
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.SENSOR_UNAVAILABLE)

        vm.onHeadingUpdated(0f)
        assertThat(vm.uiState.value.hasHeadingSample).isTrue()
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.READY)

        vm.onSensorSourceResolved(OrientationSensorSource.ROTATION_VECTOR)
        assertThat(vm.uiState.value.hasHeadingSample).isFalse()
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.SENSOR_UNAVAILABLE)
        vm.onHeadingUpdated(Float.NaN)
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.SENSOR_UNAVAILABLE)
        vm.onHeadingUpdated(91f)
        assertThat(vm.uiState.value.guidanceStatus()).isEqualTo(QiblaGuidanceStatus.READY)
    }

    @Test
    fun staleLocationAndFutureTimestampDoNotCountAsFreshFix() {
        val now = 2_000_000_000_000L
        assertThat(isRecentLocationFix(now - 1_000_000_000L, now)).isTrue()
        assertThat(isRecentLocationFix(now - 60_000_000_000L, now)).isTrue()
        assertThat(isRecentLocationFix(now - 60_000_000_001L, now)).isFalse()
        assertThat(isRecentLocationFix(0L, now)).isFalse()
        assertThat(isRecentLocationFix(now + 1L, now)).isFalse()
    }

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
