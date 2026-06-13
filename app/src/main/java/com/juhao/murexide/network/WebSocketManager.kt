package com.juhao.murexide.network

import android.util.Log
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.MessageTag
import com.juhao.murexide.proto.chat_ws_go.push_message
import com.juhao.murexide.proto.chat_ws_go.edit_message
import com.juhao.murexide.proto.chat_ws_go.stream_message
import com.juhao.murexide.proto.chat_ws_go.draft_input
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.UUID
import java.util.concurrent.TimeUnit

class WebSocketManager {
    companion object {
        private const val TAG = "WebSocketManager"
        private const val WS_URL = "wss://chat-ws-go.jwzhd.com/ws"
        private const val HEARTBEAT_INTERVAL = 30000L
    }

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var heartbeatRunnable: Runnable? = null
    
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
        data class Error(val message: String) : WsEvent()
    }

    fun connect(userId: String, token: String, deviceId: String, platform: String = "android") {
        if (isConnected) {
            Log.w(TAG, "WebSocket already connected")
            return
        }

        val request = Request.Builder()
            .url(WS_URL)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                isConnected = true
                sendLogin(userId, token, deviceId, platform)
                startHeartbeat()
                scope.launch {
                    _messageFlow.emit(WsEvent.Connected)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleBinaryMessage(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received text message: $text")
                handleTextMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                stopHeartbeat()
                isConnected = false
                scope.launch {
                    _messageFlow.emit(WsEvent.Disconnected)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}", t)
                stopHeartbeat()
                isConnected = false
                scope.launch {
                    _messageFlow.emit(WsEvent.Error(t.message ?: "连接失败"))
                }
            }
        })
    }

    private fun sendLogin(userId: String, token: String, deviceId: String, platform: String) {
        Log.d(TAG, "Login params: userId=$userId, token=$token, deviceId=$deviceId, platform=$platform")
        val loginJson = """
            {
                "seq": "${UUID.randomUUID()}",
                "cmd": "login",
                "data": {
                    "userId": "$userId",
                    "token": "$token",
                    "platform": "$platform",
                    "deviceId": "$deviceId"
                }
            }
        """.trimIndent()
        
        webSocket?.send(loginJson)
        Log.d(TAG, "Login sent: $loginJson")
    }

    private fun startHeartbeat() {
        heartbeatRunnable = object : Runnable {
            override fun run() {
                if (isConnected) {
                    sendHeartbeat()
                    webSocket?.let { _ ->
                        (client.dispatcher.executorService as? java.util.concurrent.ScheduledExecutorService)?.schedule(
                            this, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS
                        )
                    }
                }
            }
        }

        scheduleNextHeartbeat()
    }

    private fun scheduleNextHeartbeat() {
        scope.launch {
            while (isConnected) {
                kotlinx.coroutines.delay(HEARTBEAT_INTERVAL)
                if (isConnected) {
                    sendHeartbeat()
                }
            }
        }
    }

    private fun sendHeartbeat() {
        val heartbeatJson = """
            {
                "seq": "${UUID.randomUUID()}",
                "cmd": "heartbeat",
                "data": {}
            }
        """.trimIndent()
        
        webSocket?.send(heartbeatJson)
        Log.d(TAG, "Heartbeat sent")
    }

    private fun stopHeartbeat() {
        heartbeatRunnable = null
    }

    fun sendDraftSync(chatId: String, draft: String, deviceId: String) {
        if (!isConnected) {
            Log.w(TAG, "WebSocket not connected")
            return
        }

        val draftJson = """
            {
                "seq": "${UUID.randomUUID()}",
                "cmd": "inputInfo",
                "data": {
                    "chatId": "$chatId",
                    "input": "$draft",
                    "deviceId": "$deviceId"
                }
            }
        """.trimIndent()
        
        webSocket?.send(draftJson)
        Log.d(TAG, "Draft sync sent")
    }

    private fun handleTextMessage(text: String) {
        try {
            // 尝试解析JSON格式的消息
            val json = org.json.JSONObject(text)
            when (val cmd = json.optString("cmd", "")) {
                "heartbeat_ack" -> {
                    Log.d(TAG, "Heartbeat ACK received")
                }
                "push_message", "edit_message", "stream_message", "draft_input" -> {
                    // JSON格式的消息,需要转换为ProtoBuf或直接解析
                    Log.w(TAG, "Received JSON format message, need to convert to ProtoBuf: $cmd")
                    // TODO: 如果服务器发送JSON,需要在这里处理
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
            val pushMessage = push_message.ADAPTER.decode(data)
            Log.d(TAG, "Decoded push_message, cmd: ${pushMessage.info?.cmd}")
            if (pushMessage.info?.cmd == "push_message") {
                val msg = pushMessage.data_?.msg
                Log.d(TAG, "New message: msgId=${msg?.msg_id}, chatId=${msg?.chat_id}, content=${msg?.content?.text}")
                if (msg != null) {
                    val messageItem = parseWsMessage(msg)
                    scope.launch {
                        _messageFlow.emit(WsEvent.NewMessage(messageItem))
                    }
                }
                return
            }

            // 尝试解析为编辑消息
            val editMessage = edit_message.ADAPTER.decode(data)
            Log.d(TAG, "Decoded edit_message, cmd: ${editMessage.info?.cmd}")
            if (editMessage.info?.cmd == "edit_message") {
                val msg = editMessage.data_?.msg
                Log.d(TAG, "Edit message: msgId=${msg?.msg_id}, chatId=${msg?.chat_id}")
                if (msg != null) {
                    val messageItem = parseEditMessage(msg)
                    scope.launch {
                        _messageFlow.emit(WsEvent.EditMessage(messageItem))
                    }
                }
                return
            }

            // 尝试解析为流式消息
            val streamMessage = stream_message.ADAPTER.decode(data)
            if (streamMessage.info?.cmd == "stream_message") {
                val msg = streamMessage.data_?.msg
                if (msg != null) {
                    scope.launch {
                        _messageFlow.emit(WsEvent.StreamContent(msg.msg_id, msg.content))
                    }
                }
                return
            }

            // 尝试解析为草稿同步
            val draftInput = draft_input.ADAPTER.decode(data)
            if (draftInput.info?.cmd == "draft_input") {
                val draft = draftInput.data_?.draft
                if (draft != null) {
                    scope.launch {
                        _messageFlow.emit(WsEvent.DraftUpdate(draft.chat_id, draft.input))
                    }
                }
                return
            }

            Log.d(TAG, "Unknown message type: ${pushMessage.info?.cmd}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message", e)
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
            direction = if (msg.sender?.chat_id == msg.recv_id) "right" else "left",
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
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        isConnected = false
        stopHeartbeat()
    }

    fun isConnected(): Boolean = isConnected
}
