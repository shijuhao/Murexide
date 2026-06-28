package com.juhao.murexide.ui.conversationdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juhao.murexide.data.ConversationDetail
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.CustomItemCell
import com.juhao.murexide.ui.components.SettingsGroup
import com.juhao.murexide.ui.components.SettingsItemCell
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationDetailScreen(
    viewModel: ConversationDetailViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("会话详情") },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val detail = uiState.detail) {
                null if uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                null -> {
                    ErrorState(
                        message = uiState.error ?: "加载失败",
                        onRetry = viewModel::loadDetail,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        DetailHeader(detail)

                        when (detail.chatType) {
                            2 -> GroupSection(detail)
                            3 -> BotSection(detail)
                            else -> UserSection(detail)
                        }

                        if (uiState.error != null) {
                            Text(
                                text = uiState.error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(detail: ConversationDetail) {
    SettingsGroup {
        CustomItemCell {
            Avatar(url = detail.avatarUrl, size = 64.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.name.ifEmpty { "未知" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = when (detail.chatType) {
                    2 -> "群聊"
                    3 -> "机器人"
                    else -> detail.nameId?.let { "ID: $it" } ?: "用户"
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (detail.introduction.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = detail.introduction,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun UserSection(detail: ConversationDetail) {
    SettingsGroup(title = "用户信息") {
        if (detail.isVip) {
            InfoItem(Icons.Rounded.WorkspacePremium, "会员", "VIP 用户")
        }
        detail.registerTime?.let {
            InfoItem(Icons.Rounded.Event, "注册时间", it)
        }
        detail.onlineDay?.let {
            InfoItem(Icons.Rounded.AccessTime, "在线天数", "$it 天")
        }
        detail.continuousOnlineDay?.let {
            InfoItem(Icons.Rounded.LocalFireDepartment, "连续在线", "$it 天")
        }
        InfoItem(
            when (detail.gender) {
                1 -> Icons.Rounded.Boy
                2 -> Icons.Rounded.Girl
                else -> Icons.Rounded.Person
            },
            "性别",
            when (detail.gender) {
                1 -> "男"
                2 -> "女"
                else -> "其他"
            }
        )
        detail.ipGeo?.let {
            InfoItem(Icons.Rounded.LocationOn, "IP 属地", it)
        }
    }
}

@Composable
private fun GroupSection(detail: ConversationDetail) {
    SettingsGroup(title = "群聊信息") {
        detail.memberCount?.let {
            InfoItem(Icons.Rounded.Group, "成员数量", "$it 人")
        }
        detail.groupCode?.let {
            InfoItem(Icons.Rounded.Tag, "群号", it)
        }
        detail.categoryName?.let {
            InfoItem(Icons.Rounded.Category, "分类", it)
        }
        detail.ownerId?.let {
            InfoItem(Icons.Rounded.Star, "群主", it)
        }
        detail.myGroupNickname?.let {
            InfoItem(Icons.Rounded.Person, "我的群昵称", it)
        }
        InfoItem(
            Icons.Rounded.Lock,
            "加群方式",
            if (detail.isPrivate) "需要验证" else "允许任何人加入"
        )
        InfoItem(
            Icons.Rounded.Notifications,
            "消息免打扰",
            if (detail.doNotDisturb) "已开启" else "已关闭"
        )
    }
}

@Composable
private fun BotSection(detail: ConversationDetail) {
    SettingsGroup(title = "机器人信息") {
        detail.usageCount?.let {
            InfoItem(Icons.Rounded.Group, "使用人数", "$it 人")
        }
        detail.createBy?.let {
            InfoItem(Icons.Rounded.Person, "创建者", it)
        }
        detail.createTime?.let {
            InfoItem(Icons.Rounded.Event, "创建时间", formatEpoch(it))
        }
        InfoItem(
            Icons.Rounded.Visibility,
            "可见性",
            if (detail.isPrivate) "私有" else "公开"
        )
        InfoItem(
            Icons.Rounded.PowerSettingsNew,
            "运行状态",
            if (detail.isStop) "已停用" else "运行中"
        )
        InfoItem(
            Icons.Rounded.Notifications,
            "消息免打扰",
            if (detail.doNotDisturb) "已开启" else "已关闭"
        )
    }
}

@Composable
private fun InfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    SettingsItemCell(
        icon = icon,
        title = title,
        subtitle = value,
        onClick = { /* 仅展示 */ }
    )
}

private fun formatEpoch(epoch: Long): String {
    // 兼容秒 / 毫秒时间戳
    val millis = if (epoch < 100_000_000_000L) epoch * 1000 else epoch
    return SimpleDateFormat("yyyy/M/d HH:mm", Locale.getDefault()).format(Date(millis))
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}
