package com.juhao.murexide.data

import kotlinx.serialization.Serializable

@Serializable
data class MessageItem(
    val msgId: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String,
    val senderType: Int = 1,
    val chatId: String,
    val recvId: String = "",
    val chatType: Int,
    val content: String = "",
    val contentType: Int,
    val timestamp: Long,
    val msgSeq: Long = 0,
    val direction: String,
    val isRecalled: Boolean = false,
    val isEdited: Boolean = false,
    val quoteMsgId: String? = null,
    val quoteMsgText: String? = null,
    val quoteImageUrl: String? = null,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val audioTime: Int? = null,
    val videoUrl: String? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val cmdName: String? = null,
    val cmdId: Long? = null,
    val cmdType: Int? = null,
    val tags: List<MessageTag> = emptyList()
) {
    val isMine: Boolean
        get() = direction == "right"

    fun getDisplayContent(): String {
        return when (contentType) {
            CONTENT_TYPE_IMAGE -> "[图片消息]"
            CONTENT_TYPE_FILE -> "[文件消息]"
            CONTENT_TYPE_STICKER -> "[表情消息]"
            CONTENT_TYPE_VIDEO -> "[视频消息]"
            CONTENT_TYPE_AUDIO -> "[语音消息]"
            CONTENT_TYPE_MARKDOWN -> "[Markdown消息]"
            CONTENT_TYPE_HTML -> "[HTML消息]"
            else -> content.takeIf { it.isNotEmpty() } ?: "[消息]"
        }
    }

    companion object {
        const val CONTENT_TYPE_TEXT = 1
        const val CONTENT_TYPE_IMAGE = 2
        const val CONTENT_TYPE_MARKDOWN = 3
        const val CONTENT_TYPE_FILE = 4
        const val CONTENT_TYPE_STICKER = 7
        const val CONTENT_TYPE_HTML = 8
        const val CONTENT_TYPE_VIDEO = 10
        const val CONTENT_TYPE_AUDIO = 11
    }
}

@Serializable
data class MessageTag(
    val id: Long,
    val text: String,
    val color: String
)

@Serializable
data class MessageContent(
    val text: String = "",
    val image: String? = null,
    val quoteMsgText: String? = null,
    val quoteImageUrl: String? = null,
    val quoteImageName: String? = null,
    val fileKey: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val audio: String? = null,
    val audioTime: Int? = null,
    val video: String? = null,
    val postType: String? = null,
    val expressionId: String? = null,
    val stickerItemId: Long? = null,
    val stickerPackId: Long? = null,
    val mentionedId: List<String> = emptyList()
)

data class ChatUiState(
    val messages: List<MessageItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val inputText: String = "",
    val replyTo: MessageItem? = null,
    val isMarkdown: Boolean = false,
    val hasMore: Boolean = true,
    val chatName: String = "",
    val chatAvatar: String = "",
    val isAdmin: Boolean = false,
    val memberCount: Long? = null,
    val isSending: Boolean = false,
    val isRemoteDraft: Boolean = false,
    val draftSource: String? = null,
    val backgroundUrl: String? = null
)
