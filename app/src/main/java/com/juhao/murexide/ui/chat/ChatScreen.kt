package com.juhao.murexide.ui.chat

import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import android.view.View
import androidx.core.view.OnApplyWindowInsetsListener
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juhao.murexide.ui.components.Avatar
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juhao.murexide.ui.components.MultiImageViewer
import com.juhao.murexide.ui.chat.components.EditMessageDialog
import com.juhao.murexide.ui.chat.components.MessageBubble
import com.juhao.murexide.ui.chat.components.MessageInput
import com.juhao.murexide.ui.chat.components.EmojiPanel
import com.juhao.murexide.ui.chat.components.UploadProgressBar
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.data.MessageItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    chatType: Int,
    chatName: String,
    chatAvatar: String,
    onBackClick: () -> Unit = {},
    bigScreenMode: Boolean = false,
    viewModel: ChatViewModel
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val expressions by viewModel.stickerPanel.collectAsState()
    
    var showMenuMsgId by remember { mutableStateOf<String?>(null) }
    val recallDialog by viewModel.recallDialog.collectAsState()
    val editDialog by viewModel.editDialog.collectAsState()

    val listState = rememberLazyListState()
    var showScrollToBottom by remember { mutableStateOf(false) }
    var unreadCount by remember { mutableIntStateOf(0) }
    var firstMessageId by remember { mutableStateOf<String?>(null) }
    
    val settingsStorage = remember { SettingsStorage(context) }
    val avatarFollowEnabled by settingsStorage.avatarFollowFlow.collectAsState(initial = false)
    
    var viewerImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var viewerInitialPage by remember { mutableIntStateOf(0) }
    var viewerVisible by remember { mutableStateOf(false) }
    
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    
    var isKeyboardVisible by remember { mutableStateOf(false) }
    
    DisposableEffect(view) {
        val listener = View.OnApplyWindowInsetsListener { v, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (imeVisible != isKeyboardVisible) {
                isKeyboardVisible = imeVisible
                if (imeVisible && viewModel.stickerPanel.value.isVisible) {
                    viewModel.hideStickerPanel()
                }
            }
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(view, listener)
        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
        }
    }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadAndSendImage(it)
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            imagePickerLauncher.launch("image/*")
        } else {
            Toast.makeText(context, "需要存储权限才能选择图片", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun openImagePicker() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        val needRequest = permissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needRequest) {
            permissionLauncher.launch(permissions)
        } else {
            imagePickerLauncher.launch("image/*")
        }
    }

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
        
        snapshotFlow { uiState.messages.firstOrNull() }
            .collect { message: MessageItem? ->
                val msgId = message?.msgId
                if (message == null) return@collect
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
                
                if (listState.isScrollInProgress) {
                    firstMessageId = msgId
                    return@collect
                }
                
                val isAtBottom = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull()?.index == 0
                
                firstMessageId = msgId
                
                if (isAtBottom && !listState.isScrollInProgress) {
                    showMenuMsgId = null
                    if (!listState.isScrollInProgress) {
                        listState.animateScrollToItem(0)
                        unreadCount = 0
                    }
                    pendingCount = 0
                } else {
                    if (!message.isMine) {
                        unreadCount += pendingCount
                    }
                    pendingCount = 0
                }
            }
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
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = if (bigScreenMode) {
            WindowInsets(0, 0, 0, 0)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        topBar = {
            TopAppBar(
                windowInsets = if (bigScreenMode) {
                    WindowInsets(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                } else {
                    TopAppBarDefaults.windowInsets
                },
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
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (chatType == 2 && uiState.memberCount != null) {
                                Text(
                                    text = "${uiState.memberCount} 位成员",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ),
                actions = {
                    Box{
                        IconButton(onClick = {  }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                        }
                    }
                },
                navigationIcon = {
                    if (!bigScreenMode) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.imePadding()
            ) {
                if (uiState.isUploading) {
                    UploadProgressBar(
                        progress = uiState.uploadProgress,
                        imagePath = uiState.uploadImagePath ?: "",
                        onCancel = { viewModel.cancelUpload() }
                    )
                }
                
                AnimatedVisibility(
                    visible = uiState.replyTo != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
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
                                    text = uiState.replyTo?.senderName ?: "用户",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = uiState.replyTo?.getDisplayContent() ?: "消息",
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
    
                MessageInput(
                    inputText = uiState.inputText,
                    sendType = uiState.sendType,
                    isSending = uiState.isSending,
                    bigScreenMode = bigScreenMode,
                    onTextChange = { viewModel.updateInputText(it) },
                    onSendClick = { viewModel.sendMessage() },
                    onAddImageClick = { openImagePicker() },
                    onToggleSendType = { type ->
                        viewModel.toggleSendType(type)
                    },
                    onEmojiClick = {
                        if (expressions.isVisible) {
                            viewModel.hideStickerPanel()
                        } else {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            viewModel.toggleStickerPanel()
                        }
                    }
                )
                
                BackHandler(enabled = expressions.isVisible) {
                    viewModel.hideStickerPanel()
                }
                
                AnimatedVisibility(
                    visible = expressions.isVisible,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    EmojiPanel(
                        expressions = expressions.expressions,
                        isLoading = expressions.isLoading,
                        onExpressionClick = { expression ->
                            viewModel.sendExpression(expression)
                        },
                        onStickerItemClick = { stickerItem ->
                            viewModel.sendStickerItem(stickerItem)
                        },
                        stickerPacks = expressions.stickerPacks,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                }
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
                    modifier = Modifier.fillMaxSize().alpha(0.5f),
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
                        
                            val isFirstFromSender = newerMessage == null || newerMessage.isRecalled || newerMessage.contentType == MessageItem.CONTENT_TYPE_TIP || newerMessage.senderId != message.senderId
                            val isLastFromSender = olderMessage == null || olderMessage.isRecalled || olderMessage.contentType == MessageItem.CONTENT_TYPE_TIP || olderMessage.senderId != message.senderId
                            val isOlderSameSender = olderMessage != null && !olderMessage.isRecalled && olderMessage.contentType != MessageItem.CONTENT_TYPE_TIP && olderMessage.senderId == message.senderId
                            val isNewerSameSender = newerMessage != null && !newerMessage.isRecalled && newerMessage.contentType != MessageItem.CONTENT_TYPE_TIP && newerMessage.senderId == message.senderId
                        
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
                                avatarAlignment = avatarAlignment,
                                showMenu = showMenuMsgId == message.msgId,
                                showMenuMsgId = showMenuMsgId,
                                showMenuChanged = {
                                    showMenuMsgId = it
                                },
                                onImageClick = { imageUrl ->
                                    val allImages = uiState.messages
                                        .filter { !it.isRecalled }
                                        .mapNotNull { it.imageUrl }
                                        .filter { it.isNotEmpty() }
                                        .reversed()
                                    
                                    if (allImages.isNotEmpty()) {
                                        val index = allImages.indexOf(imageUrl)
                                        viewerImages = allImages
                                        viewerInitialPage = if (index >= 0) index else 0
                                        viewerVisible = true
                                    }
                                }
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
                
                val targetAlpha = if (showMenuMsgId != null && topVisibleMessageId != showMenuMsgId) 0.4f else 1f
                val animatedAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = tween(durationMillis = 300),
                    label = "floating_avatar_alpha"
                )

                if (showFloatingAvatar) {
                    Column (
                        modifier = Modifier
                            .alpha(animatedAlpha)
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
    
    if (viewerVisible) {
        MultiImageViewer(
            images = viewerImages,
            initialPage = viewerInitialPage,
            isVisible = true,
            onDismiss = { viewerVisible = false }
        )
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
            onToggleSendType = { type ->
                viewModel.toggleEditSendType(type) 
            }
        )
    }
}

fun getDeviceId(): String {
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

private fun getRealPathFromUri(context: Context, uri: Uri): String? {
    return try {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
        cursor?.use { 
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            it.moveToFirst()
            it.getString(columnIndex)
        }
    } catch (e: Exception) {
        uri.toString()
    }
}