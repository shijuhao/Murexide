@file:Suppress("AssignedValueIsNeverRead")

package com.juhao.murexide.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
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
import com.juhao.murexide.ui.chat.components.ScreenshotBottomSheet
import com.juhao.murexide.ui.chat.components.saveBitmapToGallery
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.data.MessageItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.draw.clip
import com.juhao.murexide.ui.conversationdetail.ConversationDetailActivity

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class, ExperimentalComposeUiApi::class)
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
    var showMoreMenu by remember { mutableStateOf(false) }

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

    val windowInsetsIme = WindowInsets.ime
    val isKeyboardOpen by remember {
        derivedStateOf {
            windowInsetsIme.getBottom(density) > 0
        }
    }

    LaunchedEffect(isKeyboardOpen) {
        if (isKeyboardOpen) {
            viewModel.hideStickerPanel()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadAndSendImage(it, context)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            imagePickerLauncher.launch("image/*")
        } else {
            Toast.makeText(context, "需要权限才能选择图片", Toast.LENGTH_SHORT).show()
        }
    }

    fun openImagePicker() {
        val permissions =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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
    
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadAndSendVideo(it, context)
        }
    }

    val videoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            videoPickerLauncher.launch("video/*")
        } else {
            Toast.makeText(context, "需要权限才能选择视频", Toast.LENGTH_SHORT).show()
        }
    }

    fun openVideoPicker() {
        val permissions =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

        val needRequest = permissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needRequest) {
            videoPermissionLauncher.launch(permissions)
        } else {
            videoPickerLauncher.launch("video/*")
        }
    }

    val selectionMode = uiState.selectionMode
    val selectedMessages = uiState.selectedMessages
    
    BackHandler(enabled = selectionMode) {
        viewModel.exitSelectionMode()
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

                        val currentIndex =
                            uiState.messages.indexOfFirst { it.msgId == message.msgId }
                        val newerMessage =
                            if (currentIndex > 0) uiState.messages[currentIndex - 1] else null
                        val olderMessage =
                            if (currentIndex < uiState.messages.size - 1) uiState.messages[currentIndex + 1] else null
                        val isLastFromSender =
                            olderMessage == null || olderMessage.isRecalled || olderMessage.senderId != message.senderId
                        val hasOtherSameSender =
                            (newerMessage != null && !newerMessage.isRecalled && newerMessage.senderId == message.senderId && !isLastFromSender) ||
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

    val topVisibleMessage by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty()) {
                val topIndex = visibleItems.minByOrNull { it.index }?.index
                topIndex?.let { uiState.messages.getOrNull(it) }
            } else {
                null
            }
        }
    }

    val topVisibleMessageId = topVisibleMessage?.msgId

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
    
    var showScreenshotSheet by remember { mutableStateOf(false) }
    
    if (showScreenshotSheet) {
        val orderedSelected = uiState.messages
            .filter { it in selectedMessages }
            .reversed()
            
        ScreenshotBottomSheet(
            messages = orderedSelected,
            chatName = chatName,
            chatAvatar = chatAvatar,
            onDismiss = { showScreenshotSheet = false },
            onSaveImage = { bitmap ->
                scope.launch {
                    saveBitmapToGallery(context, bitmap)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = if (bigScreenMode) {
            WindowInsets(0, 0, 0, 0)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        topBar = {
            AnimatedContent(
                targetState = selectionMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith
                            fadeOut(animationSpec = tween(200))
                },
                label = "top_bar_transition"
            ) { isSelectionMode ->
                if (isSelectionMode) {
                    TopAppBar(
                        windowInsets = if (bigScreenMode) {
                            WindowInsets(
                                top = WindowInsets.statusBars.asPaddingValues()
                                    .calculateTopPadding()
                            )
                        } else {
                            TopAppBarDefaults.windowInsets
                        },
                        title = {
                            AnimatedContent(
                                targetState = selectedMessages.size,
                                transitionSpec = {
                                    if (targetState > initialState) {
                                        slideInVertically(
                                            initialOffsetY = { fullHeight -> fullHeight },
                                            animationSpec = tween(200)
                                        ) togetherWith slideOutVertically(
                                            targetOffsetY = { fullHeight -> -fullHeight },
                                            animationSpec = tween(200)
                                        )
                                    } else {
                                        slideInVertically(
                                            initialOffsetY = { fullHeight -> -fullHeight },
                                            animationSpec = tween(200)
                                        ) togetherWith slideOutVertically(
                                            targetOffsetY = { fullHeight -> fullHeight },
                                            animationSpec = tween(200)
                                        )
                                    }
                                },
                                label = "selected_count"
                            ) { count ->
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.exitSelectionMode() }) {
                                Icon(Icons.Rounded.Close, contentDescription = "退出多选")
                            }
                        },
                        actions = {
                            if (selectedMessages.size == 1) {
                                IconButton(onClick = { 
                                    selectedMessages.firstOrNull()?.let { viewModel.setReplyTo(it) }
                                    viewModel.exitSelectionMode()
                                }) {
                                    Icon(Icons.Rounded.FormatQuote, contentDescription = "引用")
                                }
                            }
                            IconButton(onClick = { showScreenshotSheet = true }) {
                                Icon(Icons.Rounded.Crop, contentDescription = "截图")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                } else {
                    TopAppBar(
                        windowInsets = if (bigScreenMode) {
                            WindowInsets(
                                top = WindowInsets.statusBars.asPaddingValues()
                                    .calculateTopPadding()
                            )
                        } else {
                            TopAppBarDefaults.windowInsets
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        ConversationDetailActivity.start(
                                            context = context,
                                            chatId = viewModel.chatId,
                                            chatType = chatType,
                                            chatName = chatName,
                                            chatAvatar = chatAvatar
                                        )
                                    }
                            ) {
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
                            Box {
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("会话详情") },
                                        onClick = {
                                            showMoreMenu = false
                                            ConversationDetailActivity.start(
                                                context = context,
                                                chatId = viewModel.chatId,
                                                chatType = chatType,
                                                chatName = chatName,
                                                chatAvatar = chatAvatar
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(24.dp))
                                        }
                                    )
                                }

                                IconButton(onClick = {
                                    showMoreMenu = true
                                }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                                }
                            }
                        },
                        navigationIcon = {
                            if (!bigScreenMode) {
                                IconButton(onClick = onBackClick) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "返回"
                                    )
                                }
                            }
                        }
                    )
                }
            }
        },
        bottomBar = {
            AnimatedContent(
                targetState = selectionMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith
                            fadeOut(animationSpec = tween(200))
                },
                label = "bottom_bar_transition"
            ) { isSelectionMode ->
                if (isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { /* 转发选中消息 */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("转发")
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = { viewModel.recallSelectedMessages() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("撤回")
                        }
                    }
                } else {
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
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = uiState.replyTo?.getDisplayContent() ?: "消息",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.clearReplyTo() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "取消引用",
                                            modifier = Modifier.size(16.dp)
                                        )
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
                            onAddVideoClick = { openVideoPicker() },
                            onToggleSendType = { type ->
                                viewModel.toggleSendType(type)
                            },
                            requestFocus = uiState.requestInputFocus,
                            onFocusConsumed = { viewModel.onInputFocusConsumed() },
                            isEmojiPanelVisible = expressions.isVisible,
                            onEmojiClick = {
                                if (expressions.isVisible) {
                                    viewModel.hideStickerPanel()
                                    viewModel.showKeyboard()
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
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.5f),
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

                            val newerMessage = uiState.messages.getOrNull(index - 1)
                            val olderMessage = uiState.messages.getOrNull(index + 1)

                            val isFirstFromSender =
                                newerMessage == null || newerMessage.isRecalled || newerMessage.contentType == MessageItem.CONTENT_TYPE_TIP || newerMessage.senderId != message.senderId
                            val isLastFromSender =
                                olderMessage == null || olderMessage.isRecalled || olderMessage.contentType == MessageItem.CONTENT_TYPE_TIP || olderMessage.senderId != message.senderId
                            val isOlderSameSender =
                                olderMessage != null && !olderMessage.isRecalled && olderMessage.contentType != MessageItem.CONTENT_TYPE_TIP && olderMessage.senderId == message.senderId
                            val isNewerSameSender =
                                newerMessage != null && !newerMessage.isRecalled && newerMessage.contentType != MessageItem.CONTENT_TYPE_TIP && newerMessage.senderId == message.senderId

                            val isTopVisibleItem = message.msgId == topVisibleMessageId

                            val shouldShowItemAvatar = if (isTopVisibleItem) {
                                !showFloatingAvatar && ((isLastFromSender && avatarFollowEnabled) || isFirstFromSender)
                            } else {
                                isFirstFromSender
                            }

                            val avatarAlignment =
                                if (isTopVisibleItem && shouldShowItemAvatar && avatarFollowEnabled) {
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
                                isSelectionMode = selectionMode,
                                isSelected = message in selectedMessages,
                                onLongPress = { msg -> viewModel.enterSelectionMode(msg) },
                                onClickInSelectionMode = { msg -> viewModel.toggleMessageSelection(msg) },
                                showMenu = showMenuMsgId == message.msgId && !selectionMode,
                                showMenuMsgId = showMenuMsgId,
                                showMenuChanged = { msgId ->
                                    if (!selectionMode) {
                                        showMenuMsgId = msgId
                                    }
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
                                },
                                onAvatarClick = {
                                    ConversationDetailActivity.start(
                                        context = context,
                                        chatId = message.senderId,
                                        chatType = message.senderType,
                                        chatName = message.senderName,
                                        chatAvatar = message.senderAvatar
                                    )
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

                val targetAlpha =
                    if (showMenuMsgId != null && topVisibleMessageId != showMenuMsgId) 0.4f else 1f
                val animatedAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = tween(durationMillis = 300),
                    label = "floating_avatar_alpha"
                )

                if (showFloatingAvatar) {
                    Column(
                        modifier = Modifier
                            .alpha(animatedAlpha)
                            .align(if (floatingAvatarIsMine) Alignment.BottomEnd else Alignment.BottomStart)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Avatar(
                            modifier = Modifier.clickable {
                                ConversationDetailActivity.start(
                                    context = context,
                                    chatId = topVisibleMessage?.senderId ?: "0",
                                    chatType = topVisibleMessage?.senderType ?: 0,
                                    chatName = topVisibleMessage?.senderName ?: "",
                                    chatAvatar = topVisibleMessage?.senderAvatar ?: ""
                                )
                            },
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
            SmallFloatingActionButton(
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