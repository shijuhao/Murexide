package com.juhao.murexide.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.*
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.repository.MessageRepository
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.group.info
import com.juhao.murexide.proto.group.info_send
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ChatViewModel(
    private val token: String,
    private val chatId: String,
    private val chatType: Int,
    private val deviceId: String,
    private val repository: MessageRepository = MessageRepository(),
    private val wsManager: WebSocketManager = WebSocketManager.getInstance()
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val DRAFT_DEBOUNCE_MS = 500L
        private const val DRAFT_CLEAR_DELAY_MS = 300L
    }
    
    private var lastUserInputTime = 0L
    private var lastAppliedDraft = ""
    private var draftClearJob: kotlinx.coroutines.Job? = null

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
        setupWebSocket()
        if (chatType == 2) { // 群聊
            loadGroupInfo()
        }
    }

    private fun loadGroupInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val requestProto = info_send(group_id = chatId)
                val requestBody = requestProto.encode().toRequestBody("application/octet-stream".toMediaType())
                
                val request = Request.Builder()
                    .url("${NetworkClient.BASE_URL}/v1/group/info")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                NetworkClient.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body.bytes()
                        val groupInfo = info.ADAPTER.decode(body)
                        if (groupInfo.status?.code == 1) {
                            val memberCount = groupInfo.data_?.member
                            _uiState.update { it.copy(memberCount = memberCount) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load group info", e)
            }
        }
    }

    private fun setupWebSocket() {
        viewModelScope.launch {
            wsManager.messageFlow.collect { event ->
                Log.d(TAG, "Received WS event: ${event::class.simpleName}")
                when (event) {
                    is WebSocketManager.WsEvent.NewMessage -> {
                        Log.d(TAG, "New message: chatId=${event.message.chatId}, expected=$chatId, match=${event.message.chatId == chatId}")
                        if (event.message.chatId == chatId && event.message.chatType == chatType) {
                            Log.d(TAG, "Adding new message to UI")
                            addReceivedMessage(event.message)
                        }
                    }
                    is WebSocketManager.WsEvent.EditMessage -> {
                        Log.d(TAG, "Edit message: chatId=${event.message.chatId}, expected=$chatId")
                        if (event.message.chatId == chatId) {
                            updateEditedMessage(event.message)
                        }
                    }
                    is WebSocketManager.WsEvent.StreamContent -> {
                        Log.d(TAG, "Stream content: msgId=${event.msgId}")
                        // 处理流式消息内容追加
                        // TODO: 根据msgId找到对应消息并追加内容
                    }
                    is WebSocketManager.WsEvent.DraftUpdate -> {
                        Log.d(TAG, "Draft update from WS: chatId=${event.chatId}, expected=$chatId")
                        if (event.chatId == chatId) {
                            updateInputTextFromWs(event.draft)
                        }
                    }
                    is WebSocketManager.WsEvent.MessageDeleted -> {
                        Log.d(TAG, "Message deleted: msgId=${event.msgId}")
                        deleteMessage(event.msgId)
                    }
                    else -> {}
                }
            }
        }
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
        if (_uiState.value.inputText == text) return
        
        lastUserInputTime = System.currentTimeMillis()
        
        _uiState.update { it.copy(inputText = text) }
        
        wsManager.sendDraftSync(chatId, text, deviceId)
    }

    private fun updateInputTextFromWs(draft: String) {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastInput = currentTime - lastUserInputTime
        
        if (timeSinceLastInput < DRAFT_DEBOUNCE_MS) {
            Log.d(TAG, "Ignoring remote draft: user is typing (${timeSinceLastInput}ms since last input)")
            return
        }
        
        if (draft == lastAppliedDraft) {
            Log.d(TAG, "Ignoring remote draft: same as last applied draft")
            return
        }
        
        val currentInput = _uiState.value.inputText
        if (currentInput == draft) {
            Log.d(TAG, "Ignoring remote draft: already matches current input")
            lastAppliedDraft = draft
            return
        }
        
        Log.d(TAG, "Applying remote draft: '$draft' (was '$currentInput')")
        _uiState.update { it.copy(inputText = draft) }
        lastAppliedDraft = draft
        
        draftClearJob?.cancel()
        draftClearJob = viewModelScope.launch {
            kotlinx.coroutines.delay(DRAFT_CLEAR_DELAY_MS)
            Log.d(TAG, "Clearing draft state after ${DRAFT_CLEAR_DELAY_MS}ms delay")
            _uiState.update { it.copy(isRemoteDraft = false) }
        }
    }

    fun toggleMarkdown() {
        _uiState.update { it.copy(isMarkdown = !it.isMarkdown) }
    }

    fun setReplyTo(message: MessageItem) {
        _uiState.update { it.copy(replyTo = message) }
    }

    fun clearReplyTo() {
        _uiState.update { it.copy(replyTo = null) }
    }

    fun sendMessage() {
        val state = _uiState.value
        if (state.inputText.isBlank() || state.isSending) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val contentType = if (state.isMarkdown) MessageItem.CONTENT_TYPE_MARKDOWN else MessageItem.CONTENT_TYPE_TEXT
            val content = MessageContent(
                text = state.inputText,
                quoteMsgText = state.replyTo?.let {
                    "${it.senderName}: ${it.content}"
                },
                quoteImageUrl = state.replyTo?.imageUrl,
                quoteImageName = state.replyTo?.imageUrl?.toUri()?.lastPathSegment
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
                        replyTo = null,
                        isSending = false
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isSending = false) }
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
                deleteMessage(msgId)
                _toastMessage.emit("撤回成功")
            }.onFailure { error ->
                hideRecallDialog()
                _toastMessage.emit("撤回失败: ${error.message}")
                error.printStackTrace()
            }
        }
    }

    fun showEditDialog(message: MessageItem) {
        _editDialog.value = EditDialogState(
            isOpen = true,
            message = message,
            newContent = message.content,
            isMarkdown = message.contentType == MessageItem.CONTENT_TYPE_MARKDOWN
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
                val updatedMessage = message.copy(
                    content = state.newContent,
                    isEdited = true
                )
                updateEditedMessage(updatedMessage)
                _toastMessage.emit("编辑成功")
            }.onFailure { error ->
                hideEditDialog()
                _toastMessage.emit("编辑失败: ${error.message}")
                error.printStackTrace()
            }
        }
    }

    fun addReceivedMessage(message: MessageItem) {
        if (message.chatId != chatId || message.chatType != chatType) return

        _uiState.update {
            it.copy(messages = listOf(message) + it.messages)
        }
    }

    fun updateEditedMessage(message: MessageItem) {
        _uiState.update {
            it.copy(
                messages = it.messages.map { msg ->
                    if (msg.msgId == message.msgId) 
                        msg.copy(
                            content = message.content,
                            contentType = message.contentType,
                            isEdited = true
                        )
                    else 
                        msg
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