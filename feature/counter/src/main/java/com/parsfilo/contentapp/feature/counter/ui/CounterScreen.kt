package com.parsfilo.contentapp.feature.counter.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.core.designsystem.component.AppCard
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.feature.counter.R
import com.parsfilo.contentapp.feature.counter.model.CounterUiState
import com.parsfilo.contentapp.feature.counter.model.ReminderSettings
import com.parsfilo.contentapp.feature.counter.model.ZikirItem
import com.parsfilo.contentapp.feature.counter.ui.components.CounterFab
import com.parsfilo.contentapp.feature.counter.ui.components.CounterHeaderBar
import com.parsfilo.contentapp.feature.counter.ui.components.DailyProgressSection
import com.parsfilo.contentapp.feature.counter.ui.components.ReminderSettingsSheet
import com.parsfilo.contentapp.feature.counter.ui.components.SessionCompleteDialog
import com.parsfilo.contentapp.feature.counter.ui.components.SessionHistorySheet
import com.parsfilo.contentapp.feature.counter.ui.components.SharePreviewCard
import com.parsfilo.contentapp.feature.counter.ui.components.StreakBadge
import com.parsfilo.contentapp.feature.counter.ui.components.ZikirSelectorPage
import com.parsfilo.contentapp.feature.counter.ui.components.ZikirTextCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
fun CounterScreen(
    uiState: CounterUiState,
    onSettingsClick: () -> Unit,
    onRewardsClick: () -> Unit,
    onCounterTapped: () -> Unit,
    onResetCurrentCount: () -> Unit,
    onShareSession: () -> Unit,
    onZikirSelectorToggle: () -> Unit,
    onZikirSelected: (ZikirItem) -> Unit,
    onAddCustomZikir: (arabicText: String, latinText: String, turkishMeaning: String, defaultTarget: Int) -> Unit,
    onDeleteZikir: (ZikirItem) -> Unit,
    onDismissSessionComplete: () -> Unit,
    onShareConfirmed: () -> Unit,
    onShareDismissed: () -> Unit,
    onShareTextCopied: () -> Unit,
    onReminderSettingsToggle: () -> Unit,
    onReminderSaved: (ReminderSettings) -> Unit,
    reminderUiEvents: Flow<CounterReminderUiEvent> = emptyFlow(),
    onExactAlarmPermissionSettingsReturned: () -> Unit = {},
    onSessionHistoryToggle: () -> Unit,
    onSessionHistoryDismiss: () -> Unit,
    onUnlockHistoryWithAd: () -> Unit,
    onToggleHaptic: () -> Unit,
    onToggleSound: () -> Unit,
    onTargetChanged: (Int) -> Unit,
    onFirstSessionReminderAction: () -> Unit,
    onFirstSessionReminderConsumed: () -> Unit,
    bannerAdContent: (@Composable () -> Unit)? = null,
    nativeAdContent: (@Composable () -> Unit)? = null,
) {
    val d = LocalDimens.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboard.current
    val reminderSavedMessage = stringResource(R.string.counter_reminder_saved)
    val copiedMessage = stringResource(R.string.counter_copied)
    val firstSessionReminderPrompt = stringResource(R.string.counter_first_session_reminder_prompt)
    val firstSessionReminderAction = stringResource(R.string.counter_first_session_reminder_action)
    val exactAlarmPermissionMessage = stringResource(R.string.counter_exact_alarm_permission_required)
    val exactAlarmPermissionAction = stringResource(R.string.counter_exact_alarm_permission_action)
    val exactAlarmSettingsOpenFailed = stringResource(R.string.counter_exact_alarm_settings_open_failed)
    val exactAlarmPermissionGranted = stringResource(R.string.counter_exact_alarm_permission_granted)
    val exactAlarmPermissionStillMissing = stringResource(R.string.counter_exact_alarm_permission_still_missing)

    val exactAlarmPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        onExactAlarmPermissionSettingsReturned()
    }

    LaunchedEffect(uiState.showFirstSessionReminderHint) {
        if (!uiState.showFirstSessionReminderHint) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = firstSessionReminderPrompt,
            actionLabel = firstSessionReminderAction,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onFirstSessionReminderAction()
        } else {
            onFirstSessionReminderConsumed()
        }
    }

    LaunchedEffect(reminderUiEvents) {
        reminderUiEvents.collect { event ->
            when (event) {
                CounterReminderUiEvent.RequestExactAlarmPermission -> {
                    val result = snackbarHostState.showSnackbar(
                        message = exactAlarmPermissionMessage,
                        actionLabel = exactAlarmPermissionAction,
                    )
                    if (result == SnackbarResult.ActionPerformed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val exactAlarmIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                        val fallbackIntent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        try {
                            exactAlarmPermissionLauncher.launch(exactAlarmIntent)
                        } catch (_: ActivityNotFoundException) {
                            try {
                                exactAlarmPermissionLauncher.launch(fallbackIntent)
                            } catch (_: Throwable) {
                                snackbarHostState.showSnackbar(exactAlarmSettingsOpenFailed)
                            }
                        } catch (_: Throwable) {
                            snackbarHostState.showSnackbar(exactAlarmSettingsOpenFailed)
                        }
                    }
                }

                CounterReminderUiEvent.ExactAlarmPermissionGranted ->
                    snackbarHostState.showSnackbar(exactAlarmPermissionGranted)
                CounterReminderUiEvent.ExactAlarmPermissionStillMissing ->
                    snackbarHostState.showSnackbar(exactAlarmPermissionStillMissing)
            }
        }
    }

    if (uiState.showZikirSelector) {
        ZikirSelectorPage(
            zikirList = uiState.zikirList,
            selectedKey = uiState.selectedZikir?.key,
            onSelect = onZikirSelected,
            onDismiss = onZikirSelectorToggle,
            onAddCustomZikir = onAddCustomZikir,
            onDeleteZikir = onDeleteZikir,
            onSettingsClick = onSettingsClick,
            onRewardsClick = onRewardsClick,
            onReminderClick = onReminderSettingsToggle,
            onHistoryClick = onSessionHistoryToggle,
            isHapticEnabled = uiState.isHapticEnabled,
            isSoundEnabled = uiState.isSoundEnabled,
            onToggleHaptic = onToggleHaptic,
            onToggleSound = onToggleSound,
            isPremium = uiState.isPremium,
            content = bannerAdContent,
        )
        return
    }

    BackHandler {
        onZikirSelectorToggle()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            CounterHeaderBar(
                onReminderClick = onReminderSettingsToggle,
                onHistoryClick = onSessionHistoryToggle,
                onSettingsClick = onSettingsClick,
                onRewardsClick = onRewardsClick,
                isHapticEnabled = uiState.isHapticEnabled,
                isSoundEnabled = uiState.isSoundEnabled,
                onToggleHaptic = onToggleHaptic,
                onToggleSound = onToggleSound,
                onOpenZikirList = onZikirSelectorToggle,
            )

            if (!uiState.isPremium) {
                bannerAdContent?.invoke()
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = d.space16, vertical = d.space10),
                    verticalArrangement = Arrangement.spacedBy(d.space12),
                ) {
                    StreakBadge(
                        streak = uiState.currentStreak,
                        todayTotal = uiState.todayTotalCount,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    DailyProgressSection(
                        todayTotal = uiState.todayTotalCount,
                        dailyGoal = uiState.dailyGoal,
                        progress = uiState.dailyGoalProgress,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    val selectedZikir = uiState.selectedZikir
                    if (selectedZikir != null) {
                        ZikirTextCard(
                            zikir = selectedZikir,
                            onChangeClick = onZikirSelectorToggle,
                        )
                    }

                    TargetSelectorRow(
                        targetCount = uiState.targetCount,
                        onTargetChanged = onTargetChanged,
                    )

                    CurrentCountSection(
                        currentCount = uiState.currentCount,
                        targetCount = uiState.targetCount,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    CounterFab(
                        arabicText = selectedZikir?.arabicText.orEmpty(),
                        latinText = selectedZikir?.latinText.orEmpty(),
                        currentCount = uiState.currentCount,
                        onTap = onCounterTapped,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        contentDescription = pluralStringResource(
                            R.plurals.counter_content_description_fab,
                            uiState.currentCount,
                            uiState.currentCount,
                        ),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(d.space10),
                    ) {
                        AppButton(
                            text = stringResource(R.string.counter_reset),
                            onClick = onResetCurrentCount,
                            modifier = Modifier.weight(1f),
                        )
                        AppButton(
                            text = stringResource(R.string.counter_share),
                            onClick = onShareSession,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(d.space8))
                }
            }
        }
    }

    if (uiState.showReminderSettings) {
        ReminderSettingsSheet(
            initialSettings = uiState.reminderSettings,
            onSave = { settings ->
                onReminderSaved(settings)
                scope.launch {
                    snackbarHostState.showSnackbar(reminderSavedMessage)
                }
            },
            onDismiss = onReminderSettingsToggle,
        )
    }

    if (uiState.showSessionHistory) {
        SessionHistorySheet(
            sessions = uiState.recentSessions,
            isPremium = uiState.isPremium,
            historyUnlockedForSession = uiState.historyUnlockedForSession,
            onUnlockWithAd = onUnlockHistoryWithAd,
            onDismiss = onSessionHistoryDismiss,
            content = if (uiState.isPremium) null else nativeAdContent,
        )
    }

    val completedSession = uiState.lastCompletedSession
    if (uiState.showSessionComplete && completedSession != null) {
        SessionCompleteDialog(
            session = completedSession,
            currentStreak = uiState.currentStreak,
            todayTotalCount = uiState.todayTotalCount,
            onShareClick = onShareSession,
            onDismiss = onDismissSessionComplete,
        )
    }

    if (uiState.showSharePreview && uiState.shareText.isNotBlank()) {
        SharePreviewCard(
            text = uiState.shareText,
            onShare = {
                onShareConfirmed()
                onShareDismissed()
            },
            onCopy = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("zikir_share_text", uiState.shareText)))
                }
                onShareTextCopied()
                scope.launch {
                    snackbarHostState.showSnackbar(copiedMessage)
                }
            },
            onDismiss = onShareDismissed,
        )
    }
}

@Composable
private fun CurrentCountSection(
    currentCount: Int,
    targetCount: Int,
    modifier: Modifier = Modifier,
) {
    val d = LocalDimens.current
    val progress = if (targetCount > 0) {
        (currentCount.toFloat() / targetCount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(d.space16),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(d.space8),
        ) {
            Text(
                text = currentCount.toString(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
            )
            Text(
                text = stringResource(R.string.counter_target_format, targetCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun TargetSelectorRow(
    targetCount: Int,
    onTargetChanged: (Int) -> Unit,
) {
    val d = LocalDimens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(d.space8),
    ) {
        TargetButton(
            text = stringResource(R.string.counter_target_33),
            selected = targetCount == 33,
            onClick = { onTargetChanged(33) },
            modifier = Modifier.weight(1f),
        )
        TargetButton(
            text = stringResource(R.string.counter_target_99),
            selected = targetCount == 99,
            onClick = { onTargetChanged(99) },
            modifier = Modifier.weight(1f),
        )
        TargetButton(
            text = stringResource(R.string.counter_target_100),
            selected = targetCount == 100,
            onClick = { onTargetChanged(100) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TargetButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
