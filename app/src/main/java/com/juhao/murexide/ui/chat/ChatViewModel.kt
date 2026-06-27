package com.juhao.murexide.ui.chat

import android.net.Uri
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.*
import com.juhao.murexide.utils.QiniuUploader
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.repository.MessageRepository
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.group.info
import com.juhao.murexide.proto.group.info_send
import com.juhao.murexide.repository.ChatBackgroundRepository
import com.juhao.murexide.repository.StickerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ChatViewModel(
    private val token: String,
    val chatId: String,
    private val chatType: Int,
    private val deviceId: String,
    private val repository: MessageRepository = MessageRepository(),
    private val backgroundRepository: ChatBackgroundRepository = ChatBackgroundRepository(),
    private val stickerRepository: StickerRepository = StickerRepository(),
    private val wsManager: WebSocketManager = WebSocketManager.getInstance()
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val DRAFT_DEBOUNCE_MS = 500L
        private const val DRAFT_CLEAR_DELAY_MS = 300L
    }
    
    private var lastUserInputTime = 0L
    private var lastAppliedDraft = ""
    private var draftClearJob: Job? = null
    
    private var uploadJob: Job? = null

    private val msgIdCache = mutableSetOf<String>()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _recallDialog = MutableStateFlow(RecallDialogState())
    val recallDialog: StateFlow<RecallDialogState> = _recallDialog.asStateFlow()

    private val _editDialog = MutableStateFlow(EditDialogState())
    val editDialog: StateFlow<EditDialogState> = _editDialog.asStateFlow()

    private val _stickerPanel = MutableStateFlow(StickerPanelState())
    val stickerPanel: StateFlow<StickerPanelState> = _stickerPanel.asStateFlow()

    private var currentMsgId: String? = null
    private var isLoadingMore = false

    init {
        loadMessages()
        setupWebSocket()
        loadBackground()
        if (chatType == 2) { // 群聊
            loadGroupInfo()
        }
    }

    private fun loadBackground() {
        viewModelScope.launch(Dispatchers.IO) {
            backgroundRepository.getBackgroundList(token).onSuccess { list ->
                val url = backgroundRepository.resolveBackground(list, chatId)
                _uiState.update { it.copy(backgroundUrl = url) }
            }.onFailure { e ->
                Log.e(TAG, "Failed to load background", e)
            }
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
                        val match = event.message.chatId == chatId || (event.message.chatType == 1 && event.message.senderId == chatId)
                        Log.d(TAG, "New message: chatId=${event.message.chatId}, expected=$chatId, match=${match}")
                        if (match) {
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
                        updateStreamMessage(event.msgId, event.content)
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
                chatType = chatType
            ).onSuccess { messages ->
                msgIdCache.clear()
                msgIdCache.addAll(messages.map { it.msgId })

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
                msgId = currentMsgId
            ).onSuccess { messages ->
                if (messages.isNotEmpty()) {
                    val newMessages = messages.filter { it.msgId !in msgIdCache }
                    if (newMessages.isNotEmpty()) {
                        msgIdCache.addAll(newMessages.map { it.msgId })
                        _uiState.update {
                            it.copy(
                                messages = it.messages + newMessages,
                                isLoadingMore = false,
                                hasMore = true
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoadingMore = false, hasMore = true) }
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
        msgIdCache.clear()
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
            delay(DRAFT_CLEAR_DELAY_MS)
            Log.d(TAG, "Clearing draft state after ${DRAFT_CLEAR_DELAY_MS}ms delay")
            _uiState.update { it.copy(isRemoteDraft = false) }
        }
    }

    fun toggleSendType(type: String) {
        _uiState.update { it.copy(sendType = type) }
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
            val contentType = when (state.sendType) {
                "markdown" -> MessageItem.CONTENT_TYPE_MARKDOWN
                "html" -> MessageItem.CONTENT_TYPE_HTML
                else -> MessageItem.CONTENT_TYPE_TEXT
            }
            val content = MessageContent(
                text = state.inputText,
                quoteMsgText = state.replyTo?.let {
                    "${it.senderName}: ${it.content}"
                },
                quoteImageUrl = state.replyTo?.imageUrl,
                quoteImageName = state.replyTo?.imageUrl?.toUri()?.lastPathSegment
            )
            
            _uiState.update {
                it.copy(
                    requestInputFocus = true
                )
            }

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
                        isSending = false,
                        requestInputFocus = true
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isSending = false) }
                _toastMessage.emit(error.message ?: "发送失败")
            }
        }
    }

    fun uploadAndSendVideo(uri: Uri, context: Context) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    uploadProgress = 0f,
                    uploadImagePath = uri.toString(),
                    isSending = false
                )
            }
    
            try {
                val uploader = QiniuUploader(
                    context = context,
                    userToken = token,
                    uploadType = 2
                )
    
                val result = uploader.uploadFromUri(
                    context = context,
                    uri = uri,
                    onProgress = { progress ->
                        _uiState.update { it.copy(uploadProgress = progress) }
                    }
                )
    
                if (!isActive) {
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 0f,
                            uploadImagePath = null
                        )
                    }
                    return@launch
                }
    
                result.onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 1f,
                            uploadImagePath = null
                        )
                    }
                    sendVideoMessage(response.key)
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 0f,
                            uploadImagePath = null
                        )
                    }
                    _toastMessage.emit("视频上传失败: ${error.message}")
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        uploadImagePath = null
                    )
                }
                _toastMessage.emit("已取消上传")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        uploadImagePath = null
                    )
                }
                _toastMessage.emit("上传失败: ${e.message}")
            }
        }
    }
    
    private fun sendVideoMessage(videoUrl: String) {
        viewModelScope.launch {
            val content = MessageContent(
                video = videoUrl,
                text = "",
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
                contentType = MessageItem.CONTENT_TYPE_VIDEO,
                quoteMsgId = _uiState.value.replyTo?.msgId
            ).onSuccess {
                _uiState.update { 
                    it.copy(
                        replyTo = null,
                        isSending = false
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isSending = false) }
                _toastMessage.emit("发送失败: ${error.message}")
            }
        }
    }
    
    fun uploadAndSendImage(uri: Uri, context: Context) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    uploadProgress = 0f,
                    uploadImagePath = uri.toString(),
                    isSending = false
                )
            }
    
            try {
                val uploader = QiniuUploader(
                    context = context,
                    userToken = token,
                    enableWebp = true
                )
    
                val result = uploader.uploadFromUri(
                    context = context,
                    uri = uri,
                    onProgress = { progress ->
                        _uiState.update { it.copy(uploadProgress = progress) }
                    }
                )
    
                if (!isActive) {
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 0f,
                            uploadImagePath = null
                        )
                    }
                    return@launch
                }
    
                result.onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 1f,
                            uploadImagePath = null
                        )
                    }
                    sendImageMessage(response.key)
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 0f,
                            uploadImagePath = null
                        )
                    }
                    _toastMessage.emit("图片上传失败: ${error.message}")
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        uploadImagePath = null
                    )
                }
                _toastMessage.emit("已取消上传")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        uploadImagePath = null
                    )
                }
                _toastMessage.emit("上传失败: ${e.message}")
            }
        }
    }
    
    fun cancelUpload() {
        uploadJob?.cancel()
        _uiState.update { 
            it.copy(
                isUploading = false,
                uploadProgress = 0f,
                uploadImagePath = null
            )
        }
    }
    
    private fun sendImageMessage(imageUrl: String) {
        viewModelScope.launch {
            val content = MessageContent(
                image = imageUrl,
                text = "",
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
                contentType = MessageItem.CONTENT_TYPE_IMAGE,
                quoteMsgId = _uiState.value.replyTo?.msgId
            ).onSuccess {
                _uiState.update { 
                    it.copy(
                        replyTo = null,
                        isSending = false
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isSending = false) }
                _toastMessage.emit("发送失败: ${error.message}")
            }
        }
    }
    
    fun uploadAndSendFile(uri: Uri, context: Context) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    uploadProgress = 0f,
                    uploadImagePath = uri.toString(),
                    isSending = false
                )
            }
    
            try {
                val uploader = QiniuUploader(
                    context = context,
                    userToken = token,
                    uploadType = 3
                )
    
                val result = uploader.uploadFromUri(
                    context = context,
                    uri = uri,
                    onProgress = { progress ->
                        _uiState.update { it.copy(uploadProgress = progress) }
                    }
                )
    
                if (!isActive) {
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 0f,
                            uploadImagePath = null
                        )
                    }
                    return@launch
                }
    
                result.onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 1f,
                            uploadImagePath = null
                        )
                    }
                    sendFileMessage(response.key, response.fsize, uri, context)
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 0f,
                            uploadImagePath = null
                        )
                    }
                    _toastMessage.emit("文件上传失败: ${error.message}")
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        uploadImagePath = null
                    )
                }
                _toastMessage.emit("已取消上传")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        uploadImagePath = null
                    )
                }
                _toastMessage.emit("上传失败: ${e.message}")
            }
        }
    }
    
    private fun sendFileMessage(fileUrl: String, fileSize: Long, uri: Uri, context: Context) {
        viewModelScope.launch {
            val fileName = getFileNameFromUri(context, uri)
            
            val content = MessageContent(
                text = "",
                fileKey = fileUrl,
                fileName = fileName,
                fileSize = fileSize
            )
            
            repository.sendMessage(
                token = token,
                chatId = chatId,
                chatType = chatType,
                content = content,
                contentType = MessageItem.CONTENT_TYPE_FILE,
                quoteMsgId = _uiState.value.replyTo?.msgId
            ).onSuccess {
                _uiState.update { 
                    it.copy(
                        replyTo = null,
                        isSending = false
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isSending = false) }
                _toastMessage.emit("发送失败: ${error.message}")
            }
        }
    }
    
    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    return cursor.getString(nameIndex)
                }
            }
        }
        return uri.lastPathSegment ?: "file_${System.currentTimeMillis()}"
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
            sendType = when (message.contentType) {
                MessageItem.CONTENT_TYPE_MARKDOWN -> "markdown"
                MessageItem.CONTENT_TYPE_HTML -> "html"
                else -> "text"
            }
        )
    }

    fun hideEditDialog() {
        _editDialog.value = EditDialogState(isOpen = false)
    }

    fun updateEditContent(content: String) {
        _editDialog.update { it.copy(newContent = content) }
    }

    fun toggleEditSendType(type: String) {
        _editDialog.update { it.copy(sendType = type) }
    }

    fun editMessage() {
        val state = _editDialog.value
        val message = state.message ?: return
        val contentType = when (state.sendType) {
            "markdown" -> MessageItem.CONTENT_TYPE_MARKDOWN
            "html" -> MessageItem.CONTENT_TYPE_HTML
            else -> MessageItem.CONTENT_TYPE_TEXT
        }

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

    // ---------- 表情面板 ----------

    /** 切换表情面板显示/隐藏（显示前懒加载一次数据） */
    fun toggleStickerPanel() {
        val current = _stickerPanel.value
        if (current.isVisible) {
            _stickerPanel.value = current.copy(isVisible = false)
        } else {
            _stickerPanel.value = current.copy(isVisible = true)
            if (!current.isLoaded && !current.isLoading) {
                loadStickerData()
            }
        }
    }

    fun hideStickerPanel() {
        _stickerPanel.update { it.copy(isVisible = false) }
    }

    /** 同时加载个人收藏表情和表情包列表 */
    private fun loadStickerData() {
        _stickerPanel.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val exprResult = stickerRepository.getExpressionList(token)
            val packResult = stickerRepository.getStickerList(token)

            val expressions = exprResult.getOrElse {
                Log.e(TAG, "Failed to load expressions", it); emptyList()
            }
            val packs = packResult.getOrElse {
                Log.e(TAG, "Failed to load sticker packs", it); emptyList()
            }

            _stickerPanel.update {
                it.copy(
                    isLoading = false,
                    isLoaded = true,
                    expressions = expressions,
                    stickerPacks = packs
                )
            }
        }
    }

    /** 发送个人收藏表情 */
    fun sendExpression(expression: ExpressionItem) {
        val url = expression.url
        sendStickerMessage(
            imageUrl = url,
            expressionId = expression.id.toString()
        )
    }

    /** 发送表情包里的单个表情 */
    fun sendStickerItem(item: StickerItem) {
        val url = item.url
        sendStickerMessage(
            imageUrl = url,
            stickerItemId = item.id,
            stickerPackId = item.stickerPackId
        )
    }

    private fun sendStickerMessage(
        imageUrl: String,
        expressionId: String? = null,
        stickerItemId: Long? = null,
        stickerPackId: Long? = null
    ) {
        val content = MessageContent(
            image = imageUrl,
            expressionId = expressionId,
            stickerItemId = stickerItemId,
            stickerPackId = stickerPackId,
            quoteMsgText = state.replyTo?.let {
                "${it.senderName}: ${it.content}"
            },
            quoteImageUrl = state.replyTo?.imageUrl,
            quoteImageName = state.replyTo?.imageUrl?.toUri()?.lastPathSegment
        )

        viewModelScope.launch {
            repository.sendMessage(
                token = token,
                chatId = chatId,
                chatType = chatType,
                content = content,
                contentType = MessageItem.CONTENT_TYPE_STICKER,
                quoteMsgId = _uiState.value.replyTo?.msgId
            ).onSuccess {
                hideStickerPanel()
                _uiState.update { 
                    it.copy(
                        replyTo = null
                    )
                }
            }.onFailure { error ->
                _toastMessage.emit("表情发送失败: ${error.message}")
                error.printStackTrace()
            }
        }
    }

    fun addReceivedMessage(message: MessageItem) {
        if (message.msgId in msgIdCache) {
            updateEditedMessage(message)
            return
        }

        msgIdCache.add(message.msgId)
        _uiState.update {
            it.copy(messages = listOf(message) + it.messages)
        }
    }

    fun updateStreamMessage(msgId: String, content: String) {
        _uiState.update {
            it.copy(
                messages = it.messages.map { msg ->
                    if (msg.msgId == msgId)
                        msg.copy(content = msg.content + content)
                    else
                        msg
                }
            )
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
                            isEdited = true,
                            isRecalled = message.isRecalled
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

    fun enterSelectionMode(message: MessageItem) {
        _uiState.update {
            it.copy(
                selectionMode = true,
                selectedMessages = setOf(message)
            )
        }
    }
    
    fun toggleMessageSelection(message: MessageItem) {
        _uiState.update { state ->
            if (!state.selectionMode) return@update state
    
            val newSelected = if (state.selectedMessages.contains(message)) {
                state.selectedMessages - message
            } else {
                state.selectedMessages + message
            }
    
            if (newSelected.isEmpty()) {
                state.copy(
                    selectionMode = false,
                    selectedMessages = emptySet()
                )
            } else {
                state.copy(selectedMessages = newSelected)
            }
        }
    }
    
    fun exitSelectionMode() {
        _uiState.update {
            it.copy(
                selectionMode = false,
                selectedMessages = emptySet()
            )
        }
    }

    /*fun forwardSelectedMessages(targetChatId: String) {
        val selectedIds = _uiState.value.selectedMessageIds
        if (selectedIds.isEmpty()) return
        
        viewModelScope.launch {
            repository.forwardMessages(
                token = token,
                messageIds = selectedIds.toList(),
                targetChatId = targetChatId
            ).onSuccess {
                exitSelectionMode()
                _toastMessage.emit("转发成功")
            }.onFailure { error ->
                _toastMessage.emit("转发失败: ${error.message}")
            }
        }
    }*/

    fun recallSelectedMessages() {
        val selected = _uiState.value.selectedMessages
        if (selected.isEmpty()) return
    
        viewModelScope.launch {
            var successCount = 0
            var failCount = 0
    
            selected.forEach { message ->
                repository.recallMessage(
                    token = token,
                    msgId = message.msgId,
                    chatId = chatId,
                    chatType = chatType
                ).onSuccess {
                    successCount++
                    deleteMessage(message.msgId)
                }.onFailure {
                    failCount++
                }
            }
    
            exitSelectionMode()
    
            when {
                failCount == 0 -> _toastMessage.emit("撤回成功")
                successCount == 0 -> _toastMessage.emit("撤回失败")
                else -> _toastMessage.emit("成功撤回 $successCount 条，失败 $failCount 条")
            }
        }
    }
    
    fun showKeyboard() {
        _uiState.update { it.copy(requestInputFocus = true) }
    }
    
    fun onInputFocusConsumed() {
        _uiState.update { it.copy(requestInputFocus = false) }
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
    val sendType: String = "text"
)

data class StickerPanelState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    val expressions: List<ExpressionItem> = emptyList(),
    val stickerPacks: List<StickerPack> = emptyList()
)