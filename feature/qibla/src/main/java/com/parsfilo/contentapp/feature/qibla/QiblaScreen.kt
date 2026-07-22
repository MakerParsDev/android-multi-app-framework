package com.parsfilo.contentapp.feature.qibla

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.location.Location
import android.view.Display
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun QiblaRoute(
    appName: String,
    onSettingsClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    bannerAdContent: @Composable () -> Unit = {},
    nativeAdContent: @Composable () -> Unit = {},
) {
    val viewModel: QiblaViewModel = hiltViewModel()
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(SensorManager::class.java) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.any { it }
        viewModel.onPermissionStateChanged(granted)
        if (granted) viewModel.requestLocationRefresh()
    }

    LaunchedEffect(Unit) {
        val granted = hasLocationPermission(context)
        viewModel.onPermissionStateChanged(granted)
        if (granted) {
            viewModel.requestLocationRefresh()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    LaunchedEffect(uiState.hasLocationPermission, uiState.refreshRequestNonce) {
        if (!uiState.hasLocationPermission) {
            viewModel.onLocationUnavailable()
            return@LaunchedEffect
        }
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.onPermissionStateChanged(false)
            viewModel.onLocationUnavailable()
            return@LaunchedEffect
        }
        val location = resolveBestLocation(context)
        if (location == null) {
            viewModel.onLocationUnavailable()
            return@LaunchedEffect
        }
        val declination = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            System.currentTimeMillis(),
        ).declination
        viewModel.onLocationResolved(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            geomagneticDeclination = declination,
        )
    }

    DisposableEffect(sensorManager, uiState.geomagneticDeclination) {
        if (sensorManager == null) return@DisposableEffect onDispose {}

        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val geomagneticRotationSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val sensorSource = when {
            rotationSensor != null -> OrientationSensorSource.ROTATION_VECTOR
            geomagneticRotationSensor != null -> OrientationSensorSource.GEOMAGNETIC_ROTATION_VECTOR
            accelerometer != null && magnetometer != null -> OrientationSensorSource.ACCELEROMETER_MAGNETOMETER
            else -> OrientationSensorSource.UNAVAILABLE
        }
        viewModel.onSensorSourceResolved(sensorSource)

        if (sensorSource == OrientationSensorSource.UNAVAILABLE) {
            return@DisposableEffect onDispose {}
        }

        val baseRotationMatrix = FloatArray(9)
        val adjustedRotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false
        var smoothedHeading by mutableFloatStateOf(0f)
        var initializedHeading = false

        fun getDisplayRotation(): Int {
            val display = context.getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
            return display?.rotation ?: Surface.ROTATION_0
        }

        fun updateHeadingFromMatrix() {
            val rotation = getDisplayRotation()
            remapForDisplay(baseRotationMatrix, adjustedRotationMatrix, rotation)
            SensorManager.getOrientation(adjustedRotationMatrix, orientation)
            val magneticHeading =
                QiblaMath.normalize360(Math.toDegrees(orientation[0].toDouble()).toFloat())
            val trueHeading =
                QiblaMath.normalize360(magneticHeading + uiState.geomagneticDeclination)
            smoothedHeading = if (initializedHeading) {
                QiblaMath.smoothDegrees(smoothedHeading, trueHeading, SENSOR_SMOOTHING_ALPHA)
            } else {
                initializedHeading = true
                trueHeading
            }
            viewModel.onHeadingUpdated(smoothedHeading)
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                // FIX 3: UNAVAILABLE branch kaldırıldı — bu noktaya zaten ulaşılamaz
                // çünkü sensorSource == UNAVAILABLE ise yukarıda erken dönüş yapılıyor.
                when (sensorSource) {
                    OrientationSensorSource.ROTATION_VECTOR -> {
                        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                        SensorManager.getRotationMatrixFromVector(baseRotationMatrix, event.values)
                        updateHeadingFromMatrix()
                    }

                    OrientationSensorSource.GEOMAGNETIC_ROTATION_VECTOR -> {
                        if (event.sensor.type != Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) return
                        SensorManager.getRotationMatrixFromVector(baseRotationMatrix, event.values)
                        updateHeadingFromMatrix()
                    }

                    OrientationSensorSource.ACCELEROMETER_MAGNETOMETER -> {
                        when (event.sensor.type) {
                            Sensor.TYPE_ACCELEROMETER -> {
                                System.arraycopy(event.values, 0, gravity, 0, 3)
                                hasGravity = true
                            }

                            Sensor.TYPE_MAGNETIC_FIELD -> {
                                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                                hasGeomagnetic = true
                            }
                        }
                        if (hasGravity &&
                            hasGeomagnetic &&
                            SensorManager.getRotationMatrix(
                                baseRotationMatrix,
                                null,
                                gravity,
                                geomagnetic,
                            )
                        ) {
                            updateHeadingFromMatrix()
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor == null) return
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD || sensor.type == Sensor.TYPE_ROTATION_VECTOR || sensor.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
                    viewModel.onSensorAccuracyChanged(accuracy)
                }
            }
        }

        when (sensorSource) {
            OrientationSensorSource.ROTATION_VECTOR -> {
                sensorManager.registerListener(
                    listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME
                )
            }

            OrientationSensorSource.GEOMAGNETIC_ROTATION_VECTOR -> {
                sensorManager.registerListener(
                    listener,
                    geomagneticRotationSensor,
                    SensorManager.SENSOR_DELAY_GAME,
                )
            }

            OrientationSensorSource.ACCELEROMETER_MAGNETOMETER -> {
                sensorManager.registerListener(
                    listener, accelerometer, SensorManager.SENSOR_DELAY_GAME
                )
                sensorManager.registerListener(
                    listener, magnetometer, SensorManager.SENSOR_DELAY_GAME
                )
            }
        }

        onDispose { sensorManager.unregisterListener(listener) }
    }

    QiblaScreenContent(
        appName = appName,
        uiState = uiState,
        onSettingsClick = onSettingsClick,
        onRewardsClick = onRewardsClick,
        onRefreshLocation = {
            if (hasLocationPermission(context)) {
                viewModel.onPermissionStateChanged(true)
                viewModel.requestLocationRefresh()
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        },
        resources = resources,
        bannerAdContent = bannerAdContent,
        nativeAdContent = nativeAdContent,
    )
}

@Composable
private fun QiblaScreenContent(
    appName: String,
    uiState: QiblaUiState,
    onSettingsClick: () -> Unit,
    onRewardsClick: () -> Unit,
    onRefreshLocation: () -> Unit,
    resources: android.content.res.Resources,
    bannerAdContent: @Composable () -> Unit,
    nativeAdContent: @Composable () -> Unit,
) {
    val alignmentText = buildAlignmentText(resources, uiState.signedDeltaToQibla)
    val sensorLabel = stringResource(
        when (uiState.sensorSource) {
            OrientationSensorSource.ROTATION_VECTOR -> R.string.qibla_sensor_rotation_vector
            OrientationSensorSource.GEOMAGNETIC_ROTATION_VECTOR -> R.string.qibla_sensor_geomagnetic_rotation
            OrientationSensorSource.ACCELEROMETER_MAGNETOMETER -> R.string.qibla_sensor_accel_magnetometer
            OrientationSensorSource.UNAVAILABLE -> R.string.qibla_sensor_unavailable
        },
    )
    val confidenceLabel = stringResource(
        when (uiState.confidenceLevel) {
            QiblaConfidenceLevel.EXCELLENT -> R.string.qibla_confidence_excellent
            QiblaConfidenceLevel.GOOD -> R.string.qibla_confidence_good
            QiblaConfidenceLevel.FAIR -> R.string.qibla_confidence_fair
            QiblaConfidenceLevel.LOW -> R.string.qibla_confidence_low
        },
    )
    val locationLabel = when (uiState.locationStatus) {
        QiblaLocationStatus.LIVE -> stringResource(
            R.string.qibla_location_format,
            String.format(Locale.US, "%.4f", uiState.latitude ?: DEFAULT_LAT),
            String.format(Locale.US, "%.4f", uiState.longitude ?: DEFAULT_LON),
        )

        QiblaLocationStatus.UNAVAILABLE -> stringResource(R.string.qibla_location_unavailable)
        QiblaLocationStatus.FALLBACK_DEFAULT -> stringResource(R.string.qibla_location_fallback)
    }
    val showCalibration =
        uiState.sensorAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE || uiState.sensorAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
    val confidenceColor = when (uiState.confidenceLevel) {
        QiblaConfidenceLevel.EXCELLENT -> Color(0xFF2E7D32)
        QiblaConfidenceLevel.GOOD -> Color(0xFF388E3C)
        QiblaConfidenceLevel.FAIR -> Color(0xFFEF6C00)
        QiblaConfidenceLevel.LOW -> MaterialTheme.colorScheme.error
    }

    Box(
        modifier = Modifier
            .testTag("qibla_ready")
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 14.dp,
                end = 16.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                QiblaAppHeader(
                    appName = appName,
                    onSettingsClick = onSettingsClick,
                    onRewardsClick = onRewardsClick,
                )
            }
            item { bannerAdContent() }
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = alignmentText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(
                                R.string.qibla_heading_value, uiState.qiblaBearing.roundToInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(
                                R.string.qibla_confidence_format,
                                uiState.confidenceScore,
                                confidenceLabel,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = confidenceColor,
                            fontWeight = FontWeight.Medium,
                        )
                        LinearProgressIndicator(
                            progress = { uiState.confidenceScore / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = confidenceColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompassCanvas(
                            modifier = Modifier.size(320.dp),
                            relativeToQibla = uiState.relativeToQibla,
                        )
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                    shape = CircleShape,
                                )
                                .padding(10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.kabe),
                                contentDescription = stringResource(R.string.qibla_kaaba_image_desc),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        InfoRow(
                            icon = Icons.Default.Navigation,
                            label = stringResource(
                                R.string.qibla_compass_value,
                                uiState.headingTrueNorth.roundToInt(),
                            ),
                        )
                        InfoRow(
                            icon = Icons.Default.Sensors,
                            label = stringResource(R.string.qibla_sensor_format, sensorLabel),
                        )
                        InfoRow(
                            icon = Icons.Default.MyLocation,
                            label = locationLabel,
                        )
                        if (uiState.isLocationRefreshing) {
                            Text(
                                text = stringResource(R.string.qibla_location_refreshing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        uiState.locationAccuracyMeters?.let {
                            Text(
                                text = stringResource(R.string.qibla_location_accuracy_format, it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (showCalibration) {
                            Text(
                                text = stringResource(R.string.qibla_calibration_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onRefreshLocation) {
                        Text(text = stringResource(R.string.qibla_refresh_location))
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.qibla_instruction),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { nativeAdContent() }
        }
    }
}

@Composable
private fun QiblaAppHeader(
    appName: String,
    onSettingsClick: () -> Unit,
    onRewardsClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val dimens = LocalDimens.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorScheme.primaryContainer.copy(alpha = 0.95f),
        shape = RoundedCornerShape(
            topStart = dimens.radiusLarge,
            topEnd = dimens.radiusLarge,
            bottomStart = dimens.radiusLarge,
            bottomEnd = dimens.radiusLarge,
        ),
        tonalElevation = dimens.elevationMedium,
        shadowElevation = dimens.elevationHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.space6, vertical = dimens.space6),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(dimens.iconXl)
                    .background(colorScheme.secondaryContainer.copy(alpha = 0.24f), CircleShape)
                    .border(
                        width = dimens.stroke,
                        color = colorScheme.secondary.copy(alpha = 0.35f),
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.qibla_settings),
                    tint = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(dimens.iconMd),
                )
            }

            Text(
                text = appName,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )

            IconButton(
                onClick = onRewardsClick,
                modifier = Modifier
                    .size(dimens.iconXl)
                    .background(colorScheme.secondaryContainer.copy(alpha = 0.24f), CircleShape)
                    .border(
                        width = dimens.stroke,
                        color = colorScheme.secondary.copy(alpha = 0.35f),
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.CardGiftcard,
                    contentDescription = stringResource(R.string.qibla_rewards),
                    tint = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(dimens.iconMd),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CompassCanvas(
    modifier: Modifier,
    relativeToQibla: Float,
) {
    val ringColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val guideColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val qiblaNeedleColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    val northMarkerColor = Color(0xFFD32F2F)
    val safeRelativeToQibla = if (relativeToQibla.isFinite()) relativeToQibla else 0f
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val innerRadius = radius * 0.82f

        drawCircle(color = fillColor, radius = radius, center = center)
        drawCircle(color = ringColor, radius = radius, center = center, style = Stroke(width = 8f))
        drawCircle(
            color = guideColor, radius = innerRadius, center = center, style = Stroke(width = 2.5f)
        )

        repeat(12) { index ->
            val angle = Math.toRadians((index * 30 - 90).toDouble())
            val outer = Offset(
                x = center.x + radius * cos(angle).toFloat(),
                y = center.y + radius * sin(angle).toFloat(),
            )
            val inner = Offset(
                x = center.x + (radius - if (index % 3 == 0) 24f else 14f) * cos(angle).toFloat(),
                y = center.y + (radius - if (index % 3 == 0) 24f else 14f) * sin(angle).toFloat(),
            )
            drawLine(
                color = if (index % 3 == 0) ringColor else guideColor,
                start = inner,
                end = outer,
                strokeWidth = if (index % 3 == 0) 5f else 2f,
                cap = StrokeCap.Round,
            )
        }

        val qiblaRad = Math.toRadians((safeRelativeToQibla - 90f).toDouble())
        val needleEnd = Offset(
            x = center.x + radius * 0.74f * cos(qiblaRad).toFloat(),
            y = center.y + radius * 0.74f * sin(qiblaRad).toFloat(),
        )

        drawLine(
            color = qiblaNeedleColor,
            start = center,
            end = needleEnd,
            strokeWidth = 10f,
            cap = StrokeCap.Round,
        )
        drawCircle(color = qiblaNeedleColor, radius = 13f, center = needleEnd)

        drawLine(
            color = northMarkerColor,
            start = Offset(center.x, center.y - radius + 14f),
            end = Offset(center.x, center.y - radius + 40f),
            strokeWidth = 8f,
            cap = StrokeCap.Round,
        )
        drawArc(
            color = qiblaNeedleColor.copy(alpha = 0.2f),
            startAngle = -90f,
            sweepAngle = safeRelativeToQibla,
            useCenter = false,
            topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
            size = Size(width = innerRadius * 2f, height = innerRadius * 2f),
            style = Stroke(width = 6f),
        )
    }
}

private fun buildAlignmentText(
    resources: android.content.res.Resources,
    signedDelta: Float,
): String {
    val absDelta = abs(signedDelta)
    return when {
        absDelta <= 3f -> resources.getString(R.string.qibla_aligned)
        signedDelta > 0f -> resources.getString(
            R.string.qibla_turn_right_format, absDelta.roundToInt()
        )

        else -> resources.getString(R.string.qibla_turn_left_format, absDelta.roundToInt())
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
private suspend fun resolveBestLocation(context: Context): Location? {
    val fused = LocationServices.getFusedLocationProviderClient(context)
    val currentRequest =
        CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(7_500L).setMaxUpdateAgeMillis(60_000L).build()

    val current = try {
        withTimeoutOrNull(8_500L) {
            val cancellation = CancellationTokenSource()
            fused.getCurrentLocation(currentRequest, cancellation.token).await()
        }
    } catch (_: SecurityException) {
        null
    }

    if (current != null) return current

    return try {
        withTimeoutOrNull(2_000L) { fused.lastLocation.await() }
    } catch (_: SecurityException) {
        null
    }
}

private fun remapForDisplay(
    source: FloatArray,
    destination: FloatArray,
    displayRotation: Int,
) {
    when (displayRotation) {
        Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
            source,
            SensorManager.AXIS_Y,
            SensorManager.AXIS_MINUS_X,
            destination,
        )

        Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
            source,
            SensorManager.AXIS_MINUS_X,
            SensorManager.AXIS_MINUS_Y,
            destination,
        )

        Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
            source,
            SensorManager.AXIS_MINUS_Y,
            SensorManager.AXIS_X,
            destination,
        )

        else -> source.copyInto(destination)
    }
}

private const val SENSOR_SMOOTHING_ALPHA = 0.18f

@Preview(showBackground = true)
@Composable
private fun QiblaScreenContentPreviewHighConfidence() {
    AppTheme(flavorName = "kible") {
        QiblaScreenContent(
            appName = "Kıble Pusulası",
            uiState = QiblaUiState(
                qiblaBearing = 154f,
                headingTrueNorth = 152f,
                relativeToQibla = 2f,
                signedDeltaToQibla = 2f,
                hasLocationPermission = true,
                locationStatus = QiblaLocationStatus.LIVE,
                latitude = 41.015,
                longitude = 28.979,
                locationAccuracyMeters = 12,
                sensorSource = OrientationSensorSource.ROTATION_VECTOR,
                sensorAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
                confidenceScore = 94,
                confidenceLevel = QiblaConfidenceLevel.EXCELLENT,
            ),
            onSettingsClick = {},
            onRewardsClick = {},
            onRefreshLocation = {},
            resources = LocalResources.current,
            bannerAdContent = {},
            nativeAdContent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun QiblaScreenContentPreviewLowConfidence() {
    AppTheme(flavorName = "kible") {
        QiblaScreenContent(
            appName = "Kıble Pusulası",
            uiState = QiblaUiState(
                qiblaBearing = 154f,
                headingTrueNorth = 100f,
                relativeToQibla = 54f,
                signedDeltaToQibla = 54f,
                hasLocationPermission = false,
                locationStatus = QiblaLocationStatus.FALLBACK_DEFAULT,
                sensorSource = OrientationSensorSource.ACCELEROMETER_MAGNETOMETER,
                sensorAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE,
                confidenceScore = 28,
                confidenceLevel = QiblaConfidenceLevel.LOW,
            ),
            onSettingsClick = {},
            onRewardsClick = {},
            onRefreshLocation = {},
            resources = LocalResources.current,
            bannerAdContent = {},
            nativeAdContent = {},
        )
    }
}
