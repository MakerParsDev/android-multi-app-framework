package com.parsfilo.contentapp.feature.auth.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsfilo.contentapp.core.auth.AuthManager
import com.parsfilo.contentapp.core.auth.AuthManager.SignInResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {

    val isSignedIn: StateFlow<Boolean> = authManager.authState

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val signInInProgress = AtomicBoolean(false)

    fun signIn(activityContext: Activity) {
        if (!signInInProgress.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                when (authManager.signIn(activityContext)) {
                    SignInResult.Success -> Unit
                    SignInResult.NoCredential -> {
                        _errorMessage.value =
                            "Cihazda kullanılabilir Google hesabı bulunamadı. " +
                            "Önce Google hesabı ekleyip tekrar deneyin."
                    }
                    SignInResult.ReauthRequired -> {
                        _errorMessage.value =
                            "Google hesabınızın yeniden doğrulanması gerekiyor. " +
                            "Ayarlar > Google hesabınızdan tekrar giriş yapıp yeniden deneyin."
                    }
                    SignInResult.Cancelled -> Unit
                    SignInResult.Failure -> {
                        _errorMessage.value = "Giriş başarısız oldu. Lütfen tekrar deneyin."
                    }
                }
            } finally {
                _isLoading.value = false
                signInInProgress.set(false)
            }
        }
    }

    fun signOut() {
        authManager.signOut()
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
