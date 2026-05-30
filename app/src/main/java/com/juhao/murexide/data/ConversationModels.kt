package com.juhao.murexide.data

import kotlinx.serialization.Serializable

@Serializable
data class ConversationListRequest(
    val md5: String = ""
)

@Serializable
data class ConversationListResponse(
    val code: Int,
    val data: List<ConversationItem>? = null,
    val msg: String,
    val total: Int = 0,
    val md5: String = ""
)

@Serializable
data class ConversationItem(
    val chatId: String,
    val chatType: Int,
    val name: String,
    val remark: String? = null,
    val chatContent: String,
    val timestampMs: Long,
    val unreadMessage: Int = 0,
    val at: Int = 0,
    val avatarUrl: String,
    val doNotDisturb: Int = 0,
    val certificationLevel: Int = 0
) {
    val displayName: String
        get() = remark?.takeIf { it.isNotBlank() } ?: name
    
    val hasUnread: Boolean
        get() = unreadMessage > 0
    
    val isAtMentioned: Boolean
        get() = at > 0
}
