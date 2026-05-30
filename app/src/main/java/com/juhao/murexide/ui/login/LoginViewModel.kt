package com.juhao.murexide.ui.login

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.datastore.TokenStorage
import com.juhao.murexide.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val token: String) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel(
    application: Application,
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {
    
    private val tokenStorage = TokenStorage(application)
    
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            
            val deviceId = getDeviceId()
            
            repository.login(email, password, deviceId).onSuccess { token ->
                tokenStorage.saveToken(token)
                _uiState.value = LoginUiState.Success(token)
            }.onFailure { error ->
                _uiState.value = LoginUiState.Error(error.message ?: "登录失败")
            }
        }
    }

    private fun getDeviceId(): String {
        return "android_device_${System.currentTimeMillis()}"
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}
