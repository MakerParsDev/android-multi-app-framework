package com.parsfilo.contentapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.auth.AuthManager
import com.parsfilo.contentapp.core.database.dao.NotificationDao
import com.parsfilo.contentapp.core.datastore.PrayerPreferencesDataSource
import com.parsfilo.contentapp.core.datastore.PreferencesDataSource
import com.parsfilo.contentapp.core.firebase.push.PushRegistrationManager
import com.parsfilo.contentapp.feature.messages.data.MessageRepository
import com.parsfilo.contentapp.feature.otherapps.data.OtherAppsRepository
import com.parsfilo.contentapp.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val notificationDao: NotificationDao,
        messageRepository: MessageRepository,
        otherAppsRepository: OtherAppsRepository,
        private val authManager: AuthManager,
        private val preferencesDataSource: PreferencesDataSource,
        private val prayerPreferencesDataSource: PrayerPreferencesDataSource,
        private val pushRegistrationManager: PushRegistrationManager,
    ) : ViewModel() {
        val unreadNotificationCount: StateFlow<Int> =
            notificationDao
                .getUnreadCount()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = 0,
                )

        val isUserSignedIn: StateFlow<Boolean> =
            authManager.authState
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = authManager.isUserSignedIn(),
                )

        val darkModeEnabled: StateFlow<Boolean> =
            preferencesDataSource.userData
                .map { it.darkMode }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = false,
                )

        val shouldRequestNotificationPermission: StateFlow<Boolean> =
            preferencesDataSource.userData
                .map { !it.notificationPermissionPrompted }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = false,
                )

        val shouldRequestLocationPermission: StateFlow<Boolean> =
            prayerPreferencesDataSource.preferences
                .map { !it.locationPermissionPrompted }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = false,
                )

        val unreadMessageCount: StateFlow<Int> =
            messageRepository
                .getMessages()
                .map { messages -> messages.count { it.hasReply && !it.isRead } }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = 0,
                )

        private val latestOtherAppsBadgeSignature = MutableStateFlow("")

        val newOtherAppsCount: StateFlow<Int> =
            combine(
                otherAppsRepository.apps,
                preferencesDataSource.otherAppsBadgeSeenSignature,
            ) { apps, seenSignature ->
                val newApps = apps.filter { it.isNew }
                val signature = buildOtherAppsSignature(newApps.map { it.packageName })
                latestOtherAppsBadgeSignature.value = signature
                if (signature.isBlank() || signature == seenSignature) 0 else newApps.size
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

        val shouldShowSubscriptionBadge: StateFlow<Boolean> =
            preferencesDataSource.userData
                .map { prefs -> !prefs.isPremium }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = true,
                )

        init {
            viewModelScope.launch {
                Timber.d("MainViewModel init: refreshing other apps feed")
                otherAppsRepository.refreshIfNeeded()
            }
        }

        fun onNotificationPermissionResult(granted: Boolean) {
            Timber.d("MainViewModel notification permission result granted=%s", granted)
            viewModelScope.launch {
                preferencesDataSource.setNotificationPermissionPrompted(true)
                if (!granted) {
                    preferencesDataSource.setNotificationsEnabled(false)
                }
                pushRegistrationManager.syncRegistration("permission_result")
            }
        }

        fun onLocationPermissionResult() {
            Timber.d("MainViewModel location permission prompt marked as seen")
            viewModelScope.launch {
                prayerPreferencesDataSource.setLocationPermissionPrompted(true)
            }
        }

        fun onTopLevelRouteVisited(route: AppRoute) {
            Timber.d("MainViewModel top-level route visited=%s", route.route)
            when (route) {
                AppRoute.OtherApps -> markOtherAppsBadgeAsSeen()
                else -> Unit
            }
        }

        private fun markOtherAppsBadgeAsSeen() {
            viewModelScope.launch {
                Timber.d(
                    "MainViewModel marking other-apps badge as seen signature=%s",
                    latestOtherAppsBadgeSignature.value,
                )
                preferencesDataSource.setOtherAppsBadgeSeenSignature(
                    latestOtherAppsBadgeSignature.value,
                )
            }
        }

        private fun buildOtherAppsSignature(packageNames: List<String>): String =
            packageNames
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .sorted()
                .joinToString(separator = ",")
    }
