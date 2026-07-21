package com.parsfilo.contentapp.feature.notifications.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.common.result.Result
import com.parsfilo.contentapp.core.common.result.asResult
import com.parsfilo.contentapp.core.database.dao.NotificationDao
import com.parsfilo.contentapp.core.database.model.asExternalModel
import com.parsfilo.contentapp.core.firebase.AppAnalytics
import com.parsfilo.contentapp.core.firebase.logNotificationDelete
import com.parsfilo.contentapp.core.firebase.logNotificationMarkRead
import com.parsfilo.contentapp.core.firebase.logNotificationMarkUnread
import com.parsfilo.contentapp.core.firebase.logNotificationOpen
import com.parsfilo.contentapp.core.firebase.logNotificationsDeleteAll
import com.parsfilo.contentapp.core.firebase.logNotificationsMarkAllRead
import com.parsfilo.contentapp.core.model.Notification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationDao: NotificationDao,
    private val appAnalytics: AppAnalytics,
) : ViewModel() {

    val uiState: StateFlow<NotificationsUiState> = notificationDao.getNotifications()
        .map { entities -> entities.map { it.asExternalModel() } }
        .asResult()
        .map { result ->
            when (result) {
                is Result.Loading -> NotificationsUiState.Loading
                is Result.Error -> NotificationsUiState.Error(result.exception)
                is Result.Success -> NotificationsUiState.Success(result.data)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotificationsUiState.Loading
        )

    fun markAsRead(id: Long) {
        viewModelScope.launch {
            notificationDao.markAsRead(id)
            appAnalytics.logNotificationMarkRead()
        }
    }

    fun markAsUnread(id: Long) {
        viewModelScope.launch {
            notificationDao.markAsUnread(id)
            appAnalytics.logNotificationMarkUnread()
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationDao.markAllAsRead()
            appAnalytics.logNotificationsMarkAllRead()
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch {
            notificationDao.deleteNotification(id)
            appAnalytics.logNotificationDelete()
        }
    }

    fun deleteAllNotifications() {
        viewModelScope.launch {
            notificationDao.deleteAllNotifications()
            appAnalytics.logNotificationsDeleteAll()
        }
    }

    fun logNotificationOpen() {
        appAnalytics.logNotificationOpen()
    }
}

sealed interface NotificationsUiState {
    data object Loading : NotificationsUiState

    data class Success(val notifications: List<Notification>) : NotificationsUiState

    data class Error(val throwable: Throwable) : NotificationsUiState
}
