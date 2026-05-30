package com.juhao.murexide.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.repository.ConversationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ConversationUiState {
    object Loading : ConversationUiState()
    data class Success(val conversations: List<ConversationItem>) : ConversationUiState()
    data class Error(val message: String) : ConversationUiState()
}

class ConversationViewModel(
    private val token: String,
    private val repository: ConversationRepository = ConversationRepository()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<ConversationUiState>(ConversationUiState.Loading)
    val uiState: StateFlow<ConversationUiState> = _uiState
    
    private var currentMd5: String = ""

    init {
        loadConversations()
    }

    fun loadConversations() {
        viewModelScope.launch {
            _uiState.value = ConversationUiState.Loading
            
            repository.getConversationList(token, currentMd5).onSuccess { conversations ->
                _uiState.value = ConversationUiState.Success(conversations)
            }.onFailure { error ->
                _uiState.value = ConversationUiState.Error(error.message ?: "加载失败")
            }
        }
    }

    fun refresh() {
        currentMd5 = ""
        loadConversations()
    }
}
