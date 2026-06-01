package com.juhao.mixue.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import com.juhao.mixue.ui.components.*
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.theme.ThemeState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()

    val themeMode by ThemeState.themeMode
    var squareAvatar by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        squareAvatar = settingsStorage.getSquareAvatar()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Lucide.ArrowLeft, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsGroup(title = "通用") {
                SettingsDropdownItem(
                    icon = Lucide.SunMoon,
                    title = "主题模式",
                    subtitle = when (themeMode) {
                        "system" -> "跟随系统"
                        "dark" -> "深色模式"
                        "light" -> "浅色模式"
                        else -> "跟随系统"
                    },
                    options = listOf(
                        "system" to "跟随系统",
                        "dark" to "深色模式",
                        "light" to "浅色模式"
                    ),
                    selectedValue = themeMode,
                    onOptionSelected = { selected ->
                        ThemeState.themeMode.value = selected
                        scope.launch {
                            settingsStorage.setThemeMode(selected)
                        }
                    }
                )
            }

            SettingsGroup(title = "外观") {
                SettingsSwitchItem(
                    icon = Lucide.User,
                    title = "圆角正方形头像",
                    subtitle = "将好友和群组头像显示为圆角正方形",
                    checked = squareAvatar,
                    onCheckedChange = { checked ->
                        squareAvatar = checked
                        scope.launch {
                            settingsStorage.setSquareAvatar(checked)
                        }
                    }
                )
            }

            SettingsGroup(title = "关于") {
                SettingsItemCell(
                    icon = Lucide.Info,
                    title = "关于",
                    subtitle = "版本号、开发者信息",
                    onClick = { /* TODO */ }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}