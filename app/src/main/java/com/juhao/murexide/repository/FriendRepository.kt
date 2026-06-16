package com.juhao.murexide.repository

import com.juhao.murexide.data.ContactGroup
import com.juhao.murexide.data.ContactItem
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.friend.address_book_list
import com.juhao.murexide.proto.friend.address_book_list_send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class FriendRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL

    suspend fun getAddressBook(token: String, md5: String = ""): Result<List<ContactGroup>> {
        return withContext(Dispatchers.IO) {
            try {
                // 构建 ProtoBuf 请求
                val requestProto = address_book_list_send(md5 = md5)
                val requestBody = requestProto.encode().toRequestBody("application/octet-stream".toMediaType())
                
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/friend/address-book-list")
                    .post(requestBody)
                    .header("token", token)
                    .build()
                
                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.bytes()
                        val bookList = address_book_list.ADAPTER.decode(responseBody)
                        
                        if (bookList.status?.code == 1) {
                            val groups = bookList.data_.map { groupData ->
                                ContactGroup(
                                    groupName = groupData.list_name,
                                    chatType = groupData.chat_type,
                                    contacts = groupData.data_.map { item ->
                                        ContactItem(
                                            chatId = item.chat_id,
                                            chatType = groupData.chat_type,
                                            remark = item.remark.takeIf { it.isNotEmpty() },
                                            avatarUrl = item.avatar_url,
                                            permissionLevel = item.permisson_level,
                                            noDisturb = item.noDisturb,
                                            name = item.name
                                        )
                                    }
                                )
                            }
                            Result.success(groups)
                        } else {
                            Result.failure(Exception(bookList.status?.msg ?: "请求失败"))
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
}
