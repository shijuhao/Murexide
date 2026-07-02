@file:Suppress("AssignedValueIsNeverRead")

package com.juhao.murexide.ui.settings.appearance

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.theme.ThemeState
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.ui.chat.components.MessageBubble
import kotlinx.coroutines.launch
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollState = rememberScrollState()
        
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()

    val themeMode by ThemeState.themeMode
    val themeColor by ThemeState.themeColor

    var squareAvatar by remember { mutableStateOf(false) }
    var showSticky by remember { mutableStateOf(true) }
    
    var bubbleCornerRadius by remember { mutableFloatStateOf(16f) }
    val showMyBubbleAvatar by settingsStorage.showMyBubbleAvatarFlow.collectAsState(initial = true)
    var bubbleOpacity by remember { mutableFloatStateOf(0.9f) }

    LaunchedEffect(Unit) {
        squareAvatar = settingsStorage.getSquareAvatar()
        showSticky = settingsStorage.getShowSticky()
        bubbleCornerRadius = settingsStorage.getBubbleCornerRadius()
        bubbleOpacity = settingsStorage.getBubbleOpacity()
    }

    val previewMessages = remember {
        listOf(
            MessageItem(
                msgId = "preview_other",
                senderId = "other",
                senderName = "小明",
                senderAvatar = "https://chat-img.jwznb.com/defalut-avatars/Hedy%20Lamarr.png",
                content = "你好！",
                contentType = MessageItem.CONTENT_TYPE_TEXT,
                timestamp = System.currentTimeMillis() - 60000,
                direction = "left"
            ),
            MessageItem(
                msgId = "preview_other",
                senderId = "other",
                senderName = "小明",
                senderAvatar = "https://chat-img.jwznb.com/defalut-avatars/Hedy%20Lamarr.png",
                content = "看看这个气泡效果怎么样？",
                contentType = MessageItem.CONTENT_TYPE_TEXT,
                timestamp = System.currentTimeMillis() - 60000,
                direction = "left"
            ),
            MessageItem(
                msgId = "preview_me",
                senderId = "me",
                senderName = "我",
                senderAvatar = "https://chat-img.jwznb.com/defalut-avatars/Mary%20Roebling.png",
                content = "效果不错！可以调整圆角和透明度",
                contentType = MessageItem.CONTENT_TYPE_TEXT,
                timestamp = System.currentTimeMillis(),
                direction = "right"
            )
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("外观设置") },
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
                .verticalScroll(scrollState)
        ) {
            // 主题设置
            SettingsGroup(title = "主题") {
                SettingsDropdownItem(
                    icon = Icons.Rounded.WbSunny,
                    title = "主题模式",
                    subtitle = when (themeMode) {
                        "dark" -> "深色模式"
                        "light" -> "浅色模式"
                        "oled" -> "纯黑模式"
                        else -> "跟随系统"
                    },
                    options = listOf(
                        "system" to "跟随系统",
                        "light" to "浅色模式",
                        "dark" to "深色模式",
                        "oled" to "纯黑模式"
                    ),
                    selectedValue = themeMode,
                    onOptionSelected = { selected ->
                        ThemeState.themeMode.value = selected
                        scope.launch {
                            settingsStorage.setThemeMode(selected)
                        }
                    }
                )
                SettingsDropdownItem(
                    icon = Icons.Rounded.Draw,
                    title = "主题颜色",
                    subtitle = when (themeColor) {
                        "PURPLE" -> "紫色"
                        "BLUE" -> "蓝色"
                        "GREEN" -> "绿色"
                        "ORANGE" -> "橙色"
                        else -> "动态取色"
                    },
                    options = listOf(
                        "DYNAMIC" to "动态取色",
                        "PURPLE" to "紫色",
                        "BLUE" to "蓝色",
                        "GREEN" to "绿色",
                        "ORANGE" to "橙色"
                    ),
                    selectedValue = themeMode,
                    onOptionSelected = { selected ->
                        ThemeState.themeColor.value = selected
                        scope.launch {
                            settingsStorage.setThemeColor(selected)
                        }
                    }
                )
            }
            
            // 气泡预览区域
            SettingsGroup(title = "消息气泡预览") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MessageBubble(
                            message = previewMessages[0],
                            isLastFromSender = true,
                            isFirstFromSender = false,
                            isOlderSameSender = false,
                            isNewerSameSender = true,
                            showAvatar = false,
                            showMyBubbleAvatarSetting = showMyBubbleAvatar,
                            bubbleOpacity = bubbleOpacity,
                            bubbleCornerRadius = bubbleCornerRadius
                        )
                        
                        MessageBubble(
                            message = previewMessages[1],
                            isLastFromSender = false,
                            isFirstFromSender = true,
                            isOlderSameSender = true,
                            isNewerSameSender = false,
                            showAvatar = true,
                            showMyBubbleAvatarSetting = showMyBubbleAvatar,
                            bubbleOpacity = bubbleOpacity,
                            bubbleCornerRadius = bubbleCornerRadius
                        )
                        
                        MessageBubble(
                            message = previewMessages[2],
                            isLastFromSender = true,
                            isFirstFromSender = true,
                            isOlderSameSender = false,
                            isNewerSameSender = false,
                            showAvatar = showMyBubbleAvatar,
                            showMyBubbleAvatarSetting = showMyBubbleAvatar,
                            bubbleOpacity = bubbleOpacity,
                            bubbleCornerRadius = bubbleCornerRadius
                        )
                    }
                }
            }

            // 气泡样式设置
            SettingsGroup(title = "气泡样式") {
                // 圆角 Slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.RoundedCorner,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "气泡圆角",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${bubbleCornerRadius.toInt()}dp",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Slider(
                        value = bubbleCornerRadius,
                        onValueChange = { bubbleCornerRadius = it },
                        onValueChangeFinished = {
                            scope.launch {
                                settingsStorage.setBubbleCornerRadius(bubbleCornerRadius)
                            }
                        },
                        valueRange = 0f..24f,
                        steps = 23,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "0dp",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "24dp",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 透明度 Slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Opacity,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "气泡透明度",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${(bubbleOpacity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Slider(
                        value = bubbleOpacity,
                        onValueChange = { bubbleOpacity = it },
                        onValueChangeFinished = {
                            scope.launch {
                                settingsStorage.setBubbleOpacity(bubbleOpacity)
                            }
                        },
                        valueRange = 0.4f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "40%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "100%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 显示头像开关
                SettingsSwitchItem(
                    icon = Icons.Rounded.Face,
                    title = "显示我的头像",
                    subtitle = "在我发送的消息气泡旁显示我的头像",
                    checked = showMyBubbleAvatar,
                    onCheckedChange = { checked ->
                        scope.launch {
                            settingsStorage.setShowMyBubbleAvatar(checked)
                        }
                    }
                )
            }

            // 会话设置
            SettingsGroup(title = "会话") {
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}