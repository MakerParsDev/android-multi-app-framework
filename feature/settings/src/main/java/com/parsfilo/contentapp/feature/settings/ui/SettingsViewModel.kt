package com.parsfilo.contentapp.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.datastore.UserPreferencesData
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import com.parsfilo.contentapp.core.firebase.logAppShared
import com.parsfilo.contentapp.core.firebase.push.PushRegistrationManager
import com.parsfilo.contentapp.feature.ads.AdAgeGateStatus
import com.parsfilo.contentapp.feature.ads.AdManager
import com.parsfilo.contentapp.feature.ads.UmpDebugGeography
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val preferencesDataSource: PreferencesDataSource,
        private val appAnalytics: AppAnalytics,
        private val adManager: AdManager,
        private val pushRegistrationManager: PushRegistrationManager,
    ) : ViewModel() {
        init {
            viewModelScope.launch {
                preferencesDataSource.getOrCreateInstallationId()
            }
        }

        val uiState: StateFlow<SettingsUiState> =
            preferencesDataSource.userData
                .map { SettingsUiState.Success(it) as SettingsUiState }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = SettingsUiState.Loading,
                )

        fun setDarkMode(darkMode: Boolean) {
            viewModelScope.launch {
                preferencesDataSource.setDarkMode(darkMode)
            }
        }

        fun setFontSize(size: Int) {
            viewModelScope.launch {
                preferencesDataSource.setFontSize(size)
            }
        }

        fun setDeveloperModeEnabled(enabled: Boolean) {
            viewModelScope.launch {
                preferencesDataSource.setDeveloperModeEnabled(enabled)
            }
        }

        fun setNotificationsEnabled(enabled: Boolean) {
            viewModelScope.launch {
                preferencesDataSource.setNotificationsEnabled(enabled)
            }
        }

        fun retryPushRegistrationNow() {
            viewModelScope.launch {
                pushRegistrationManager.syncRegistration(
                    reason = "developer_manual_retry",
                    scheduleRetryOnFailure = true,
                )
            }
        }

        fun logShareApp(platform: String) {
            appAnalytics.logAppShared(platform = platform)
        }

        fun setAdsAgeGateStatus(status: AdAgeGateStatus) {
            viewModelScope.launch {
                preferencesDataSource.setAdsAgeGateStatus(status.storageValue)
                preferencesDataSource.setAdsAgeGatePromptCompleted(true)
            }
        }

        fun setConsentDebugGeography(geography: UmpDebugGeography) {
            adManager.setConsentDebugGeography(geography)
        }

        fun resetConsent() {
            adManager.resetConsent()
        }

        fun adManager(): AdManager = adManager
    }

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Success(
        val preferences: UserPreferencesData,
    ) : SettingsUiState
}
