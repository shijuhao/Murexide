package com.juhao.murexide.ui.theme

import androidx.compose.runtime.mutableStateOf

object ThemeState {
    var themeMode = mutableStateOf("system")
    var themeColor = mutableStateOf("DYNAMIC")
    var squareAvatar = mutableStateOf(false)
}