package com.juhao.murexide.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.juhao.murexide.utils.UpdateInfo
import com.juhao.murexide.utils.checkForUpdateWithDetails
import com.juhao.murexide.utils.getAppVersionInfo
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
    val updateEnabled = context.getAppVersionInfo().commitHash != "dev"
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()
    
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    val themeMode by ThemeState.themeMode
    var squareAvatar by remember { mutableStateOf(false) }
    var avatarFollow by remember { mutableStateOf(false) }
    var showSticky by remember { mutableStateOf(true) }
    var updateChannel by remember { mutableStateOf("stable") }

    LaunchedEffect(Unit) {
        squareAvatar = settingsStorage.getSquareAvatar()
        avatarFollow = settingsStorage.getAvatarFollow()
        showSticky = settingsStorage.getShowSticky()
        updateChannel = settingsStorage.getUpdateChannel()
    }
    
    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            currentVersion = context.getAppVersionInfo().versionName,
            onDismiss = { showUpdateDialog = false },
            onConfirm = {
                val intent = Intent(Intent.ACTION_VIEW, updateInfo?.releaseUrl?.toUri())
                context.startActivity(intent)
                showUpdateDialog = false
            }
        )
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
                    icon = Icons.Rounded.ChatBubbleOutline,
                    title = "显示置顶会话",
                    subtitle = "在主页显示置顶会话",
                    checked = showSticky,
                    onCheckedChange = { checked ->
                        showSticky = checked
                        scope.launch {
                            settingsStorage.setShowSticky(checked)
                        }
                    }
                )
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
            
            SettingsGroup(title = "行为") {
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
            
            SettingsGroup(title = "更新") {
                SettingsItem(
                    icon = Icons.Rounded.Update,
                    title = "检查更新",
                    isEnabled = updateEnabled,
                    subtitle = if (updateEnabled)
                        "访问仓库获取最新版本"
                    else
                        "Dev版本无法检查更新",
                    onClick = {
                        scope.launch {
                            val includePreRelease = updateChannel == "preRelease"
                            
                            val info = checkForUpdateWithDetails(
                                context = context,
                                includePreRelease = includePreRelease
                            )
                            if (info != null) {
                                updateInfo = info
                                showUpdateDialog = true
                            } else {
                                Toast.makeText(
                                    context,
                                    "已是最新版本",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
                SettingsDropdownItem(
                    icon = Icons.AutoMirrored.Rounded.List,
                    title = "更新频道",
                    subtitle = if (updateChannel == "stable")
                        "仅检查正式版本"
                    else
                        "检查预发布版本",
                    options = listOf(
                        "stable" to "仅正式版",
                        "preRelease" to "正式版 + 预发布版"
                    ),
                    selectedValue = updateChannel,
                    onOptionSelected = { selected ->
                        updateChannel = selected
                        scope.launch {
                            settingsStorage.setUpdateChannel(selected)
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

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val isPreRelease = updateInfo.isPreRelease
    val versionType = if (isPreRelease) "预发布版" else "正式版"
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isPreRelease) {
                    "发现新预发布版"
                } else {
                    "发现新正式版"
                }
            )
        },
        text = {
            Column {
                Text(
                    text = "$currentVersion  →  ${updateInfo.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "版本类型：$versionType",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("前往下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        }
    )
}