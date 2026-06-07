package com.juhao.murexide.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.repository.AuthRepository
import com.juhao.murexide.repository.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class MineUiState {
    object Loading : MineUiState()
    data class Success(val userInfo: UserInfo) : MineUiState()
    data class Error(val message: String) : MineUiState()
}

class MineViewModel(
    private val token: String,
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<MineUiState>(MineUiState.Loading)
    val uiState: StateFlow<MineUiState> = _uiState
    
    init {
        loadUserInfo()
    }
    
    fun loadUserInfo() {
        viewModelScope.launch {
            _uiState.value = MineUiState.Loading
            
            repository.getUserInfo(token).onSuccess { userInfo ->
                _uiState.value = MineUiState.Success(userInfo)
            }.onFailure { error ->
                _uiState.value = MineUiState.Error(error.message ?: "获取用户信息失败")
            }
        }
    }
}
