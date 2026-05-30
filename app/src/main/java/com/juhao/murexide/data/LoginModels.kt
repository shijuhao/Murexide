package com.juhao.murexide.data

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val deviceId: String,
    val platform: String = "android"
)

@Serializable
data class LoginResponse(
    val code: Int,
    val data: LoginData? = null,
    val msg: String
)

@Serializable
data class LoginData(
    val token: String
)

@Serializable
data class CaptchaResponse(
    val code: Int,
    val data: CaptchaData? = null,
    val msg: String
)

@Serializable
data class CaptchaData(
    val b64s: String,
    val id: String
)
