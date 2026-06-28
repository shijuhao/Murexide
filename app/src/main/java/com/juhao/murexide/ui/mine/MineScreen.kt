package com.juhao.murexide.ui.mine

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.repository.UserInfo
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.CustomItemCell
import com.juhao.murexide.ui.components.SettingsGroup
import com.juhao.murexide.ui.components.SettingsItemCell

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(
    token: String,
    onSettingsClick: () -> Unit,
    viewModel: MineViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return MineViewModel(token) as T
            }
        }
    )
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Rounded.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) {
        when (val state = uiState) {
            is MineUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = it.calculateTopPadding(),
                            end = it.calculateRightPadding(LayoutDirection.Ltr)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is MineUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = it.calculateTopPadding(),
                            end = it.calculateRightPadding(LayoutDirection.Ltr)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("加载失败: ${state.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadUserInfo() }) {
                            Text("重试")
                        }
                    }
                }
            }
            is MineUiState.Success -> {
                MineContent(
                    userInfo = state.userInfo,
                    onlineDay = state.onlineDay,
                    continuousOnlineDay = state.continuousOnlineDay,
                    scrollState = scrollState,
                    paddingValues = it,
                    introduction = state.introduction
                )
            }
        }
    }
}

@Composable
private fun MineContent(
    userInfo: UserInfo,
    onlineDay: Int?,
    continuousOnlineDay: Int?,
    introduction: String,
    scrollState: ScrollState,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = paddingValues.calculateTopPadding(),
                end = paddingValues.calculateRightPadding(LayoutDirection.Ltr)
            )
            .verticalScroll(scrollState)
    ) {
        SettingsGroup {
            CustomItemCell {
                Avatar(url = userInfo.avatarUrl, size = 64.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userInfo.name.ifEmpty { "未知" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ID: ${userInfo.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (introduction.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = introduction,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        SettingsGroup(title = "账号信息") {
            InfoItem(
                Icons.Rounded.Verified,
                "用户等级",
                if (userInfo.isVip) "会员" else "普通用户"
            )
            InfoItem(Icons.Rounded.MonetizationOn, "金币", "${userInfo.coin}")
            InfoItem(Icons.Rounded.Email, "邮箱", userInfo.email.ifEmpty { "未设置" })
            InfoItem(Icons.Rounded.Phone, "手机号", userInfo.phone.ifEmpty { "未绑定" })
            InfoItem(Icons.Rounded.CardGiftcard, "邀请码", userInfo.invitationCode.ifEmpty { "未设置" })
        }

        SettingsGroup(title = "活跃度") {
            onlineDay?.let {
                InfoItem(Icons.Rounded.AccessTime, "在线天数", "$it 天")
            }
            continuousOnlineDay?.let {
                InfoItem(Icons.Rounded.LocalFireDepartment, "连续在线", "$it 天")
            }
            if (onlineDay == null && continuousOnlineDay == null) {
                InfoItem(Icons.Rounded.Schedule, "在线天数", "加载中…")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun InfoItem(
    icon: ImageVector,
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