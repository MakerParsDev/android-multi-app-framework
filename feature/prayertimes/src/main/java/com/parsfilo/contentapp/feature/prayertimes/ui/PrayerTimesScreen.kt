package com.parsfilo.contentapp.feature.prayertimes.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.feature.prayertimes.R
import com.parsfilo.contentapp.feature.prayertimes.data.PRAYER_AKSAM
import com.parsfilo.contentapp.feature.prayertimes.data.PRAYER_GUNES
import com.parsfilo.contentapp.feature.prayertimes.data.PRAYER_IKINDI
import com.parsfilo.contentapp.feature.prayertimes.data.PRAYER_IMSAK
import com.parsfilo.contentapp.feature.prayertimes.data.PRAYER_OGLE
import com.parsfilo.contentapp.feature.prayertimes.data.PRAYER_YATSI
import com.parsfilo.contentapp.feature.prayertimes.data.PrayerDateTime
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerAppVariant
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerTimesDay
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerTimesMode
import com.parsfilo.contentapp.feature.prayertimes.ui.components.AlarmSettingsBottomSheet
import com.parsfilo.contentapp.feature.prayertimes.ui.components.NextPrayerCountdownCard
import com.parsfilo.contentapp.feature.prayertimes.ui.components.PrayerTimesAppHeader
import com.parsfilo.contentapp.feature.prayertimes.ui.components.PrayerTimesBackground
import com.parsfilo.contentapp.feature.prayertimes.ui.components.PrayerTimesListSection
import com.parsfilo.contentapp.feature.prayertimes.ui.components.prayerItemsForVariant
import kotlinx.coroutines.launch

@Composable
fun PrayerTimesRoute(
    appName: String,
    variant: PrayerAppVariant,
    bannerAdContent: (@Composable () -> Unit)? = null,
    nativeAdContent: (@Composable () -> Unit)? = null,
    onOpenLocationPicker: () -> Unit,
    onOpenRewards: () -> Unit,
    onOpenQibla: () -> Unit,
    viewModel: PrayerTimesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackMessage by viewModel.snackBarMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val permissionNeededText = stringResource(R.string.prayertimes_permission_needed)
    val scope = rememberCoroutineScope()

    val requestLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            viewModel.onModeChanged(PrayerTimesMode.AUTO)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    permissionNeededText,
                )
            }
        }
    }

    LaunchedEffect(snackMessage) {
        snackMessage?.asString(context)?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackMessage()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PrayerTimesUiEvent.OpenLocationPicker -> onOpenLocationPicker()
            }
        }
    }

    PrayerTimesScreen(
        appName = appName,
        variant = variant,
        uiState = uiState,
        onAlarmEnabledChanged = viewModel::onAlarmEnabledChanged,
        onAlarmOffsetChanged = viewModel::onAlarmOffsetChanged,
        onAlarmPrayerKeysChanged = viewModel::onAlarmPrayerKeysChanged,
        onAlarmSoundUriChanged = viewModel::onAlarmSoundUriChanged,
        onTestAlarmSound = viewModel::onTestAlarmSound,
        onAutoModeClick = {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                viewModel.onModeChanged(PrayerTimesMode.AUTO)
            } else {
                requestLocationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        },
        onManualModeClick = {
            viewModel.onModeChanged(PrayerTimesMode.MANUAL)
            onOpenLocationPicker()
        },
        onRefresh = viewModel::refresh,
        onResolveByDeviceLocation = viewModel::resolveByDeviceLocation,
        onConfirmResolvedLocation = viewModel::confirmResolvedLocationSuggestion,
        onRejectResolvedLocation = viewModel::rejectResolvedLocationSuggestion,
        onOpenLocationPicker = onOpenLocationPicker,
        onOpenRewards = onOpenRewards,
        snackbarHostState = snackbarHostState,
        bannerAdContent = bannerAdContent,
        nativeAdContent = nativeAdContent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen(
    appName: String,
    variant: PrayerAppVariant,
    uiState: PrayerTimesUiState,
    onAlarmEnabledChanged: (Boolean) -> Unit,
    onAlarmOffsetChanged: (Int) -> Unit,
    onAlarmPrayerKeysChanged: (Set<String>) -> Unit,
    onAlarmSoundUriChanged: (String?) -> Unit,
    onTestAlarmSound: (String?) -> Unit,
    onAutoModeClick: () -> Unit,
    onManualModeClick: () -> Unit,
    onRefresh: () -> Unit,
    onResolveByDeviceLocation: () -> Unit,
    onConfirmResolvedLocation: () -> Unit,
    onRejectResolvedLocation: () -> Unit,
    onOpenLocationPicker: () -> Unit,
    onOpenRewards: () -> Unit,
    snackbarHostState: SnackbarHostState,
    bannerAdContent: (@Composable () -> Unit)? = null,
    nativeAdContent: (@Composable () -> Unit)? = null,
) {
    val showAlarmSheetState = remember { mutableStateOf(false) }
    val performanceReadyModifier =
        if (!uiState.isRefreshing) {
            Modifier.testTag("prayer_times_ready")
        } else {
            Modifier
        }

    PrayerTimesBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
            ),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = performanceReadyModifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        PrayerTimesAppHeader(
                            appName = appName,
                            onSettingsClick = onOpenLocationPicker,
                            onRewardsClick = onOpenRewards,
                        )
                    }

                    item { bannerAdContent?.invoke() }

                    if (uiState.days.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.prayertimes_no_data),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        when (variant) {
                            PrayerAppVariant.NAMAZ_VAKITLERI -> {
                                item {
                                    PrayerTimesListSection(
                                        variant = variant,
                                        upcomingDays = uiState.days,
                                        selectedAlarmPrayerKeys = uiState.alarmSettings.selectedPrayerKeys,
                                        onToggleAlarm = { prayerKey ->
                                            onAlarmPrayerKeysChanged(
                                                togglePrayerKey(
                                                    allKeys = prayerItemsForVariant(variant).map { it.key }.toSet(),
                                                    selectedKeys = uiState.alarmSettings.selectedPrayerKeys,
                                                    prayerKey = prayerKey,
                                                )
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.nextPrayer != null) {
                        item {
                            NextPrayerCountdownCard(
                                nextPrayerLabel = prayerLabelText(uiState.nextPrayer.prayerKey),
                                nextPrayerTime = uiState.nextPrayer.timeHm,
                                countdown = uiState.nextPrayerCountdown,
                            )
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = PrayerTimesDesignTokens.GlassSurface.copy(alpha = 0.86f),
                            ),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppButton(
                                        text = stringResource(R.string.prayertimes_mode_auto),
                                        onClick = onAutoModeClick,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = modeButtonColors(selected = uiState.mode == PrayerTimesMode.AUTO),
                                        modifier = Modifier.weight(1f),
                                    )
                                    AppButton(
                                        text = stringResource(R.string.prayertimes_mode_manual),
                                        onClick = onManualModeClick,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = modeButtonColors(selected = uiState.mode == PrayerTimesMode.MANUAL),
                                        modifier = Modifier.weight(1f),
                                    )
                                }

                                AppButton(
                                    text = stringResource(R.string.prayertimes_open_alarm_settings),
                                    onClick = { showAlarmSheetState.value = true },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = primaryActionButtonColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                if (uiState.mode == PrayerTimesMode.AUTO) {
                                    AppButton(
                                        text = stringResource(R.string.prayertimes_use_device_location),
                                        onClick = onResolveByDeviceLocation,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = secondaryActionButtonColors(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        text = if (uiState.isResolvingLocation) {
                                            stringResource(R.string.prayertimes_resolving_location)
                                        } else {
                                            stringResource(R.string.prayertimes_auto_mode_hint)
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = prayerBodyFontFamily()),
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Text(
                                        text = stringResource(R.string.prayertimes_location_provider_attribution),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                                    )
                                }
                            }
                        }
                    }

                    if (!uiState.pendingResolvedLocationName.isNullOrBlank()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = PrayerTimesDesignTokens.GlassSurface.copy(alpha = PrayerTimesDesignTokens.GLASS_ALPHA),
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.prayertimes_resolved_confirmation_title,
                                            uiState.pendingResolvedLocationName,
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = stringResource(R.string.prayertimes_resolved_confirmation_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AppButton(
                                            text = stringResource(R.string.prayertimes_resolved_confirmation_yes),
                                            onClick = onConfirmResolvedLocation,
                                            shape = RoundedCornerShape(16.dp),
                                            colors = primaryActionButtonColors(),
                                            modifier = Modifier.weight(1f),
                                        )
                                        AppButton(
                                            text = stringResource(R.string.prayertimes_resolved_confirmation_no),
                                            onClick = onRejectResolvedLocation,
                                            shape = RoundedCornerShape(16.dp),
                                            colors = secondaryActionButtonColors(),
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (nativeAdContent != null) {
                        item { nativeAdContent.invoke() }
                    }

                    item { Spacer(modifier = Modifier.height(10.dp)) }
                }
            }
        }
    }

    if (showAlarmSheetState.value) {
        AlarmSettingsBottomSheet(
            variant = variant,
            settings = uiState.alarmSettings,
            onDismissRequest = { showAlarmSheetState.value = false },
            onEnabledChanged = onAlarmEnabledChanged,
            onOffsetChanged = onAlarmOffsetChanged,
            onPrayerKeysChanged = onAlarmPrayerKeysChanged,
            onSoundSelected = onAlarmSoundUriChanged,
            onTestSound = onTestAlarmSound,
        )
    }
}

@Composable
private fun primaryActionButtonColors() = ButtonDefaults.buttonColors(
    containerColor = PrayerTimesDesignTokens.ActionPrimary,
    contentColor = PrayerTimesDesignTokens.HeaderText,
    disabledContainerColor = PrayerTimesDesignTokens.ActionPrimary.copy(alpha = 0.45f),
)

@Composable
private fun secondaryActionButtonColors() = ButtonDefaults.buttonColors(
    containerColor = PrayerTimesDesignTokens.GlassSurface.copy(alpha = 0.96f),
    contentColor = MaterialTheme.colorScheme.onSurface,
    disabledContainerColor = PrayerTimesDesignTokens.GlassSurface.copy(alpha = 0.45f),
)

@Composable
private fun modeButtonColors(selected: Boolean) = ButtonDefaults.buttonColors(
    containerColor = if (selected) {
        PrayerTimesDesignTokens.ActionPrimary
    } else {
        PrayerTimesDesignTokens.GlassSurface.copy(alpha = 0.96f)
    },
    contentColor = if (selected) {
        PrayerTimesDesignTokens.HeaderText
    } else {
        MaterialTheme.colorScheme.onSurface
    },
    disabledContainerColor = PrayerTimesDesignTokens.GlassSurface.copy(alpha = 0.45f),
)

@Composable
private fun prayerLabelText(prayerKey: String): String {
    return when (prayerKey) {
        PRAYER_IMSAK -> stringResource(R.string.prayertimes_sahur_imsak)
        PRAYER_GUNES -> stringResource(R.string.prayertimes_sabah_gunes)
        PRAYER_OGLE -> stringResource(R.string.prayertimes_ogle)
        PRAYER_IKINDI -> stringResource(R.string.prayertimes_ikindi)
        PRAYER_AKSAM -> stringResource(R.string.prayertimes_aksam)
        PRAYER_YATSI -> stringResource(R.string.prayertimes_yatsi)
        else -> prayerKey
    }
}

private fun togglePrayerKey(
    allKeys: Set<String>,
    selectedKeys: Set<String>,
    prayerKey: String,
): Set<String> {
    val effective = selectedKeys.ifEmpty { allKeys }
    val next = effective.toMutableSet()
    if (!next.add(prayerKey)) {
        next.remove(prayerKey)
    }
    return if (next.isEmpty()) setOf(prayerKey) else next
}

private fun countdownForPrayer(
    prayerKey: String,
    days: List<PrayerTimesDay>,
    nowMillis: Long,
): String {
    val nextMillis = days.asSequence()
        .mapNotNull { day ->
            val hm = when (prayerKey) {
                PRAYER_IMSAK -> day.imsak
                PRAYER_GUNES -> day.gunes
                PRAYER_OGLE -> day.ogle
                PRAYER_IKINDI -> day.ikindi
                PRAYER_AKSAM -> day.aksam
                PRAYER_YATSI -> day.yatsi
                else -> null
            } ?: return@mapNotNull null
            PrayerDateTime.parseIsoHmToMillis(day.localDate, hm)
        }
        .firstOrNull { it > nowMillis }
        ?: return "--:--:--"

    return PrayerDateTime.formatCountdown(nextMillis - nowMillis)
}
