package com.parsfilo.contentapp.feature.counter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsfilo.contentapp.feature.counter.model.CounterHapticEvent
import kotlinx.coroutines.launch

@Composable
fun CounterRoute(
    onSettingsClick: () -> Unit,
    onRewardsClick: () -> Unit,
    onShowInterstitial: suspend () -> Unit = {},
    onShowRewardedHistoryAd: suspend (onUnlocked: () -> Unit) -> Unit = { onUnlocked -> onUnlocked() },
    bannerAdContent: (@Composable () -> Unit)? = null,
    nativeAdContent: (@Composable () -> Unit)? = null,
    viewModel: CounterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val hapticEnabled by rememberUpdatedState(uiState.isHapticEnabled)

    LaunchedEffect(viewModel) {
        viewModel.hapticEvents.collect { event ->
            if (!hapticEnabled) return@collect
            when (event) {
                CounterHapticEvent.TAP -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                CounterHapticEvent.TEN -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                CounterHapticEvent.TARGET -> haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.interstitialEvents.collect {
            onShowInterstitial()
        }
    }

    CounterScreen(
        uiState = uiState,
        onSettingsClick = onSettingsClick,
        onRewardsClick = onRewardsClick,
        onCounterTapped = viewModel::onCounterTapped,
        onResetCurrentCount = viewModel::onResetCurrentCount,
        onShareSession = viewModel::onShareSession,
        onZikirSelectorToggle = viewModel::onZikirSelectorToggle,
        onZikirSelected = viewModel::onZikirSelected,
        onAddCustomZikir = viewModel::onAddCustomZikir,
        onDeleteZikir = viewModel::onDeleteZikir,
        onDismissSessionComplete = viewModel::onDismissSessionComplete,
        onShareConfirmed = viewModel::onShareConfirmed,
        onShareDismissed = viewModel::onShareDismissed,
        onShareTextCopied = viewModel::onShareTextCopied,
        onReminderSettingsToggle = viewModel::onReminderSettingsToggle,
        onReminderSaved = viewModel::onReminderSaved,
        reminderUiEvents = viewModel.reminderUiEvents,
        onExactAlarmPermissionSettingsReturned = viewModel::onExactAlarmPermissionSettingsReturned,
        onSessionHistoryToggle = viewModel::onSessionHistoryToggle,
        onSessionHistoryDismiss = viewModel::onSessionHistoryDismissed,
        onUnlockHistoryWithAd = {
            scope.launch {
                onShowRewardedHistoryAd { viewModel.onRewardedHistoryUnlocked() }
            }
        },
        onToggleHaptic = viewModel::toggleHaptic,
        onToggleSound = viewModel::toggleSound,
        onTargetChanged = viewModel::onTargetChanged,
        onFirstSessionReminderAction = viewModel::onFirstSessionReminderAction,
        onFirstSessionReminderConsumed = viewModel::onFirstSessionReminderConsumed,
        bannerAdContent = bannerAdContent,
        nativeAdContent = nativeAdContent,
    )
}
