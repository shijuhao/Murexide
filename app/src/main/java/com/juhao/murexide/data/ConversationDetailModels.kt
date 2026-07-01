package com.juhao.murexide.data

/**
 * 会话详情统一数据模型（用户 / 群聊 / 机器人）
 */
data class ConversationDetail(
    val chatId: String,
    val chatType: Int,
    val name: String,
    val avatarUrl: String,
    val introduction: String = "",
    // 群聊
    val groupId: String? = null,
    val memberCount: Long? = null,
    val ownerId: String? = null,
    val groupCode: String? = null,
    val categoryName: String? = null,
    val myGroupNickname: String? = null,
    val isPrivate: Boolean = false,
    val doNotDisturb: Boolean = false,
    // 用户
    val nameId: Long? = null,
    val registerTime: String? = null,
    val onlineDay: Int? = null,
    val continuousOnlineDay: Int? = null,
    val ipGeo: String? = null,
    val isVip: Boolean = false,
    val gender: Int = 3,
    // 机器人
    val createBy: String? = null,
    val createTime: Long? = null,
    val usageCount: Long? = null,
    val isStop: Boolean = false
)

data class ConversationDetailUiState(
    val isLoading: Boolean = true,
    val detail: ConversationDetail? = null,
    val error: String? = null
)
