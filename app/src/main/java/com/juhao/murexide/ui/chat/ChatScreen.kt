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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.ui.components.Avatar
import com.composables.icons.lucide.*
import com.juhao.murexide.ui.chat.components.EditMessageDialog
import com.juhao.murexide.ui.chat.components.MessageBubble
import com.juhao.murexide.ui.chat.components.MessageInput
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape

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
        factory = ChatViewModelFactory(token, chatId, chatType)
    )
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val recallDialog by viewModel.recallDialog.collectAsState()
    val editDialog by viewModel.editDialog.collectAsState()

    val listState = rememberLazyListState()
    var showScrollToBottom by remember { mutableStateOf(false) }
    var unreadCount by remember { mutableIntStateOf(0) }
    var firstMessageId by remember { mutableStateOf<String?>(null) }
    
    var showFloatingAvatar by remember { mutableStateOf(false) }
    var floatingAvatarUrl by remember { mutableStateOf("") }
    var floatingAvatarIsMine by remember { mutableStateOf(false) }
    
    var topMessageHasEnoughSpace by remember { mutableStateOf(false) }
    var lastTopIndex by remember { mutableStateOf<Int?>(null) }
    
    LaunchedEffect(topVisibleMessageIndex) {
        if (lastTopIndex != topVisibleMessageIndex) {
            lastTopIndex = topVisibleMessageIndex
            topMessageHasEnoughSpace = false
        }
    }

    val topVisibleMessageIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty()) {
                visibleItems.minByOrNull { it.index }?.index
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
    
            val floatingAvatarState = if (visibleItems.isEmpty() || uiState.messages.isEmpty()) {
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
                        
                        val newerMessage = if (firstVisibleIndex > 0) uiState.messages.getOrNull(firstVisibleIndex - 1) else null
                        val olderMessage = if (firstVisibleIndex < uiState.messages.size - 1) uiState.messages.getOrNull(firstVisibleIndex + 1) else null
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
            
            Triple(shouldLoadMore, atBottom, floatingAvatarState)
        }
        .collect { (shouldLoadMore, atBottom, floatingAvatarState) ->
            if (shouldLoadMore) {
                viewModel.loadMore()
            }
    
            showScrollToBottom = !atBottom
            if (atBottom) {
                unreadCount = 0
                if (uiState.messages.isNotEmpty()) {
                    firstMessageId = uiState.messages.first().msgId
                }
            }
    
            val (show, url, isMine) = floatingAvatarState
            
            topMessageHasEnoughSpace = show
    
            if (show) {
                showFloatingAvatar = true
                floatingAvatarUrl = url
                floatingAvatarIsMine = isMine
            } else {
                showFloatingAvatar = false
                floatingAvatarUrl = ""
                floatingAvatarIsMine = false
            }
        }
    }

    LaunchedEffect(uiState.messages) {
        if (uiState.messages.isEmpty()) return@LaunchedEffect

        val currentFirstId = uiState.messages.first().msgId

        if (firstMessageId != null && currentFirstId != firstMessageId) {
            if (showScrollToBottom) {
                unreadCount += 1
            } else {
                listState.scrollToItem(0)
                unreadCount = 0
            }
        }

        firstMessageId = currentFirstId
    }

    val scrollToBottom: () -> Unit = {
        scope.launch {
            listState.animateScrollToItem(0)
            unreadCount = 0
            if (uiState.messages.isNotEmpty()) {
                firstMessageId = uiState.messages.first().msgId
            }
        }
    }

    Scaffold(
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
                                fontSize = 18.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Lucide.ArrowLeft, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = uiState.replyTo != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    uiState.replyTo?.let { repliedMessage ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
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
                                        text = repliedMessage.content.ifEmpty { "消息" },
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
                                    Icon(Lucide.X, contentDescription = "取消引用", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
    
                MessageInput(
                    inputText = uiState.inputText,
                    selectedImages = uiState.selectedImages,
                    isMarkdown = uiState.isMarkdown,
                    onTextChange = { viewModel.updateInputText(it) },
                    onSendClick = { viewModel.sendMessage() },
                    onAddImageClick = { },
                    onRemoveImage = { viewModel.removeImage(it) },
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
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
    
                        itemsIndexed(items = uiState.messages) { index, message ->
                            val newerMessage = if (index > 0) uiState.messages[index - 1] else null
                            val olderMessage = if (index < uiState.messages.size - 1) uiState.messages[index + 1] else null
    
                            val isFirstFromSender = newerMessage == null || newerMessage.isRecalled || newerMessage.senderId != message.senderId
                            val isLastFromSender = olderMessage == null || olderMessage.isRecalled || olderMessage.senderId != message.senderId
                            val isOlderSameSender = olderMessage != null && !olderMessage.isRecalled && olderMessage.senderId == message.senderId
                            val isNewerSameSender = newerMessage != null && !newerMessage.isRecalled && newerMessage.senderId == message.senderId

                            val isTopVisibleItem = index == topVisibleMessageIndex

                            val shouldShowItemAvatar = if (isTopVisibleItem) {
                                !showFloatingAvatar && (isLastFromSender || isFirstFromSender)
                            } else {
                                isFirstFromSender
                            }
                            
                            val avatarAlignment = if (isTopVisibleItem && shouldShowItemAvatar) {
                                if (topMessageHasEnoughSpace) Alignment.Bottom else Alignment.Top
                            } else {
                                Alignment.Bottom
                            }
    
                            MessageBubble(
                                message = message,
                                onRecall = { viewModel.showRecallDialog(message.msgId) },
                                onEdit = { viewModel.showEditDialog(message) },
                                clipboardManager = clipboardManager,
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
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(
                    imageVector = Lucide.ChevronDown,
                    contentDescription = "滚动到底部",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
