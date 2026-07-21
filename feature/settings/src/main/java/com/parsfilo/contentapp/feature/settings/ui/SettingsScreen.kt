package com.parsfilo.contentapp.feature.settings.ui

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.messaging.FirebaseMessaging
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.core.designsystem.component.AppCard
import com.parsfilo.contentapp.core.designsystem.component.AppTopBar
import com.parsfilo.contentapp.core.designsystem.theme.app_transparent
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.feature.ads.AdAgeGateStatus
import com.parsfilo.contentapp.feature.ads.AdManager
import com.parsfilo.contentapp.feature.ads.UmpConsentDebugResult
import com.parsfilo.contentapp.feature.ads.UmpDebugGeography
import com.parsfilo.contentapp.feature.settings.R
import kotlinx.coroutines.launch
import java.util.Locale

private const val DEVELOPER_DEVICE_MODEL_PREFIX = "SM-A346E"
private const val DEVELOPER_MODE_REQUIRED_TAPS = 5
private const val DEVELOPER_MODE_MIN_FONT_SIZE = 14

@Composable
fun SettingsRoute(
    onBackClick: () -> Unit = {},
    onPrivacyOptionsUpdated: () -> Unit = {},
    updateDebugSummary: String? = null,
    onUpdateDebugFetchNow: () -> Unit = {},
    onUpdateDebugSimulateSoft: () -> Unit = {},
    onUpdateDebugSimulateHard: () -> Unit = {},
    onUpdateDebugClearSimulation: () -> Unit = {},
    onUpdateDebugResetSoftPrompt: () -> Unit = {},
    onRetryPushRegistration: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onDarkModeChanged = viewModel::setDarkMode,
        onFontSizeChanged = viewModel::setFontSize,
        onDeveloperModeChanged = viewModel::setDeveloperModeEnabled,
        onNotificationsChanged = viewModel::setNotificationsEnabled,
        onPrivacyOptionsUpdated = onPrivacyOptionsUpdated,
        onAdsAgeGateChanged = viewModel::setAdsAgeGateStatus,
        onResetConsent = viewModel::resetConsent,
        onSetConsentDebugGeography = viewModel::setConsentDebugGeography,
        adManager = viewModel.adManager(),
        updateDebugSummary = updateDebugSummary,
        onUpdateDebugFetchNow = onUpdateDebugFetchNow,
        onUpdateDebugSimulateSoft = onUpdateDebugSimulateSoft,
        onUpdateDebugSimulateHard = onUpdateDebugSimulateHard,
        onUpdateDebugClearSimulation = onUpdateDebugClearSimulation,
        onUpdateDebugResetSoftPrompt = onUpdateDebugResetSoftPrompt,
        onRetryPushRegistration = { viewModel.retryPushRegistrationNow() },
        onShareApp = { platform -> viewModel.logShareApp(platform) },
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBackClick: () -> Unit = {},
    onDarkModeChanged: (Boolean) -> Unit,
    onFontSizeChanged: (Int) -> Unit,
    onDeveloperModeChanged: (Boolean) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onPrivacyOptionsUpdated: () -> Unit = {},
    onAdsAgeGateChanged: (AdAgeGateStatus) -> Unit,
    onResetConsent: () -> Unit,
    onSetConsentDebugGeography: (UmpDebugGeography) -> Unit,
    adManager: AdManager,
    updateDebugSummary: String? = null,
    onUpdateDebugFetchNow: () -> Unit = {},
    onUpdateDebugSimulateSoft: () -> Unit = {},
    onUpdateDebugSimulateHard: () -> Unit = {},
    onUpdateDebugClearSimulation: () -> Unit = {},
    onUpdateDebugResetSoftPrompt: () -> Unit = {},
    onRetryPushRegistration: () -> Unit = {},
    onShareApp: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val nonFatalSentMessage = stringResource(R.string.settings_crash_debug_non_fatal_sent)
    val activity = remember(context) { context.findActivity() }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val showMessage: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var systemNotificationsEnabled by remember {
        mutableStateOf(isNotificationPermissionEnabled(context))
    }
    var isPrivacyOptionsRequired by remember { mutableStateOf(false) }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            systemNotificationsEnabled = isNotificationPermissionEnabled(context)
            onNotificationsChanged(granted && systemNotificationsEnabled)
        }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    systemNotificationsEnabled = isNotificationPermissionEnabled(context)
                    val status = adManager.privacyOptionsRequired.value
                    isPrivacyOptionsRequired = status
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    val languageChangedText = stringResource(R.string.settings_language_changed)
    val deviceIdCopiedText = stringResource(R.string.settings_device_id_copied)
    val developerModeEnabledText = stringResource(R.string.settings_developer_mode_enabled)
    val fcmDebugRefreshedText = stringResource(R.string.settings_fcm_debug_refreshed)
    val fcmDebugErrorText = stringResource(R.string.settings_fcm_debug_error)
    val fcmDebugCopiedText = stringResource(R.string.settings_fcm_debug_copied)
    val pushRetryStartedText = stringResource(R.string.settings_push_retry_started)
    val privacyOptionsUnavailableText =
        stringResource(R.string.settings_privacy_options_unavailable)
    val privacyOptionsErrorText = stringResource(R.string.settings_privacy_options_error)
    val privacyOptionsSavedText = stringResource(R.string.settings_privacy_options_saved)
    val adInspectorOpenedText = stringResource(R.string.settings_ads_debug_ad_inspector_opened)
    val adInspectorErrorText = stringResource(R.string.settings_ads_debug_ad_inspector_error)
    val consentResetText = stringResource(R.string.settings_ads_debug_consent_reset)
    val consentFormShownText = stringResource(R.string.settings_ads_debug_consent_form_done)
    val consentFormErrorText = stringResource(R.string.settings_ads_debug_consent_form_error)
    val consentFormNotRequiredText =
        stringResource(R.string.settings_ads_debug_consent_form_not_required)
    val consentFormActuallyShownText =
        stringResource(R.string.settings_ads_debug_consent_form_shown)
    val adsConfigUpdatedText = stringResource(R.string.settings_ads_debug_ads_config_updated)
    val debugGeoSetResetHintText = stringResource(R.string.settings_ads_debug_geo_set_reset_hint)
    val updateDebugFetchText = stringResource(R.string.settings_update_debug_fetch_now)
    val updateDebugFetchStartedText = stringResource(R.string.settings_update_debug_fetch_started)
    val updateDebugSimSoftText = stringResource(R.string.settings_update_debug_simulate_soft)
    val updateDebugSimHardText = stringResource(R.string.settings_update_debug_simulate_hard)
    val updateDebugClearSimText = stringResource(R.string.settings_update_debug_clear_simulation)
    val updateDebugResetSoftText = stringResource(R.string.settings_update_debug_reset_soft_session)
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var ageGateMenuExpanded by remember { mutableStateOf(false) }
    var developerTapCount by remember { mutableIntStateOf(0) }
    val currentPreferences = (uiState as? SettingsUiState.Success)?.preferences
    val isDeveloperDevice =
        remember {
            listOf(Build.MODEL, Build.DEVICE, Build.PRODUCT).any { candidate ->
                candidate.contains(
                    DEVELOPER_DEVICE_MODEL_PREFIX,
                    ignoreCase = true,
                )
            }
        }

    val languageOptions =
        remember {
            listOf(
                LanguageOption(tag = "", labelRes = R.string.settings_language_system),
                LanguageOption(tag = "tr", labelRes = R.string.settings_language_tr),
                LanguageOption(tag = "en", labelRes = R.string.settings_language_en),
                LanguageOption(tag = "de", labelRes = R.string.settings_language_de),
            )
        }

    // Per-app locales:
    // - API 33+: LocaleManager (platform)
    // - API 32-: AppCompat storage
    val appLocaleTags =
        if (Build.VERSION.SDK_INT >= 33) {
            val lm = context.getSystemService(android.app.LocaleManager::class.java)
            lm?.applicationLocales?.toLanguageTags().orEmpty()
        } else {
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }
    val effectiveLanguageTag = appLocaleTags.ifBlank { LocalLocale.current.platformLocale.toLanguageTag() }

    LaunchedEffect(Unit) {
        isPrivacyOptionsRequired = adManager.privacyOptionsRequired.value
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            AppTopBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationIconContentDescription = stringResource(R.string.settings_back),
                onNavigationClick = onBackClick,
                onTitleClick = {
                    if (
                        !isDeveloperDevice ||
                        currentPreferences == null ||
                        currentPreferences.fontSize > DEVELOPER_MODE_MIN_FONT_SIZE
                    ) {
                        developerTapCount = 0
                        return@AppTopBar
                    }
                    val nextTapCount = developerTapCount + 1
                    if (nextTapCount >= DEVELOPER_MODE_REQUIRED_TAPS) {
                        developerTapCount = 0
                        if (!currentPreferences.developerModeEnabled) {
                            onDeveloperModeChanged(true)
                            showMessage(developerModeEnabledText)
                        }
                    } else {
                        developerTapCount = nextTapCount
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = app_transparent,
                    ),
                titleStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal),
            ) {
                Box {
                    TextButton(
                        onClick = { languageMenuExpanded = true },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = stringResource(R.string.settings_language),
                            tint = colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(dimens.space6))
                        Text(
                            text = effectiveLanguageTag.uppercase(Locale.ROOT).take(2),
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    DropdownMenu(
                        expanded = languageMenuExpanded,
                        onDismissRequest = { languageMenuExpanded = false },
                    ) {
                        languageOptions.forEach { option ->
                            val isSelected = option.matches(appLocaleTags)
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(option.labelRes),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                        if (isSelected) {
                                            Text(
                                                text = "✓",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        val localeManager =
                                            (
                                                activity
                                                    ?: context
                                            ).getSystemService(android.app.LocaleManager::class.java)
                                        val list =
                                            if (option.tag.isBlank()) {
                                                LocaleList.getEmptyLocaleList()
                                            } else {
                                                LocaleList.forLanguageTags(option.tag)
                                            }
                                        localeManager?.applicationLocales = list
                                    } else {
                                        val locales =
                                            if (option.tag.isBlank()) {
                                                LocaleListCompat.getEmptyLocaleList()
                                            } else {
                                                LocaleListCompat.forLanguageTags(option.tag)
                                            }
                                        AppCompatDelegate.setApplicationLocales(locales)
                                    }
                                    languageMenuExpanded = false
                                    showMessage(languageChangedText)

                                    // Ensure resources are reloaded immediately on this screen.
                                    activity?.recreate()
                                },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                thickness = dimens.stroke,
                color = colorScheme.outline.copy(alpha = 0.3f),
                modifier = Modifier.padding(horizontal = dimens.space16),
            )

            when (uiState) {
                SettingsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colorScheme.primary)
                    }
                }

                is SettingsUiState.Success -> {
                    val preferences = uiState.preferences
                    val notificationsChecked =
                        preferences.notificationsEnabled && systemNotificationsEnabled
                    val isDebugBuild =
                        remember(context) {
                            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                        }
                    val canShowDeveloperTools = isDebugBuild || preferences.developerModeEnabled
                    val debugGeography by adManager.debugGeography.collectAsStateWithLifecycle()
                    val lastRequestDebugGeography by adManager.lastRequestDebugGeography.collectAsStateWithLifecycle()
                    val canRequestAds by adManager.canRequestAds.collectAsStateWithLifecycle()
                    val lastConsentDebugResult by adManager.lastConsentDebugResult.collectAsStateWithLifecycle()
                    var debugFcmToken by remember(preferences.lastPushToken) {
                        mutableStateOf(preferences.lastPushToken)
                    }
                    var isFetchingDebugToken by remember { mutableStateOf(false) }
                    var debugFcmTokenError by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(systemNotificationsEnabled, preferences.notificationsEnabled) {
                        if (!systemNotificationsEnabled && preferences.notificationsEnabled) {
                            onNotificationsChanged(false)
                        }
                    }

                    val listItemColors =
                        ListItemDefaults.colors(
                            containerColor = app_transparent,
                            headlineColor = colorScheme.onSurface,
                            supportingColor = colorScheme.onSurfaceVariant,
                        )
                    val switchColors =
                        SwitchDefaults.colors(
                            checkedThumbColor = colorScheme.onPrimary,
                            checkedTrackColor = colorScheme.primary,
                            uncheckedThumbColor = colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = colorScheme.surfaceVariant,
                        )
                    val currentAgeGateStatus =
                        remember(preferences.adsAgeGateStatus) {
                            AdAgeGateStatus.fromStorage(preferences.adsAgeGateStatus)
                        }
                    val ageGateOptions =
                        remember {
                            listOf(
                                AdAgeGateStatus.UNKNOWN to R.string.settings_ads_age_unknown,
                                AdAgeGateStatus.UNDER_13 to R.string.settings_ads_age_under_13,
                                AdAgeGateStatus.AGE_13_TO_15 to R.string.settings_ads_age_13_to_15,
                                AdAgeGateStatus.AGE_16_OR_OVER to R.string.settings_ads_age_16_or_over,
                            )
                        }

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(horizontal = dimens.space8, vertical = dimens.space8),
                    ) {
                        // Bildirimler toggle
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.settings_notifications))
                            },
                            supportingContent = {
                                Text(stringResource(R.string.settings_notifications_desc))
                            },
                            trailingContent = {
                                Switch(
                                    checked = notificationsChecked,
                                    onCheckedChange = { enabled ->
                                        if (!enabled) {
                                            onNotificationsChanged(false)
                                            return@Switch
                                        }

                                        if (isNotificationPermissionEnabled(context)) {
                                            systemNotificationsEnabled = true
                                            onNotificationsChanged(true)
                                            return@Switch
                                        }

                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS,
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notificationPermissionLauncher.launch(
                                                Manifest.permission.POST_NOTIFICATIONS,
                                            )
                                        } else {
                                            openAppNotificationSettings(context)
                                        }
                                    },
                                    colors = switchColors,
                                )
                            },
                            colors = listItemColors,
                        )

                        if (isPrivacyOptionsRequired) {
                            HorizontalDivider(
                                color = colorScheme.outline.copy(alpha = 0.3f),
                                modifier = Modifier.padding(horizontal = dimens.space16),
                            )

                            ListItem(
                                headlineContent = {
                                    Text(stringResource(R.string.settings_privacy_options))
                                },
                                supportingContent = {
                                    Text(stringResource(R.string.settings_privacy_options_desc_required))
                                },
                                trailingContent = {
                                    TextButton(
                                        onClick = {
                                            if (activity == null) {
                                                showMessage(privacyOptionsUnavailableText)
                                                return@TextButton
                                            }
                                            adManager.showPrivacyOptions(activity) { success ->
                                                if (success) {
                                                    onPrivacyOptionsUpdated()
                                                    showMessage(privacyOptionsSavedText)
                                                } else {
                                                    showMessage(privacyOptionsErrorText)
                                                }
                                                isPrivacyOptionsRequired =
                                                    adManager.privacyOptionsRequired.value
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.settings_privacy_options_button))
                                    }
                                },
                                colors = listItemColors,
                            )

                            HorizontalDivider(
                                color = colorScheme.outline.copy(alpha = 0.3f),
                                modifier = Modifier.padding(horizontal = dimens.space16),
                            )
                        }

                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.settings_ads_age_gate_title),
                                )
                            },
                            supportingContent = {
                                Text(stringResource(R.string.settings_ads_age_gate_desc))
                            },
                            trailingContent = {
                                Box {
                                    TextButton(onClick = { ageGateMenuExpanded = true }) {
                                        Text(
                                            text =
                                                stringResource(
                                                    ageGateOptions
                                                        .firstOrNull {
                                                            it.first == currentAgeGateStatus
                                                        }?.second
                                                        ?: R.string.settings_ads_age_unknown,
                                                ),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = ageGateMenuExpanded,
                                        onDismissRequest = { ageGateMenuExpanded = false },
                                    ) {
                                        ageGateOptions.forEach { (status, labelRes) ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(labelRes)) },
                                                onClick = {
                                                    ageGateMenuExpanded = false
                                                    onAdsAgeGateChanged(status)
                                                    if (activity != null) {
                                                        adManager.onAdsConfigChanged(activity) { _ ->
                                                            onPrivacyOptionsUpdated()
                                                            showMessage(adsConfigUpdatedText)
                                                            isPrivacyOptionsRequired =
                                                                adManager.privacyOptionsRequired.value
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                            colors = listItemColors,
                        )

                        HorizontalDivider(
                            color = colorScheme.outline.copy(alpha = 0.3f),
                            modifier = Modifier.padding(horizontal = dimens.space16),
                        )

                        // Dark Mode toggle
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.settings_dark_mode))
                            },
                            trailingContent = {
                                Switch(
                                    checked = preferences.darkMode,
                                    onCheckedChange = onDarkModeChanged,
                                    colors = switchColors,
                                )
                            },
                            colors = listItemColors,
                        )

                        HorizontalDivider(
                            color = colorScheme.outline.copy(alpha = 0.3f),
                            modifier = Modifier.padding(horizontal = dimens.space16),
                        )

                        // Font Size slider
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(
                                        R.string.settings_font_size_value,
                                        preferences.fontSize,
                                    ),
                                )
                            },
                            supportingContent = {
                                Slider(
                                    value = preferences.fontSize.toFloat(),
                                    onValueChange = { onFontSizeChanged(it.toInt()) },
                                    valueRange = DEVELOPER_MODE_MIN_FONT_SIZE.toFloat()..40f,
                                    colors =
                                        SliderDefaults.colors(
                                            thumbColor = colorScheme.primary,
                                            activeTrackColor = colorScheme.primary,
                                            inactiveTrackColor = colorScheme.surfaceVariant,
                                        ),
                                )
                            },
                            colors = listItemColors,
                        )

                        HorizontalDivider(
                            color = colorScheme.outline.copy(alpha = 0.3f),
                            modifier = Modifier.padding(horizontal = dimens.space16),
                        )

                        Spacer(modifier = Modifier.height(dimens.space8))

                        AppCard(
                            modifier = Modifier.padding(horizontal = dimens.space8),
                            shape = MaterialTheme.shapes.large,
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    contentColor = colorScheme.onSurfaceVariant,
                                ),
                        ) {
                            Column(modifier = Modifier.padding(dimens.space16)) {
                                Text(
                                    text = stringResource(R.string.settings_share_app),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(dimens.space4))
                                Text(
                                    text = stringResource(R.string.settings_share_app_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(dimens.space12))
                                AppButton(
                                    text = stringResource(R.string.settings_share_app_button),
                                    onClick = {
                                        onShareApp("system_share")
                                        shareApp(context)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors =
                                        androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = colorScheme.primary,
                                            contentColor = colorScheme.onPrimary,
                                        ),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(dimens.space8))

                        if (canShowDeveloperTools) {
                            AppCard(
                                modifier = Modifier.padding(horizontal = dimens.space8),
                                shape = MaterialTheme.shapes.large,
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        contentColor = colorScheme.onSurfaceVariant,
                                    ),
                            ) {
                                Column(modifier = Modifier.padding(dimens.space16)) {
                                    Text(
                                        text = stringResource(R.string.settings_device_id_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space4))
                                    Text(
                                        text = stringResource(R.string.settings_device_id_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space10))
                                    Text(
                                        text = preferences.installationId.ifBlank { "..." },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space6))
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.settings_push_health_status,
                                                if (preferences.hasPushToken) {
                                                    stringResource(R.string.settings_push_health_ready)
                                                } else {
                                                    stringResource(R.string.settings_push_health_missing)
                                                },
                                                preferences.lastPushSyncSuccessAt.formatDebugTime(),
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    if (preferences.lastPushSyncFailureReason.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(dimens.space4))
                                        Text(
                                            text =
                                                stringResource(
                                                    R.string.settings_push_health_failure,
                                                    preferences.lastPushSyncFailureReason,
                                                ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.error,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(dimens.space12))
                                    AppButton(
                                        text = stringResource(R.string.settings_device_id_copy),
                                        onClick = {
                                            if (preferences.installationId.isBlank()) return@AppButton
                                            coroutineScope.launch {
                                                clipboard.setClipEntry(
                                                    ClipEntry(
                                                        ClipData.newPlainText(
                                                            "installation_id",
                                                            preferences.installationId,
                                                        ),
                                                    ),
                                                )
                                            }
                                            showMessage(deviceIdCopiedText)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                            androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = colorScheme.secondary,
                                                contentColor = colorScheme.onSecondary,
                                            ),
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space8))
                                    AppButton(
                                        text = stringResource(R.string.settings_push_retry_now),
                                        onClick = {
                                            onRetryPushRegistration()
                                            showMessage(pushRetryStartedText)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                            androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = colorScheme.primary,
                                                contentColor = colorScheme.onPrimary,
                                            ),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(dimens.space8))

                            AppCard(
                                modifier = Modifier.padding(horizontal = dimens.space8),
                                shape = MaterialTheme.shapes.large,
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        contentColor = colorScheme.onSurfaceVariant,
                                    ),
                            ) {
                                Column(modifier = Modifier.padding(dimens.space16)) {
                                    Text(
                                        text = stringResource(R.string.settings_ads_debug_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space4))
                                    Text(
                                        text = stringResource(R.string.settings_ads_debug_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space8))
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.settings_ads_debug_geo_status,
                                                debugGeography.name,
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space4))
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.settings_ads_debug_geo_last_request_status,
                                                lastRequestDebugGeography.name,
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space4))
                                    val requestStatusText =
                                        when {
                                            debugGeography == UmpDebugGeography.NONE ->
                                                stringResource(
                                                    R.string.settings_ads_debug_geo_effective_none,
                                                )

                                            lastRequestDebugGeography == debugGeography ->
                                                stringResource(
                                                    R.string.settings_ads_debug_geo_effective_applied,
                                                    debugGeography.name,
                                                )

                                            else ->
                                                stringResource(
                                                    R.string.settings_ads_debug_geo_effective_pending,
                                                    debugGeography.name,
                                                )
                                        }
                                    Text(
                                        text = requestStatusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space4))
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.settings_ads_debug_can_request_ads,
                                                if (canRequestAds) {
                                                    stringResource(R.string.settings_ads_debug_yes)
                                                } else {
                                                    stringResource(R.string.settings_ads_debug_no)
                                                },
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space4))
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.settings_ads_debug_privacy_options_status,
                                                if (isPrivacyOptionsRequired) {
                                                    stringResource(R.string.settings_ads_debug_yes)
                                                } else {
                                                    stringResource(R.string.settings_ads_debug_no)
                                                },
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space4))
                                    val lastConsentResultText =
                                        when (val result = lastConsentDebugResult) {
                                            UmpConsentDebugResult.Idle ->
                                                stringResource(R.string.settings_ads_debug_last_result_idle)

                                            UmpConsentDebugResult.Shown ->
                                                stringResource(R.string.settings_ads_debug_last_result_shown)

                                            UmpConsentDebugResult.NotRequired ->
                                                stringResource(R.string.settings_ads_debug_last_result_not_required)

                                            is UmpConsentDebugResult.Error ->
                                                stringResource(
                                                    R.string.settings_ads_debug_last_result_error,
                                                    result.message,
                                                )
                                        }
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.settings_ads_debug_last_result,
                                                lastConsentResultText,
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space12))
                                    AppButton(
                                        text = stringResource(R.string.settings_ads_debug_open_inspector),
                                        onClick = {
                                            if (activity == null) {
                                                showMessage(privacyOptionsUnavailableText)
                                                return@AppButton
                                            }
                                            adManager.openAdInspector(activity) { error ->
                                                showMessage(
                                                    if (error == null) {
                                                        adInspectorOpenedText
                                                    } else {
                                                        "$adInspectorErrorText: $error"
                                                    },
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                            androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = colorScheme.primary,
                                                contentColor = colorScheme.onPrimary,
                                            ),
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space8))
                                    AppButton(
                                        text = stringResource(R.string.settings_ads_debug_reset_consent),
                                        onClick = {
                                            onResetConsent()
                                            showMessage(consentResetText)
                                            if (activity != null) {
                                                adManager.onAdsConfigChanged(activity) {
                                                    onPrivacyOptionsUpdated()
                                                    isPrivacyOptionsRequired =
                                                        adManager.privacyOptionsRequired.value
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                            androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = colorScheme.secondary,
                                                contentColor = colorScheme.onSecondary,
                                            ),
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space8))
                                    AppButton(
                                        text = stringResource(R.string.settings_ads_debug_show_consent_form),
                                        onClick = {
                                            val hostActivity = activity ?: return@AppButton
                                            adManager.showConsentFormIfRequired(hostActivity) { result ->
                                                onPrivacyOptionsUpdated()
                                                showMessage(
                                                    when (result) {
                                                        UmpConsentDebugResult.Shown ->
                                                            consentFormActuallyShownText

                                                        UmpConsentDebugResult.NotRequired ->
                                                            consentFormNotRequiredText

                                                        is UmpConsentDebugResult.Error ->
                                                            "$consentFormErrorText: ${result.message}"

                                                        UmpConsentDebugResult.Idle ->
                                                            consentFormShownText
                                                    },
                                                )
                                                isPrivacyOptionsRequired =
                                                    adManager.privacyOptionsRequired.value
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                            androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = colorScheme.primary,
                                                contentColor = colorScheme.onPrimary,
                                            ),
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space8))
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        AppButton(
                                            text = stringResource(R.string.settings_ads_debug_force_eea),
                                            onClick = {
                                                onSetConsentDebugGeography(UmpDebugGeography.EEA)
                                                onResetConsent()
                                                if (activity != null) {
                                                    adManager.onAdsConfigChanged(activity) {
                                                        onPrivacyOptionsUpdated()
                                                        isPrivacyOptionsRequired =
                                                            adManager.privacyOptionsRequired.value
                                                    }
                                                }
                                                showMessage(
                                                    String.format(
                                                        debugGeoSetResetHintText,
                                                        UmpDebugGeography.EEA.name,
                                                    ),
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors =
                                                androidx.compose.material3.ButtonDefaults.buttonColors(
                                                    containerColor = colorScheme.tertiary,
                                                    contentColor = colorScheme.onTertiary,
                                                ),
                                        )
                                        Spacer(modifier = Modifier.width(dimens.space8))
                                        AppButton(
                                            text = stringResource(R.string.settings_ads_debug_force_us),
                                            onClick = {
                                                onSetConsentDebugGeography(UmpDebugGeography.US_STATES)
                                                onResetConsent()
                                                if (activity != null) {
                                                    adManager.onAdsConfigChanged(activity) {
                                                        onPrivacyOptionsUpdated()
                                                        isPrivacyOptionsRequired =
                                                            adManager.privacyOptionsRequired.value
                                                    }
                                                }
                                                showMessage(
                                                    String.format(
                                                        debugGeoSetResetHintText,
                                                        UmpDebugGeography.US_STATES.name,
                                                    ),
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors =
                                                androidx.compose.material3.ButtonDefaults.buttonColors(
                                                    containerColor = colorScheme.tertiary,
                                                    contentColor = colorScheme.onTertiary,
                                                ),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(dimens.space8))
                                    AppButton(
                                        text = stringResource(R.string.settings_ads_debug_clear_geo),
                                        onClick = {
                                            onSetConsentDebugGeography(UmpDebugGeography.NONE)
                                            showMessage("UMP debug geography cleared")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                            androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = colorScheme.surface,
                                                contentColor = colorScheme.onSurface,
                                            ),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(dimens.space8))

                            AppCard(
                                modifier = Modifier.padding(horizontal = dimens.space8),
                                shape = MaterialTheme.shapes.large,
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        contentColor = colorScheme.onSurfaceVariant,
                                    ),
                            ) {
                                Column(modifier = Modifier.padding(dimens.space16)) {
                                    Text(
                                        text = stringResource(R.string.settings_update_debug_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space4))
                                    Text(
                                        text = stringResource(R.string.settings_update_debug_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space10))
                                    Text(
                                        text =
                                            updateDebugSummary?.takeIf { it.isNotBlank() }
                                                ?: stringResource(R.string.settings_update_debug_summary_empty),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space12))
                                    AppButton(
                                        text = updateDebugFetchText,
                                        onClick = {
                                            onUpdateDebugFetchNow()
                                            showMessage(updateDebugFetchStartedText)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                            androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = colorScheme.primary,
                                                contentColor = colorScheme.onPrimary,
                                            ),
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space8))
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        AppButton(
                                            text = updateDebugSimSoftText,
                                            onClick = {
                                                onUpdateDebugSimulateSoft()
                                                showMessage(updateDebugSimSoftText)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors =
                                                androidx.compose.material3.ButtonDefaults.buttonColors(
                                                    containerColor = colorScheme.tertiary,
                                                    contentColor = colorScheme.onTertiary,
                                                ),
                                        )
                                        Spacer(modifier = Modifier.width(dimens.space8))
                                        AppButton(
                                            text = updateDebugSimHardText,
                                            onClick = {
                                                onUpdateDebugSimulateHard()
                                                showMessage(updateDebugSimHardText)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors =
                                                androidx.compose.material3.ButtonDefaults.buttonColors(
                                                    containerColor = colorScheme.error,
                                                    contentColor = colorScheme.onError,
                                                ),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(dimens.space8))
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        AppButton(
                                            text = updateDebugClearSimText,
                                            onClick = {
                                                onUpdateDebugClearSimulation()
                                                showMessage(updateDebugClearSimText)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors =
                                                androidx.compose.material3.ButtonDefaults.buttonColors(
                                                    containerColor = colorScheme.secondary,
                                                    contentColor = colorScheme.onSecondary,
                                                ),
                                        )
                                        Spacer(modifier = Modifier.width(dimens.space8))
                                        AppButton(
                                            text = updateDebugResetSoftText,
                                            onClick = {
                                                onUpdateDebugResetSoftPrompt()
                                                showMessage(updateDebugResetSoftText)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors =
                                                androidx.compose.material3.ButtonDefaults.buttonColors(
                                                    containerColor = colorScheme.surface,
                                                    contentColor = colorScheme.onSurface,
                                                ),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(dimens.space8))

                            AppCard(
                                modifier = Modifier.padding(horizontal = dimens.space8),
                                shape = MaterialTheme.shapes.large,
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        contentColor = colorScheme.onSurfaceVariant,
                                    ),
                            ) {
                                Column(modifier = Modifier.padding(dimens.space16)) {
                                    Text(
                                        text = stringResource(R.string.settings_fcm_debug_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space4))
                                    Text(
                                        text = stringResource(R.string.settings_fcm_debug_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space10))
                                    Text(
                                        text =
                                            debugFcmToken.ifBlank {
                                                stringResource(
                                                    R.string.settings_fcm_debug_empty,
                                                )
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurface,
                                    )
                                    if (!debugFcmTokenError.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(dimens.space6))
                                        Text(
                                            text = debugFcmTokenError.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.error,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(dimens.space12))
                                    AppButton(
                                        text =
                                            if (isFetchingDebugToken) {
                                                stringResource(R.string.settings_fcm_debug_fetching)
                                            } else {
                                                stringResource(R.string.settings_fcm_debug_refresh)
                                            },
                                        onClick = {
                                            if (isFetchingDebugToken) return@AppButton
                                            isFetchingDebugToken = true
                                            debugFcmTokenError = null
                                            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                                                isFetchingDebugToken = false
                                                if (task.isSuccessful) {
                                                    debugFcmToken = task.result.orEmpty()
                                                    if (debugFcmToken.isNotBlank()) {
                                                        showMessage(fcmDebugRefreshedText)
                                                    }
                                                } else {
                                                    debugFcmTokenError =
                                                        task.exception?.localizedMessage
                                                            ?: fcmDebugErrorText
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                            androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = colorScheme.primary,
                                                contentColor = colorScheme.onPrimary,
                                            ),
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space8))
                                    AppButton(
                                        text = stringResource(R.string.settings_fcm_debug_copy),
                                        onClick = {
                                            if (debugFcmToken.isBlank()) return@AppButton
                                            coroutineScope.launch {
                                                clipboard.setClipEntry(
                                                    ClipEntry(
                                                        ClipData.newPlainText(
                                                            "fcm_token",
                                                            debugFcmToken,
                                                        ),
                                                    ),
                                                )
                                            }
                                            showMessage(fcmDebugCopiedText)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                            androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = colorScheme.secondary,
                                                contentColor = colorScheme.onSecondary,
                                            ),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(dimens.space8))

                            // Crashlytics Test Card
                            AppCard(
                                modifier = Modifier.padding(horizontal = dimens.space8),
                                shape = MaterialTheme.shapes.large,
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = colorScheme.errorContainer.copy(alpha = 0.4f),
                                        contentColor = colorScheme.onErrorContainer,
                                    ),
                            ) {
                                Column(modifier = Modifier.padding(dimens.space16)) {
                                    Text(
                                        text = stringResource(R.string.settings_crash_debug_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space4))
                                    Text(
                                        text = stringResource(R.string.settings_crash_debug_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space12))
                                    AppButton(
                                        text = stringResource(R.string.settings_crash_debug_fatal),
                                        onClick = {
                                            throw IllegalStateException("Crashlytics test crash — debug button")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                            androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = colorScheme.error,
                                                contentColor = colorScheme.onError,
                                            ),
                                    )
                                    Spacer(modifier = Modifier.height(dimens.space8))
                                    AppButton(
                                        text = stringResource(R.string.settings_crash_debug_non_fatal),
                                        onClick = {
                                            com.google.firebase.crashlytics.FirebaseCrashlytics
                                                .getInstance()
                                                .recordException(
                                                    IllegalStateException("Crashlytics non-fatal test — debug button"),
                                                )
                                            showMessage(nonFatalSentMessage)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                            androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = colorScheme.tertiary,
                                                contentColor = colorScheme.onTertiary,
                                            ),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(dimens.bottomBarHeight + dimens.space16))
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(dimens.space16),
        )
    }
}

private fun Context.findActivity(): android.app.Activity? =
    when (this) {
        is android.app.Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private data class LanguageOption(
    val tag: String,
    val labelRes: Int,
) {
    fun matches(appLocaleTags: String): Boolean {
        val normalizedCurrent = appLocaleTags.trim().lowercase(Locale.ROOT)
        val normalized = tag.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) {
            // "System" is selected when app locales are empty (follow system language).
            return normalizedCurrent.isBlank()
        }
        return normalizedCurrent.startsWith(normalized)
    }
}

private fun isNotificationPermissionEnabled(context: Context): Boolean {
    val managerEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val runtimeGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    return managerEnabled && runtimeGranted
}

private fun openAppNotificationSettings(context: Context) {
    val intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri(),
            )
        }
    context.startActivity(intent)
}

private fun shareApp(context: Context) {
    val playUrl = "https://play.google.com/store/apps/details?id=${context.packageName}"
    val shareText = context.getString(R.string.settings_share_app_message, playUrl)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            },
            null,
        ),
    )
}

private fun Long.formatDebugTime(): String =
    if (this <= 0L) {
        "-"
    } else {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(this))
    }
