package com.parsfilo.contentapp.feature.messages.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.auth.AuthManager
import com.parsfilo.contentapp.core.common.result.Result
import com.parsfilo.contentapp.core.common.result.asResult
import com.parsfilo.contentapp.core.model.Message
import com.parsfilo.contentapp.feature.messages.data.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val authManager: AuthManager
) : ViewModel() {

    val uiState: StateFlow<MessagesUiState> = messageRepository.getMessages()
        .asResult()
        .map { result ->
            when (result) {
                is Result.Loading -> MessagesUiState.Loading
                is Result.Error -> MessagesUiState.Error(result.exception)
                is Result.Success -> MessagesUiState.Success(result.data)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MessagesUiState.Loading
        )

    fun sendMessage(subject: String, message: String, category: String) {
        viewModelScope.launch {
            runCatching {
                messageRepository.sendMessage(subject, message, category)
            }.onFailure { error ->
                Timber.w(error, "Message send failed")
            }
        }
    }
}

sealed interface MessagesUiState {
    data object Loading : MessagesUiState

    data class Success(val messages: List<Message>) : MessagesUiState

    data class Error(val throwable: Throwable) : MessagesUiState
}
