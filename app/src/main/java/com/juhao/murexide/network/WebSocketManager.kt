package com.juhao.murexide.network

import android.util.Log
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.MessageTag
import com.juhao.murexide.proto.chat_ws_go.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.onTimeout
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.UUID

class WebSocketManager private constructor() {
    companion object {
        private const val TAG = "WebSocketManager"
        private const val WS_URL = "wss://chat-ws-go.jwzhd.com/ws"
        private const val HEARTBEAT_INTERVAL = 15_000L
        private const val CONNECT_TIMEOUT = 2_500L

        @Volatile
        private var instance: WebSocketManager? = null

        fun getInstance(): WebSocketManager {
            return instance ?: synchronized(this) {
                instance ?: WebSocketManager().also { instance = it }
            }
        }
    }

    private var webSocket: WebSocket? = null
    @Volatile private var isConnected = false
    @Volatile private var connectionVersion = 0
    private var currentUserId: String? = null
    private var currentToken: String? = null
    private var currentDeviceId: String? = null
    private var currentPlatform: String? = null
    @Volatile private var lastHeartbeatAckTime = 0L
    private var heartbeatJob: Job? = null

    private var maintainerJob: Job? = null
    private val connectionSignal = Channel<Unit>(Channel.CONFLATED)

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _messageFlow = MutableSharedFlow<WsEvent>()
    val messageFlow: SharedFlow<WsEvent> = _messageFlow.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = NetworkClient.okHttpClient

    sealed class WsEvent {
        data class NewMessage(val message: MessageItem) : WsEvent()
        data class EditMessage(val message: MessageItem) : WsEvent()
        data class StreamContent(val msgId: String, val content: String) : WsEvent()
        data class DraftUpdate(val chatId: String, val draft: String) : WsEvent()
        data class MessageDeleted(val msgId: String) : WsEvent()
        object Connected : WsEvent()
        object Disconnected : WsEvent()
    }

    fun connect(userId: String, token: String, deviceId: String, platform: String = "android") {
        val credsChanged = currentUserId != userId ||
                currentToken != token ||
                currentDeviceId != deviceId ||
                currentPlatform != platform

        currentUserId = userId
        currentToken = token
        currentDeviceId = deviceId
        currentPlatform = platform

        if (credsChanged || !isConnected) {
            connectionVersion++
            closeCurrentSocket()
            handleDisconnect()
            startMaintainer()
            connectionSignal.trySend(Unit)
        }
    }

    private fun startMaintainer() {
        maintainerJob?.cancel()
        maintainerJob = scope.launch {
            var backoff = 1000L
            while (isActive && currentUserId != null && currentToken != null) {
                if (isConnected) {
                    connectionSignal.receive()
                    backoff = 1000L
                    continue
                }

                createConnection()

                val connected = waitForConnectionOrTimeout(CONNECT_TIMEOUT)
                if (connected) {
                    backoff = 1000L
                } else {
                    select<Unit> {
                        connectionSignal.onReceive { }
                        onTimeout(backoff) {
                        }
                    }
                    backoff = minOf(backoff * 2, 30_000L)
                }
            }
        }
    }

    private fun createConnection() {
        connectionVersion++
        val thisVersion = connectionVersion

        closeCurrentSocket()

        val request = Request.Builder().url(WS_URL).build()
        Log.d(TAG, "Opening WebSocket (version=$thisVersion)")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (thisVersion != connectionVersion) {
                    Log.d(TAG, "Ignoring stale onOpen (v$thisVersion)")
                    return
                }
                Log.d(TAG, "WebSocket connected (v$thisVersion)")
                isConnected = true
                _connectionState.value = true
                lastHeartbeatAckTime = System.currentTimeMillis()
                sendLogin(
                    currentUserId ?: return,
                    currentToken ?: return,
                    currentDeviceId ?: return,
                    currentPlatform ?: "android"
                )
                startHeartbeat()
                scope.launch { _messageFlow.emit(WsEvent.Connected) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (thisVersion != connectionVersion) return
                handleBinaryMessage(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (thisVersion != connectionVersion) return
                Log.d(TAG, "Received text message: $text")
                handleTextMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (thisVersion != connectionVersion) return
                Log.d(TAG, "WebSocket closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (thisVersion != connectionVersion) return
                Log.d(TAG, "WebSocket closed: $code / $reason")
                onConnectionLost()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (thisVersion != connectionVersion) return
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                onConnectionLost()
            }
        })
    }

    private fun onConnectionLost() {
        if (isConnected) {
            handleDisconnect()
        }
        connectionSignal.trySend(Unit)
    }

    private suspend fun waitForConnectionOrTimeout(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isConnected) return true
            delay(100)
        }
        return isConnected
    }

    private fun closeCurrentSocket() {
        webSocket?.let {
            try {
                it.cancel()
            } catch (_: Exception) {
            }
        }
        webSocket = null
    }

    private fun handleDisconnect() {
        stopHeartbeat()
        isConnected = false
        _connectionState.value = false
        webSocket = null
        scope.launch {
            _messageFlow.emit(WsEvent.Disconnected)
        }
    }

    fun manualReconnect() {
        closeCurrentSocket()
        handleDisconnect()
        startMaintainer()
        connectionSignal.trySend(Unit)
    }

    fun notifyNetworkLost() {
        closeCurrentSocket()
        handleDisconnect()
        connectionSignal.trySend(Unit)
    }

    private fun sendLogin(userId: String, token: String, deviceId: String, platform: String) {
        val json = JSONObject().apply {
            put("seq", UUID.randomUUID().toString())
            put("cmd", "login")
            put("data", JSONObject().apply {
                put("userId", userId)
                put("token", token)
                put("platform", platform)
                put("deviceId", deviceId)
            })
        }
        webSocket?.send(json.toString())
        Log.d(TAG, "Login sent")
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isConnected) {
                delay(HEARTBEAT_INTERVAL)
                if (!isConnected) break
                val now = System.currentTimeMillis()
                if (now - lastHeartbeatAckTime > HEARTBEAT_INTERVAL * 2) {
                    Log.w(TAG, "Heartbeat timeout")
                    onConnectionLost()
                    break
                }
                sendHeartbeat()
            }
        }
    }

    private fun sendHeartbeat() {
        val json = JSONObject().apply {
            put("seq", UUID.randomUUID().toString())
            put("cmd", "heartbeat")
            put("data", JSONObject())
        }
        webSocket?.send(json.toString())
        Log.d(TAG, "Heartbeat sent")
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    fun sendDraftSync(chatId: String, draft: String, deviceId: String) {
        if (!isConnected) {
            Log.w(TAG, "WebSocket not connected")
            return
        }
        val json = JSONObject().apply {
            put("seq", UUID.randomUUID().toString())
            put("cmd", "inputInfo")
            put("data", JSONObject().apply {
                put("chatId", chatId)
                put("input", draft)
                put("deviceId", deviceId)
            })
        }
        webSocket?.send(json.toString())
        Log.d(TAG, "Draft sync sent")
    }

    private fun handleTextMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (val cmd = json.optString("cmd", "")) {
                "heartbeat_ack" -> {
                    Log.d(TAG, "Heartbeat ACK received")
                    lastHeartbeatAckTime = System.currentTimeMillis()
                }
                else -> {
                    Log.d(TAG, "Unknown command: $cmd")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse text message", e)
        }
    }

    private fun handleBinaryMessage(data: ByteArray) {
        Log.d(TAG, "Received binary message, size: ${data.size} bytes")
        try {
            val base = heartbeat_ack.ADAPTER.decode(data)
            val cmd = base.info?.cmd
            Log.d(TAG, "Binary message cmd: $cmd")

            when (cmd) {
                "heartbeat_ack" -> {
                    Log.d(TAG, "Heartbeat ACK received (Binary)")
                    lastHeartbeatAckTime = System.currentTimeMillis()
                }
                "push_message" -> {
                    val pushMessage = push_message.ADAPTER.decode(data)
                    pushMessage.data_?.msg?.let { msg ->
                        Log.d(TAG, "New message: msgId=${msg.msg_id}, chatId=${msg.chat_id}")
                        val messageItem = parseWsMessage(msg)
                        scope.launch {
                            _messageFlow.emit(WsEvent.NewMessage(messageItem))
                        }
                    }
                }
                "edit_message" -> {
                    val editMessage = edit_message.ADAPTER.decode(data)
                    editMessage.data_?.msg?.let { msg ->
                        Log.d(TAG, "Edit message: msgId=${msg.msg_id}, chatId=${msg.chat_id}")
                        val messageItem = parseEditMessage(msg)
                        scope.launch {
                            _messageFlow.emit(WsEvent.EditMessage(messageItem))
                        }
                    }
                }
                "stream_message" -> {
                    val streamMessage = stream_message.ADAPTER.decode(data)
                    streamMessage.data_?.msg?.let { msg ->
                        scope.launch {
                            _messageFlow.emit(WsEvent.StreamContent(msg.msg_id, msg.content))
                        }
                    }
                }
                "draft_input" -> {
                    val draftInput = draft_input.ADAPTER.decode(data)
                    draftInput.data_?.draft?.let { draft ->
                        scope.launch {
                            _messageFlow.emit(WsEvent.DraftUpdate(draft.chat_id, draft.input))
                        }
                    }
                }
                "file_send_message" -> {
                    val fileMsg = file_send_message.ADAPTER.decode(data)
                    Log.d(TAG, "File send message: ${fileMsg.data_?.sender?.send_type}")
                }
                "bot_board_message" -> {
                    val boardMsg = bot_board_message.ADAPTER.decode(data)
                    Log.d(TAG, "Bot board message from: ${boardMsg.data_?.board?.bot_name}")
                }
                else -> {
                    Log.w(TAG, "Unknown binary command: $cmd")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse binary message", e)
        }
    }

    private fun parseWsMessage(msg: com.juhao.murexide.proto.chat_ws_go.WsMsg): MessageItem {
        return MessageItem(
            msgId = msg.msg_id,
            senderId = msg.sender?.chat_id ?: "",
            senderName = msg.sender?.name ?: "",
            senderAvatar = msg.sender?.avatar_url ?: "",
            chatId = msg.chat_id,
            chatType = msg.chat_type,
            content = msg.content?.text ?: "",
            contentType = msg.content_type,
            timestamp = msg.timestamp,
            msgSeq = msg.msg_seq,
            direction = if (msg.sender?.chat_id == currentUserId) "right" else "left",
            isRecalled = msg.delete_time > 0,
            isEdited = msg.edit_time > 0,
            quoteMsgId = msg.quote_msg_id.takeIf { it.isNotEmpty() },
            quoteMsgText = msg.content?.quote_msg_text?.takeIf { it.isNotEmpty() },
            imageUrl = msg.content?.image_url?.takeIf { it.isNotEmpty() },
            audioUrl = msg.content?.audio_url?.takeIf { it.isNotEmpty() },
            audioTime = if ((msg.content?.audio_time ?: 0) > 0) msg.content?.audio_time?.toInt() else null,
            videoUrl = msg.content?.video_url?.takeIf { it.isNotEmpty() },
            fileUrl = msg.content?.file_url?.takeIf { it.isNotEmpty() },
            fileName = msg.content?.file_name?.takeIf { it.isNotEmpty() },
            fileSize = if ((msg.content?.file_size ?: 0) > 0) msg.content?.file_size else null,
            cmdName = msg.cmd?.name?.takeIf { it.isNotEmpty() },
            cmdId = msg.cmd?.id,
            tags = msg.sender?.tag?.map { tag ->
                MessageTag(
                    id = tag.id,
                    text = tag.text,
                    color = tag.color
                )
            } ?: emptyList()
        )
    }

    private fun parseEditMessage(msg: com.juhao.murexide.proto.chat_ws_go.WsMsg): MessageItem {
        return MessageItem(
            msgId = msg.msg_id,
            senderId = "",
            senderName = "",
            senderAvatar = "",
            chatId = msg.chat_id,
            chatType = 1,
            content = msg.content?.text ?: "",
            contentType = msg.content_type,
            timestamp = System.currentTimeMillis(),
            msgSeq = 0,
            direction = "left",
            isRecalled = false,
            isEdited = true,
            quoteMsgId = msg.quote_msg_id.takeIf { it.isNotEmpty() },
            quoteMsgText = msg.content?.quote_msg_text?.takeIf { it.isNotEmpty() }
        )
    }

    fun disconnect() {
        currentUserId = null
        currentToken = null
        maintainerJob?.cancel()
        maintainerJob = null
        connectionVersion++
        closeCurrentSocket()
        stopHeartbeat()
        isConnected = false
        _connectionState.value = false
    }
}