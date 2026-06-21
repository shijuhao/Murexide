package com.juhao.murexide.ui.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.ui.components.Avatar
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juhao.murexide.ui.chat.components.EditMessageDialog
import com.juhao.murexide.ui.chat.components.MessageBubble
import com.juhao.murexide.ui.chat.components.MessageInput
import com.juhao.murexide.datastore.SettingsStorage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.ui.draw.alpha

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ChatScreen(
    token: String,
    chatId: String,
    chatType: Int,
    chatName: String,
    chatAvatar: String,
    onBackClick: () -> Unit,
    viewModel: ChatViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(
                    token = token,
                    chatId = chatId,
                    chatType = chatType,
                    deviceId = getDeviceId()
                ) as T
            }
        }
    )
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val recallDialog by viewModel.recallDialog.collectAsState()
    val editDialog by viewModel.editDialog.collectAsState()

    val listState = rememberLazyListState()
    var showScrollToBottom by remember { mutableStateOf(false) }
    var unreadCount by remember { mutableIntStateOf(0) }
    var isScrollingToBottom by remember { mutableStateOf(false) }
    var firstMessageId by remember { mutableStateOf<String?>(null) }
    
    val settingsStorage = remember { SettingsStorage(context) }
    val avatarFollowEnabled by settingsStorage.avatarFollowFlow.collectAsState(initial = false)

    val floatingAvatarState by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty() || uiState.messages.isEmpty() || !avatarFollowEnabled) {
                Triple(false, "", false)
            } else {
                val topVisibleItem = visibleItems.minByOrNull { it.index }
                if (topVisibleItem == null) {
                    Triple(false, "", false)
                } else {
                    val firstVisibleIndex = topVisibleItem.index
                    val message = uiState.messages.getOrNull(firstVisibleIndex)
                    
                    if (message == null || message.isRecalled) {
                        Triple(false, "", false)
                    } else {
                        val itemHeightDp = with(density) { topVisibleItem.size.toDp() }.value
                        val visibleHeightDp = with(density) { 
                            (topVisibleItem.size + topVisibleItem.offset.coerceAtMost(0)).toDp() 
                        }.value
                        
                        val hasEnoughSpace = visibleHeightDp >= 44 && itemHeightDp >= 44
                        
                        val currentIndex = uiState.messages.indexOfFirst { it.msgId == message.msgId }
                        val newerMessage = if (currentIndex > 0) uiState.messages[currentIndex - 1] else null
                        val olderMessage = if (currentIndex < uiState.messages.size - 1) uiState.messages[currentIndex + 1] else null
                        val isLastFromSender = olderMessage == null || olderMessage.isRecalled || olderMessage.senderId != message.senderId
                        val hasOtherSameSender = (newerMessage != null && !newerMessage.isRecalled && newerMessage.senderId == message.senderId && !isLastFromSender) ||
                                                 (olderMessage != null && !olderMessage.isRecalled && olderMessage.senderId == message.senderId)
                        
                        if (hasEnoughSpace) {
                            Triple(true, message.senderAvatar, message.isMine)
                        } else if (hasOtherSameSender && message.senderAvatar.isNotEmpty()) {
                            Triple(true, message.senderAvatar, message.isMine)
                        } else {
                            Triple(false, "", false)
                        }
                    }
                }
            }
        }
    }
    
    val showFloatingAvatar = floatingAvatarState.first
    val floatingAvatarUrl = floatingAvatarState.second
    val floatingAvatarIsMine = floatingAvatarState.third
    
    val topVisibleMessageId by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty()) {
                val topIndex = visibleItems.minByOrNull { it.index }?.index
                topIndex?.let { uiState.messages.getOrNull(it)?.msgId }
            } else {
                null
            }
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
    
            val shouldLoadMore = if (visibleItems.isNotEmpty()) {
                val lastVisibleIndex = visibleItems.last().index
                val totalItems = layoutInfo.totalItemsCount
                lastVisibleIndex >= totalItems - 5 && uiState.hasMore && !uiState.isLoadingMore && !uiState.isRefreshing
            } else {
                false
            }
    
            val atBottom = if (visibleItems.isNotEmpty()) {
                val firstVisibleIndex = visibleItems.first().index
                firstVisibleIndex == 0
            } else {
                true
            }
            
            Pair(shouldLoadMore, atBottom)
        }
        .collect { (shouldLoadMore, atBottom) ->
            if (shouldLoadMore) {
                viewModel.loadMore()
            }
    
            showScrollToBottom = !atBottom
            if (atBottom) {
                unreadCount = 0
            }
        }
    }
    
    LaunchedEffect(Unit) {
        var lastMsgId: String? = null
        var pendingCount = 0
        
        snapshotFlow { uiState.messages.firstOrNull()?.msgId }
            .collect { msgId: String? ->
                if (msgId == null) return@collect
                if (msgId == lastMsgId) return@collect
                lastMsgId = msgId
                
                pendingCount++
                
                if (firstMessageId == null) {
                    firstMessageId = msgId
                    pendingCount = 0
                    return@collect
                }
                
                if (msgId == firstMessageId) {
                    pendingCount = 0
                    return@collect
                }
                
                if (isScrollingToBottom || listState.isScrollInProgress) {
                    firstMessageId = msgId
                    return@collect
                }
                
                val isAtBottom = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull()?.index == 0
                
                firstMessageId = msgId
                
                if (isAtBottom && !listState.isScrollInProgress) {
                    isScrollingToBottom = true
                    if (!listState.isScrollInProgress) {
                        listState.animateScrollToItem(0)
                        unreadCount = 0
                    }
                    isScrollingToBottom = false
                    pendingCount = 0
                } else {
                    unreadCount += pendingCount
                    pendingCount = 0
                }
            }
    }
    
    val scrollToBottom: () -> Unit = {
        if (!isScrollingToBottom) {
            scope.launch {
                isScrollingToBottom = true
                listState.animateScrollToItem(0)
                unreadCount = 0
                if (uiState.messages.isNotEmpty()) {
                    firstMessageId = uiState.messages.first().msgId
                }
                isScrollingToBottom = false
            }
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(
                            url = chatAvatar,
                            size = 36.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = chatName,
                                fontSize = 18.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (chatType == 2 && uiState.memberCount != null) {
                                Text(
                                    text = "${uiState.memberCount} 人",
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                actions = {
                    Box{
                        IconButton(onClick = {  }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.imePadding()) {
                AnimatedVisibility(
                    visible = uiState.replyTo != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    uiState.replyTo?.let { repliedMessage ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(32.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = repliedMessage.senderName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = repliedMessage.getDisplayContent(),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.clearReplyTo() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "取消引用", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
    
                MessageInput(
                    inputText = uiState.inputText,
                    isMarkdown = uiState.isMarkdown,
                    isSending = uiState.isSending,
                    onTextChange = { viewModel.updateInputText(it) },
                    onSendClick = { viewModel.sendMessage() },
                    onAddImageClick = { },
                    onToggleMarkdown = { viewModel.toggleMarkdown() }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            uiState.backgroundUrl?.takeIf { it.isNotEmpty() }?.let { bgUrl ->
                val bgRequest = remember(bgUrl) {
                    ImageRequest.Builder(context)
                        .data(bgUrl)
                        .apply {
                            if (bgUrl.contains("jwznb.com")) {
                                setHeader("Referer", "https://myapp.jwznb.com")
                            }
                        }
                        .build()
                }
                AsyncImage(
                    model = bgRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().alpha(0.6f),
                    contentScale = ContentScale.Crop
                )
            }

            if (uiState.isLoading && uiState.messages.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null && uiState.messages.isEmpty()) {
                Text(
                    text = "错误: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = uiState.messages,
                            key = { it.msgId }
                        ) { message ->
                            val index = uiState.messages.indexOf(message)
                            
                            val newerMessage = if (index > 0) uiState.messages[index - 1] else null
                            val olderMessage = if (index < uiState.messages.size - 1) uiState.messages[index + 1] else null
                        
                            val isFirstFromSender = newerMessage == null || newerMessage.isRecalled || newerMessage.senderId != message.senderId
                            val isLastFromSender = olderMessage == null || olderMessage.isRecalled || olderMessage.senderId != message.senderId
                            val isOlderSameSender = olderMessage != null && !olderMessage.isRecalled && olderMessage.senderId == message.senderId
                            val isNewerSameSender = newerMessage != null && !newerMessage.isRecalled && newerMessage.senderId == message.senderId
                        
                            val isTopVisibleItem = message.msgId == topVisibleMessageId
                        
                            val shouldShowItemAvatar = if (isTopVisibleItem) {
                                !showFloatingAvatar && ((isLastFromSender && avatarFollowEnabled) || isFirstFromSender)
                            } else {
                                isFirstFromSender
                            }
                        
                            val avatarAlignment = if (isTopVisibleItem && shouldShowItemAvatar && avatarFollowEnabled) {
                                if (isLastFromSender) Alignment.Top else Alignment.Bottom
                            } else {
                                Alignment.Bottom
                            }
                        
                            MessageBubble(
                                message = message,
                                onRecall = { viewModel.showRecallDialog(message.msgId) },
                                onEdit = { viewModel.showEditDialog(message) },
                                onReply = { viewModel.setReplyTo(message) },
                                isAdmin = uiState.isAdmin,
                                isLastFromSender = isLastFromSender,
                                isFirstFromSender = isFirstFromSender,
                                isOlderSameSender = isOlderSameSender,
                                isNewerSameSender = isNewerSameSender,
                                showAvatar = shouldShowItemAvatar,
                                avatarAlignment = avatarAlignment
                            )
                        }

                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
    
                AnimatedScrollToBottomButton(
                    visible = showScrollToBottom,
                    unreadCount = unreadCount,
                    onClick = scrollToBottom,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                )

                if (showFloatingAvatar) {
                    Column (
                        modifier = Modifier
                            .align(if (floatingAvatarIsMine) Alignment.BottomEnd else Alignment.BottomStart)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Avatar(
                            url = floatingAvatarUrl,
                            size = 36.dp
                        )
                    }
                }
            }
        }
    }

    if (recallDialog.isOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.hideRecallDialog() },
            title = { Text("撤回消息") },
            text = { Text("确定要撤回这条消息吗？") },
            confirmButton = {
                TextButton(onClick = { viewModel.recallMessage() }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideRecallDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    if (editDialog.isOpen && editDialog.message != null) {
        EditMessageDialog(
            state = editDialog,
            onDismiss = { viewModel.hideEditDialog() },
            onContentChange = { viewModel.updateEditContent(it) },
            onSave = { viewModel.editMessage() },
            onToggleMarkdown = { viewModel.toggleEditMarkdown() }
        )
    }
}

private fun getDeviceId(): String {
    return "android_${System.currentTimeMillis()}"
}

@Composable
fun AnimatedScrollToBottomButton(
    visible: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "scroll_button_alpha"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "scroll_button_scale"
    )

    Box(
        modifier = modifier
            .wrapContentSize()
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = animatedScale
                scaleY = animatedScale
            }
    ) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        ) {
            SmallFloatingActionButton (
                onClick = onClick,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "滚动到底部",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
