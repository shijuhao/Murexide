package com.juhao.murexide.repository

import com.juhao.murexide.data.*
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.list_message
import com.juhao.murexide.proto.list_message_send
import com.juhao.murexide.proto.send_message_send
import com.juhao.murexide.proto.send_message
import com.juhao.murexide.proto.edit_message_send
import com.juhao.murexide.proto.edit_message
import com.juhao.murexide.proto.list_message_by_mid_seq_send
import com.juhao.murexide.proto.recall_msg_send
import com.juhao.murexide.proto.recall_msg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class MessageRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL

    suspend fun getMessageList(
        token: String,
        chatId: String,
        chatType: Int,
        msgId: String? = null,
        msgSeq: Long? = null,
        msgCount: Int = 20
    ): Result<List<MessageItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = list_message_send(
                    msg_count = msgCount.toLong(),
                    msg_id = msgId ?: "",
                    chat_type = chatType.toLong(),
                    chat_id = chatId
                ).encode().toRequestBody("application/octet-stream".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/msg/list-message")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.bytes()
                        val messageList = list_message.ADAPTER.decode(responseBody)

                        if (messageList.status?.code == 1) {
                            val messages = messageList.msg.map { msg ->
                                MessageItem(
                                    msgId = msg.msg_id,
                                    senderId = msg.sender?.chat_id ?: "",
                                    senderName = msg.sender?.name ?: "",
                                    senderAvatar = msg.sender?.avatar_url ?: "",
                                    chatId = chatId,
                                    chatType = chatType,
                                    content = msg.content?.text ?: "",
                                    contentType = msg.content_type,
                                    timestamp = msg.send_time,
                                    msgSeq = msg.msg_seq,
                                    direction = msg.direction,
                                    isRecalled = msg.msg_delete_time > 0,
                                    isEdited = msg.edit_time > 0,
                                    quoteMsgId = msg.quote_msg_id.takeIf { it.isNotEmpty() },
                                    quoteMsgText = msg.content?.quote_msg_text?.takeIf { it.isNotEmpty() },
                                    quoteImageUrl = msg.content?.quote_image_url?.takeIf { it.isNotEmpty() },
                                    images = listOfNotNull(
                                        msg.content?.image_url?.takeIf { it.isNotEmpty() },
                                        msg.content?.video_url?.takeIf { it.isNotEmpty() },
                                        msg.content?.audio_url?.takeIf { it.isNotEmpty() }
                                    ).filter { it.contains("image") || it.contains("jpg") || it.contains("png") || it.contains("jpeg") },
                                    audioUrl = msg.content?.audio_url?.takeIf { it.isNotEmpty() },
                                    audioTime = if ((msg.content?.audio_time ?: 0) > 0) msg.content?.audio_time?.toInt() else null,
                                    videoUrl = msg.content?.video_url?.takeIf { it.isNotEmpty() },
                                    fileUrl = msg.content?.file_url?.takeIf { it.isNotEmpty() },
                                    fileName = msg.content?.file_name?.takeIf { it.isNotEmpty() },
                                    fileSize = if ((msg.content?.file_size ?: 0) > 0) msg.content?.file_size else null,
                                    cmdName = msg.cmd?.name?.takeIf { it.isNotEmpty() },
                                    cmdId = msg.cmd?.type?.toLong(),
                                    cmdType = msg.cmd?.type,
                                    tags = msg.sender?.tag?.map { tag ->
                                        MessageTag(
                                            id = tag.id,
                                            text = tag.text,
                                            color = tag.color
                                        )
                                    } ?: emptyList()
                                )
                            }
                            Result.success(messages)
                        } else {
                            Result.failure(Exception(messageList.status?.msg ?: "获取消息失败"))
                        }
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendMessage(
        token: String,
        chatId: String,
        chatType: Int,
        content: MessageContent,
        contentType: Int,
        quoteMsgId: String? = null
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val msgId = UUID.randomUUID().toString().replace("-", "")

                // 构建 ProtoBuf 请求
                val contentProto = send_message_send.Content(
                    text = content.text.takeIf { it.isNotEmpty() } ?: "",
                    image = content.image ?: "",
                    quote_msg_text = content.quoteMsgText ?: "",
                    quote_image_url = content.quoteImageUrl ?: "",
                    quote_image_name = content.quoteImageName ?: "",
                    file_name = content.fileName ?: "",
                    file_size = content.fileSize ?: 0L,
                    audio = content.audio ?: "",
                    audio_time = content.audioTime?.toLong() ?: 0L,
                    video = content.video ?: "",
                    post_type = content.postType ?: "",
                    expression_id = content.expressionId ?: "",
                    sticker_item_id = content.stickerItemId ?: 0L,
                    sticker_pack_id = content.stickerPackId ?: 0L,
                    mentioned_id = content.mentionedId
                )

                val requestProto = send_message_send(
                    msg_id = msgId,
                    chat_id = chatId,
                    chat_type = chatType.toLong(),
                    content = contentProto,
                    content_type = contentType.toLong(),
                    quote_msg_id = quoteMsgId ?: ""
                )
                val requestBody = requestProto.encode().toRequestBody("application/octet-stream".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/msg/send-message")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.bytes()
                        val sendResult = send_message.ADAPTER.decode(responseBody)

                        if (sendResult.status?.code == 1) {
                            Result.success(true)
                        } else {
                            Result.failure(Exception(sendResult.status?.msg ?: "发送失败"))
                        }
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun editMessage(
        token: String,
        msgId: String,
        chatId: String,
        chatType: Int,
        content: MessageContent,
        contentType: Int,
        quoteMsgId: String? = null
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // 构建 ProtoBuf 请求
                val contentProto = edit_message_send.Content(
                    text = content.text.takeIf { it.isNotEmpty() } ?: "",
                    quote_msg_text = content.quoteMsgText ?: ""
                )

                val requestProto = edit_message_send(
                    msg_id = msgId,
                    chat_id = chatId,
                    chat_type = chatType,
                    content = contentProto,
                    content_type = contentType.toLong(),
                    quote_msg_id = quoteMsgId ?: ""
                )
                val requestBody = requestProto.encode().toRequestBody("application/octet-stream".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/msg/edit-message")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.bytes()
                        val editResult = edit_message.ADAPTER.decode(responseBody)

                        if (editResult.status?.code == 1) {
                            Result.success(true)
                        } else {
                            Result.failure(Exception(editResult.status?.msg ?: "编辑失败"))
                        }
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun recallMessage(
        token: String,
        msgId: String,
        chatId: String,
        chatType: Int
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // 构建 ProtoBuf 请求
                val requestProto = recall_msg_send(
                    msg_id = msgId,
                    chat_id = chatId,
                    chat_type = chatType.toLong()
                )
                val requestBody = requestProto.encode().toRequestBody("application/octet-stream".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/msg/recall-msg")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.bytes()
                        val recallResult = recall_msg.ADAPTER.decode(responseBody)

                        if (recallResult.status?.code == 1) {
                            Result.success(true)
                        } else {
                            Result.failure(Exception(recallResult.status?.msg ?: "撤回失败"))
                        }
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // TODO: 等待 Wire 生成 chat_ws_go_proto 后启用
    /*
    fun parseWebSocketMessage(data: ByteArray): MessageItem? {
        return try {
            val wsMessage = chat_ws_go_proto.PushMessage.ADAPTER.decode(data)

            val msg = wsMessage.data?.msg ?: return null
            MessageItem(
                msgId = msg.msg_id,
                senderId = msg.sender?.chat_id ?: "",
                senderName = msg.sender?.name ?: "",
                senderAvatar = msg.sender?.avatar_url ?: "",
                chatId = msg.chat_id,
                chatType = msg.chat_type.toInt(),
                content = msg.content?.text ?: "",
                contentType = msg.content_type.toInt(),
                timestamp = msg.timestamp,
                direction = if (msg.sender?.chat_id == msg.recv_id) "right" else "left",
                isRecalled = msg.delete_timestamp > 0,
                quoteMsgId = msg.quote_msg_id.takeIf { it.isNotEmpty() } ?: "",
                quoteMsgText = msg.content?.quote_msg_text?.takeIf { it.isNotEmpty() },
                quoteImageUrl = msg.content?.quote_image_url?.takeIf { it.isNotEmpty() },
                images = listOfNotNull(
                    msg.content?.image_url?.takeIf { it.isNotEmpty() },
                    msg.content?.video_url?.takeIf { it.isNotEmpty() },
                    msg.content?.audio_url?.takeIf { it.isNotEmpty() }
                ),
                audioUrl = msg.content?.audio_url?.takeIf { it.isNotEmpty() },
                audioTime = if ((msg.content?.audio_time ?: 0) > 0) msg.content?.audio_time?.toInt() else null,
                videoUrl = msg.content?.video_url?.takeIf { it.isNotEmpty() },
                fileUrl = msg.content?.file_url?.takeIf { it.isNotEmpty() },
                fileName = msg.content?.file_name?.takeIf { it.isNotEmpty() },
                fileSize = if ((msg.content?.file_size ?: 0) > 0) msg.content?.file_size else null,
                cmdName = msg.cmd?.name?.takeIf { it.isNotEmpty() },
                cmdId = msg.cmd?.id,
                cmdType = msg.cmd?.type?.toInt(),
                tags = msg.sender?.tag?.map { tag ->
                    MessageTag(
                        id = tag.id,
                        text = tag.text,
                        color = tag.color
                    )
                } ?: emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }
    */
}
