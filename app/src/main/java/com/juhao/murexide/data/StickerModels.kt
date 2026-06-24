package com.juhao.murexide.data

import kotlinx.serialization.Serializable

// ---------- 表情包 (sticker/list) ----------

@Serializable
data class StickerItem(
    val id: Long = 0,
    val name: String = "",
    val url: String = "",
    val stickerPackId: Long = 0,
    val createBy: String = "",
    val createTime: Long = 0,
    val delFlag: Int = 0
)

@Serializable
data class StickerPack(
    val id: Long = 0,
    val name: String = "",
    val createBy: String = "",
    val createTime: Long = 0,
    val delFlag: Int = 0,
    val userCount: Long = 0,
    val hot: Long = 0,
    val uuid: String = "",
    val updateTime: Long = 0,
    val sort: Long = 0,
    val stickerItems: List<StickerItem> = emptyList()
)

@Serializable
data class StickerListResponse(
    val code: Int = 0,
    val data: StickerListData? = null,
    val msg: String = ""
)

@Serializable
data class StickerListData(
    val stickerPacks: List<StickerPack> = emptyList()
)

// ---------- 个人收藏表情 (expression/list) ----------

@Serializable
data class ExpressionItem(
    val id: Long = 0,
    val url: String = "",
    val urlOriginal: String = "",
    val delFlag: Int = 0,
    val createTime: Long = 0,
    val createBy: String = ""
)

@Serializable
data class ExpressionListResponse(
    val code: Int = 0,
    val data: ExpressionListData? = null,
    val msg: String = ""
)

@Serializable
data class ExpressionListData(
    val expression: List<ExpressionItem> = emptyList()
)
