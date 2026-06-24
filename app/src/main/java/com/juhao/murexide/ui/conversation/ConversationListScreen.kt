package com.juhao.murexide.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.juhao.murexide.R
import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.components.Avatar
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    modifier: Modifier = Modifier,
    token: String,
    onConversationClick: (ConversationItem) -> Unit,
    currentConversation: ConversationItem? = null,
    bigScreenMode: Boolean = false,
    viewModel: ConversationViewModel = remember { ConversationViewModel(token) }
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val uiState by viewModel.uiState.collectAsState()
    val isWsConnected by viewModel.isWsConnected.collectAsState()

    val settingsStorage = remember { SettingsStorage(context) }
    var showSticky by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showSticky = settingsStorage.getShowSticky()
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.app_name),
                            maxLines = 1
                        )
                        if (!isWsConnected) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.error
                            ) {}
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Rounded.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Rounded.Add, contentDescription = "添加")
                    }
                }
            )
        }
    ) {
        PullToRefreshBox(
            isRefreshing = uiState is ConversationUiState.Loading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = it.calculateTopPadding(),
                )
                .then(
                    if (!bigScreenMode) Modifier.padding(end = it.calculateEndPadding(LayoutDirection.Ltr))
                    else Modifier.padding(bottom = it.calculateBottomPadding())
                ),
        ) {
            val state = uiState
            if (state is ConversationUiState.Success) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (showSticky && state.stickyConversations.isNotEmpty()) {
                        item {
                            StickyConversationSection(
                                stickyItems = state.stickyConversations,
                                onStickyClick = { sticky ->
                                    onConversationClick(
                                        ConversationItem(
                                            chatId = sticky.chatId,
                                            chatType = sticky.chatType,
                                            name = sticky.chatName,
                                            chatContent = "",
                                            timestampMs = 0,
                                            avatarUrl = sticky.avatarUrl
                                        )
                                    )
                                }
                            )
                        }
                    }

                    if (state.conversations.isEmpty() && state.stickyConversations.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("暂无会话")
                            }
                        }
                    } else {
                        items(
                            items = state.conversations,
                            key = { item -> item.chatId }
                        ) { conversation ->
                            ConversationItem(
                                conversation = conversation,
                                isSelected = currentConversation?.chatId == conversation.chatId,
                                onClick = {
                                    viewModel.clearUnread(conversation.chatId)
                                    onConversationClick(conversation)
                                }
                            )
                        }
                    }
                }
            } else if (state is ConversationUiState.Error) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("加载失败: ${state.message}")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("重试")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StickyConversationSection(
    stickyItems: List<StickyItem>,
    onStickyClick: (StickyItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            stickyItems.forEach { item ->
                StickyItemView(
                    item = item,
                    onClick = { onStickyClick(item) }
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun StickyItemView(
    item: StickyItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Avatar(
            url = item.avatarUrl,
            size = 42.dp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.chatName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ConversationItem(
    conversation: ConversationItem,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(conversation.avatarUrl, 52.dp)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formatTime(conversation.timestampMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.chatContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (conversation.doNotDisturb == 1) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }

                if (conversation.hasUnread || conversation.isAtMentioned) {
                    Spacer(modifier = Modifier.width(8.dp))

                    Badges(
                        doNotDisturb = conversation.doNotDisturb == 1,
                        hasUnread = conversation.hasUnread,
                        isAtMentioned = conversation.isAtMentioned,
                        unreadCount = conversation.unreadMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun Badges(
    doNotDisturb: Boolean,
    hasUnread: Boolean,
    isAtMentioned: Boolean,
    unreadCount: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isAtMentioned) {
            Badge(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) {
                Text("@", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (hasUnread) {
            Badge(
                containerColor = if (doNotDisturb) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                contentColor = if (doNotDisturb) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
            ) {
                Text("$unreadCount", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatTime(timestampMs: Long): String {
    if (timestampMs <= 0) return ""

    val date = Date(timestampMs)
    val now = Date()

    val todayCalendar = Calendar.getInstance().apply {
        time = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val dateCalendar = Calendar.getInstance().apply {
        time = date
    }

    return when {
        date.after(todayCalendar.time) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        }

        dateCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) -> {
            SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(date)
        }

        else -> {
            SimpleDateFormat("yyyy/M/d HH:mm", Locale.getDefault()).format(date)
        }
    }
}
