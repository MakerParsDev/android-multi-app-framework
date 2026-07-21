package com.parsfilo.contentapp.feature.notifications.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.database.dao.NotificationDao
import com.parsfilo.contentapp.core.database.model.asExternalModel
import com.parsfilo.contentapp.core.model.Notification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NotificationDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    notificationDao: NotificationDao,
) : ViewModel() {

    private val notificationRowId: Long = savedStateHandle.get<Long>("notificationId") ?: 0L

    val uiState: StateFlow<NotificationDetailUiState> =
        if (notificationRowId <= 0L) {
            kotlinx.coroutines.flow.flowOf(NotificationDetailUiState.NotFound)
        } else {
            notificationDao.getById(notificationRowId)
                .map { entity ->
                    if (entity == null) {
                        NotificationDetailUiState.NotFound
                    } else {
                        NotificationDetailUiState.Success(entity.asExternalModel())
                    }
                }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotificationDetailUiState.Loading,
        )
}

sealed interface NotificationDetailUiState {
    data object Loading : NotificationDetailUiState

    data object NotFound : NotificationDetailUiState

    data class Success(val notification: Notification) : NotificationDetailUiState
}
