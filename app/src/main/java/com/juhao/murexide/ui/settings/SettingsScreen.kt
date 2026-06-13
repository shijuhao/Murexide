package com.juhao.murexide.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.theme.ThemeState
import kotlinx.coroutines.launch
import androidx.compose.ui.input.nestedscroll.nestedScroll
import android.content.Intent
import com.juhao.murexide.ui.about.AboutActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollState = rememberScrollState()
        
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()

    val themeMode by ThemeState.themeMode
    var squareAvatar by remember { mutableStateOf(false) }
    var avatarFollow by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        squareAvatar = settingsStorage.getSquareAvatar()
        avatarFollow = settingsStorage.getAvatarFollow()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp)
                .verticalScroll(scrollState)
        ) {
            SettingsGroup(title = "通用") {
                SettingsDropdownItem(
                    icon = Icons.Rounded.WbSunny,
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
                    icon = Icons.Rounded.People,
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
            
            SettingsGroup(title = "实验性") {
                SettingsSwitchItem(
                    icon = Icons.Rounded.Animation,
                    title = "聊天页头像跟随",
                    subtitle = "头像跟随视角移动",
                    checked = avatarFollow,
                    onCheckedChange = { checked ->
                        avatarFollow = checked
                        scope.launch {
                            settingsStorage.setAvatarFollow(checked)
                        }
                    }
                )
            }

            SettingsGroup(title = "关于") {
                SettingsItem(
                    icon = Icons.Rounded.Info,
                    title = "关于",
                    subtitle = "版本号、开发者信息",
                    onClick = {
                        val intent = Intent(context, AboutActivity::class.java)
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}