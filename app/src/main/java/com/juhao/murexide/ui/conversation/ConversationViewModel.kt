package com.juhao.murexide.ui.conversation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.repository.ConversationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.juhao.murexide.network.NetworkClient
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType

sealed class ConversationUiState {
    object Loading : ConversationUiState()
    data class Success(
        val conversations: List<ConversationItem>,
        val stickyConversations: List<StickyItem> = emptyList()
    ) : ConversationUiState()
    data class Error(val message: String) : ConversationUiState()
}

@Serializable
data class StickyItem(
    val id: Long,
    val chatType: Int,
    val chatId: String,
    val chatName: String,
    val avatarUrl: String,
    val certificationLevel: Int
)

@Serializable
data class StickyListResponse(
    val code: Int,
    val data: StickyData? = null,
    val msg: String
)

@Serializable
data class StickyData(
    val sticky: List<StickyItem> = emptyList()
)

class ConversationViewModel(
    private val token: String,
    private val repository: ConversationRepository = ConversationRepository(),
    private val wsManager: WebSocketManager = WebSocketManager.getInstance()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<ConversationUiState>(ConversationUiState.Loading)
    val uiState: StateFlow<ConversationUiState> = _uiState

    private val _isWsConnected = MutableStateFlow(true)
    val isWsConnected: StateFlow<Boolean> = _isWsConnected
    
    private var currentMd5: String = ""
    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadConversations()
        observeWebSocket()
        observeWsConnection()
    }

    private fun observeWsConnection() {
        viewModelScope.launch {
            wsManager.connectionState.collect { connected ->
                _isWsConnected.value = connected
            }
        }
    }

    private fun observeWebSocket() {
        viewModelScope.launch {
            wsManager.messageFlow.collect { event ->
                if (event is WebSocketManager.WsEvent.NewMessage) {
                    handleNewMessage(event.message)
                }
            }
        }
    }

    private fun handleNewMessage(message: com.juhao.murexide.data.MessageItem) {
        _uiState.update { state ->
            if (state is ConversationUiState.Success) {
                val conversations = state.conversations.toMutableList()
                val index = conversations.indexOfFirst { it.chatId == message.chatId }
                
                if (index != -1) {
                    val oldConv = conversations.removeAt(index)
                    val updatedConv = oldConv.copy(
                        chatContent = message.getDisplayContent(),
                        timestampMs = message.timestamp,
                        unreadMessage = oldConv.unreadMessage + 1
                    )
                    conversations.add(0, updatedConv)
                    state.copy(conversations = conversations)
                } else {
                    refresh()
                    state
                }
            } else {
                state
            }
        }
    }

    fun loadConversations() {
        viewModelScope.launch {
            _uiState.value = ConversationUiState.Loading

            fetchStickyList()
            repository.getConversationList(token, currentMd5).onSuccess { conversations ->
                _uiState.update { state ->
                    if (state is ConversationUiState.Success) {
                        state.copy(conversations = conversations)
                    } else {
                        ConversationUiState.Success(conversations = conversations)
                    }
                }
            }.onFailure { error ->
                _uiState.value = ConversationUiState.Error(error.message ?: "加载失败")
            }
        }
    }

    private fun fetchStickyList() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val requestBody = "{}".toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url("${NetworkClient.BASE_URL}/v1/sticky/list")
                        .post(requestBody)
                        .header("token", token)
                        .build()

                    NetworkClient.okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body.string()
                            Log.d("ConversationViewModel", "Sticky list response: $body")
                            val stickyResponse = json.decodeFromString<StickyListResponse>(body)
                            if (stickyResponse.code == 1) {
                                val stickyList = stickyResponse.data?.sticky ?: emptyList()
                                _uiState.update { state ->
                                    if (state is ConversationUiState.Success) {
                                        state.copy(stickyConversations = stickyList)
                                    } else {
                                        ConversationUiState.Success(
                                            conversations = emptyList(),
                                            stickyConversations = stickyList
                                        )
                                    }
                                }
                            }
                        } else {
                            Log.e("ConversationViewModel", "Sticky list error: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ConversationViewModel", "Failed to fetch sticky list", e)
                }
            }
        }
    }

    fun refresh() {
        currentMd5 = ""
        loadConversations()
    }

    fun clearUnread(chatId: String) {
        val currentState = _uiState.value
        if (currentState is ConversationUiState.Success) {
            val conversations = currentState.conversations.map {
                if (it.chatId == chatId) it.copy(unreadMessage = 0) else it
            }
            _uiState.update { currentState.copy(conversations = conversations) }
        }
    }
}
