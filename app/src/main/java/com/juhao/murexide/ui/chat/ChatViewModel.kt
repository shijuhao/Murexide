package com.juhao.murexide.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.*
import com.juhao.murexide.repository.MessageRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val token: String,
    private val chatId: String,
    private val chatType: Int,
    private val repository: MessageRepository = MessageRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _recallDialog = MutableStateFlow(RecallDialogState())
    val recallDialog: StateFlow<RecallDialogState> = _recallDialog.asStateFlow()

    private val _editDialog = MutableStateFlow(EditDialogState())
    val editDialog: StateFlow<EditDialogState> = _editDialog.asStateFlow()

    private var currentMsgId: String? = null
    private var isLoadingMore = false

    init {
        loadMessages()
    }

    fun loadMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.getMessageList(
                token = token,
                chatId = chatId,
                chatType = chatType,
                msgCount = 20
            ).onSuccess { messages ->
                _uiState.update {
                    it.copy(
                        messages = messages,
                        isLoading = false,
                        hasMore = messages.isNotEmpty(),
                        error = null
                    )
                }
                if (messages.isNotEmpty()) {
                    currentMsgId = messages.last().msgId
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "加载失败"
                    )
                }
            }
        }
    }

    fun loadMore() {
        if (isLoadingMore || !_uiState.value.hasMore) return

        viewModelScope.launch {
            isLoadingMore = true
            _uiState.update { it.copy(isLoadingMore = true) }

            repository.getMessageList(
                token = token,
                chatId = chatId,
                chatType = chatType,
                msgId = currentMsgId,
                msgCount = 20
            ).onSuccess { messages ->
                if (messages.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            messages = it.messages + messages,
                            isLoadingMore = false,
                            hasMore = true
                        )
                    }
                    currentMsgId = messages.last().msgId
                } else {
                    _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingMore = false) }
            }

            isLoadingMore = false
        }
    }

    fun refresh() {
        currentMsgId = null
        loadMessages()
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun toggleMarkdown() {
        _uiState.update { it.copy(isMarkdown = !it.isMarkdown) }
    }

    fun addImage(imageUri: String) {
        _uiState.update {
            it.copy(selectedImages = it.selectedImages + imageUri)
        }
    }

    fun removeImage(index: Int) {
        _uiState.update {
            it.copy(selectedImages = it.selectedImages.filterIndexed { i, _ -> i != index })
        }
    }

    fun setReplyTo(message: MessageItem) {
        _uiState.update { it.copy(replyTo = message) }
    }

    fun clearReplyTo() {
        _uiState.update { it.copy(replyTo = null) }
    }

    fun sendMessage() {
        val state = _uiState.value
        if (state.inputText.isBlank() && state.selectedImages.isEmpty()) return

        viewModelScope.launch {
            val contentType = if (state.isMarkdown) MessageItem.CONTENT_TYPE_MARKDOWN else MessageItem.CONTENT_TYPE_TEXT
            val content = MessageContent(
                text = state.inputText,
                image = state.selectedImages.firstOrNull()
            )

            repository.sendMessage(
                token = token,
                chatId = chatId,
                chatType = chatType,
                content = content,
                contentType = contentType,
                quoteMsgId = state.replyTo?.msgId
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        inputText = "",
                        selectedImages = emptyList(),
                        replyTo = null
                    )
                }
                refresh()
                _toastMessage.emit("发送成功")
            }.onFailure { error ->
                _toastMessage.emit(error.message ?: "发送失败")
            }
        }
    }

    fun showRecallDialog(msgId: String) {
        _recallDialog.value = RecallDialogState(isOpen = true, msgId = msgId)
    }

    fun hideRecallDialog() {
        _recallDialog.value = RecallDialogState(isOpen = false)
    }

    fun recallMessage() {
        val msgId = _recallDialog.value.msgId ?: return

        viewModelScope.launch {
            repository.recallMessage(
                token = token,
                msgId = msgId,
                chatId = chatId,
                chatType = chatType
            ).onSuccess {
                hideRecallDialog()
                refresh()
                _toastMessage.emit("撤回成功")
            }.onFailure { error ->
                hideRecallDialog()
                _toastMessage.emit(error.message ?: "撤回失败")
            }
        }
    }

    fun showEditDialog(message: MessageItem) {
        _editDialog.value = EditDialogState(
            isOpen = true,
            message = message,
            newContent = message.content,
            isMarkdown = message.isMarkdownMessage
        )
    }

    fun hideEditDialog() {
        _editDialog.value = EditDialogState(isOpen = false)
    }

    fun updateEditContent(content: String) {
        _editDialog.update { it.copy(newContent = content) }
    }

    fun toggleEditMarkdown() {
        _editDialog.update { it.copy(isMarkdown = !it.isMarkdown) }
    }

    fun editMessage() {
        val state = _editDialog.value
        val message = state.message ?: return
        val contentType = if (state.isMarkdown) MessageItem.CONTENT_TYPE_MARKDOWN else MessageItem.CONTENT_TYPE_TEXT

        viewModelScope.launch {
            val content = MessageContent(text = state.newContent)

            repository.editMessage(
                token = token,
                msgId = message.msgId,
                chatId = chatId,
                chatType = chatType,
                content = content,
                contentType = contentType
            ).onSuccess {
                hideEditDialog()
                refresh()
                _toastMessage.emit("编辑成功")
            }.onFailure { error ->
                hideEditDialog()
                _toastMessage.emit(error.message ?: "编辑失败")
            }
        }
    }

    fun addReceivedMessage(message: MessageItem) {
        if (message.chatId != chatId || message.chatType != chatType) return

        _uiState.update {
            it.copy(messages = listOf(message) + it.messages)
        }
    }

    fun updateMessage(message: MessageItem) {
        _uiState.update {
            it.copy(
                messages = it.messages.map { msg ->
                    if (msg.msgId == message.msgId) message else msg
                }
            )
        }
    }

    fun deleteMessage(msgId: String) {
        _uiState.update {
            it.copy(
                messages = it.messages.map { msg ->
                    if (msg.msgId == msgId) msg.copy(isRecalled = true) else msg
                }
            )
        }
    }
}

data class RecallDialogState(
    val isOpen: Boolean = false,
    val msgId: String? = null
)

data class EditDialogState(
    val isOpen: Boolean = false,
    val message: MessageItem? = null,
    val newContent: String = "",
    val isMarkdown: Boolean = false
)

class ChatViewModelFactory(
    private val token: String,
    private val chatId: String,
    private val chatType: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(token, chatId, chatType) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
