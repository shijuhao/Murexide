package com.juhao.murexide.repository

import com.juhao.murexide.data.CaptchaResponse
import com.juhao.murexide.data.LoginRequest
import com.juhao.murexide.data.LoginResponse
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.user.info
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AuthRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun login(email: String, password: String, deviceId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val loginRequest = LoginRequest(
                    email = email,
                    password = password,
                    deviceId = deviceId,
                    platform = "android"
                )
                
                val jsonBody = json.encodeToString(LoginRequest.serializer(), loginRequest)
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$baseUrl/v1/user/email-login")
                    .post(requestBody)
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val loginResponse = json.decodeFromString(LoginResponse.serializer(),
                            responseBody
                        )
                        
                        if (loginResponse.code == 1 && loginResponse.data != null) {
                            Result.success(loginResponse.data.token)
                        } else {
                            Result.failure(Exception(loginResponse.msg))
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

    suspend fun getCaptcha(): Result<CaptchaResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/v1/user/captcha")
                    .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val captchaResponse = json.decodeFromString(CaptchaResponse.serializer(),
                            responseBody
                        )
                        Result.success(captchaResponse)
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getUserInfo(token: String): Result<UserInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/v1/user/info")
                    .get()
                    .header("token", token)
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.bytes()
                        val userInfo = info.ADAPTER.decode(responseBody)
                        
                        if (userInfo.status?.code == 1 && userInfo.data_ != null) {
                            val data = userInfo.data_
                            Result.success(
                                UserInfo(
                                    id = data.id,
                                    name = data.name,
                                    avatarUrl = data.avatar_url,
                                    phone = data.phone,
                                    email = data.email,
                                    coin = data.coin,
                                    isVip = data.is_vip == 1,
                                    invitationCode = data.invitation_code
                                )
                            )
                        } else {
                            Result.failure(Exception(userInfo.status?.msg ?: "获取用户信息失败"))
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

data class UserInfo(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val phone: String,
    val email: String,
    val coin: Double,
    val isVip: Boolean,
    val invitationCode: String
)
