package com.juhao.murexide.data

import kotlinx.serialization.Serializable

/**
 * 聊天背景设置项。
 * @param chatId 对应的会话 ID，"all" 表示全局背景
 * @param imgUrl 背景图片直链
 */
@Serializable
data class ChatBackgroundItem(
    val id: Long = 0,
    val userId: String = "",
    val chatId: String = "",
    val imgUrl: String = "",
    val createTime: Long = 0,
    val updateTime: Long = 0
)

@Serializable
data class ChatBackgroundListResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: ChatBackgroundListData? = null
)

@Serializable
data class ChatBackgroundListData(
    val list: List<ChatBackgroundItem> = emptyList()
)
