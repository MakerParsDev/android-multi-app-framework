package com.parsfilo.contentapp.feature.messages.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.auth.AuthManager
import com.parsfilo.contentapp.core.common.result.Result
import com.parsfilo.contentapp.core.common.result.asResult
import com.parsfilo.contentapp.core.model.Message
import com.parsfilo.contentapp.feature.messages.data.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val _sendState = MutableStateFlow<MessageSendState>(MessageSendState.Idle)
    val sendState: StateFlow<MessageSendState> = _sendState

    fun resetSendState() {
        // Do not unlock the composer while a Firestore write is in flight.
        if (_sendState.value != MessageSendState.Sending) _sendState.value = MessageSendState.Idle
    }

    fun sendMessage(subject: String, message: String, category: String) {
        if (_sendState.value == MessageSendState.Sending || _sendState.value == MessageSendState.Sent) return
        if (subject.isBlank() || message.isBlank()) return
        // Update synchronously to prevent a second tap from starting a second write.
        _sendState.value = MessageSendState.Sending
        viewModelScope.launch {
            runCatching { messageRepository.sendMessage(subject.trim(), message.trim(), category) }
                .onSuccess { _sendState.value = MessageSendState.Sent }
                .onFailure { error ->
                    if (error is CancellationException || error !is Exception) {
                        _sendState.value = MessageSendState.Idle
                        throw error
                    }
                    Timber.w(error, "Message send failed")
                    _sendState.value = MessageSendState.Error
                }
        }
    }
}

sealed interface MessagesUiState {
    data object Loading : MessagesUiState

    data class Success(val messages: List<Message>) : MessagesUiState

    data class Error(val throwable: Throwable) : MessagesUiState
}

/** Composer-only state; failures never expose backend exception text to users. */
sealed interface MessageSendState {
    data object Idle : MessageSendState

    data object Sending : MessageSendState

    data object Sent : MessageSendState

    data object Error : MessageSendState
}
