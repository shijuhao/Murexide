package com.juhao.murexide.ui.chat.components

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.ui.chat.EditDialogState
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.MarkdownRenderer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: MessageItem,
    onRecall: () -> Unit,
    onEdit: () -> Unit,
    onReply: () -> Unit,
    isAdmin: Boolean = false,
    isLastFromSender: Boolean = true,
    isFirstFromSender: Boolean = true,
    isOlderSameSender: Boolean = false,
    isNewerSameSender: Boolean = false,
    showAvatar: Boolean = true,
    avatarAlignment: Alignment.Vertical = Alignment.Bottom
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var showMenu by remember { mutableStateOf(false) }
    val isMine = message.isMine
    val context = LocalContext.current

    val timestampDisplay = remember(message.timestamp) {
        try {
            val date = Date(message.timestamp)
            val now = Date()
        
            val todayCalendar = Calendar.getInstance().apply {
                time = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        
            when {
                date.after(todayCalendar.time) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
                date.after(Date(todayCalendar.timeInMillis - 86400000)) -> "昨天 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
                else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) {
            ""
        }
    }

    if (message.isRecalled) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 250.dp)
            ) {
                Text(
                    text = "${message.senderName} 撤回了一条消息",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    } else if (message.contentType == MessageItem.CONTENT_TYPE_TIP) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 250.dp)
            ) {
                Text(
                    text = message.content,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        showMenu = true
                    },
                    onLongClick = { showMenu = true }
                )
                .padding(
                    start = 8.dp,
                    end = 8.dp,
                    top = if (isOlderSameSender) 0.dp else 4.dp,
                    bottom = if (isNewerSameSender) 0.dp else 4.dp
                ),
            verticalAlignment = avatarAlignment,
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
        ) {
            if (!isMine && showAvatar) {
                Avatar(
                    url = message.senderAvatar,
                    size = 36.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else if (!isMine) {
                Spacer(modifier = Modifier.width(44.dp))
            }

            val hideMsgCard = message.contentType == MessageItem.CONTENT_TYPE_IMAGE
                || message.contentType == MessageItem.CONTENT_TYPE_STICKER
                || message.contentType == MessageItem.CONTENT_TYPE_FILE

            Box(modifier = Modifier.weight(1f, fill = false)) {
                Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                    Card(
                        shape = RoundedCornerShape(
                            topStart = if (isMine) 16.dp else if (isLastFromSender) 16.dp else 4.dp,
                            topEnd = if (isMine) if (isLastFromSender) 16.dp else 4.dp else 16.dp,
                            bottomStart = if (isMine) 16.dp else if (isFirstFromSender) 16.dp else 4.dp,
                            bottomEnd = if (isMine) if (isFirstFromSender) 16.dp else 4.dp else 16.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (hideMsgCard)
                                Color.Transparent
                            else if (isMine)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(if (hideMsgCard) 0.dp else 8.dp)) {
                            if (!isMine && isLastFromSender && !hideMsgCard) {
                                Row(
                                    modifier = Modifier.padding(bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = message.senderName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    if (message.senderType == 3) {
                                        Surface(
                                            shape = RoundedCornerShape(50.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "机器人",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            message.cmdName?.let {
                                Text(
                                    text = "/$it",
                                    fontSize = 12.sp,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            if (message.quoteMsgText != null) {
                                val quoteText = message.quoteMsgText
                                Surface(
                                    modifier = Modifier.padding(bottom = 4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(22.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(2.dp)
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (message.quoteImageUrl != null) {
                                            val builder = ImageRequest.Builder(context)
                                                .data(message.quoteImageUrl)

                                            if (message.quoteImageUrl.contains("chat-img.jwznb.com") ||
                                                message.quoteImageUrl.contains("jwznb.com") ||
                                                message.quoteImageUrl.contains("myapp.jwznb.com")) {
                                                builder.setHeader("Referer", "https://myapp.jwznb.com")
                                            }

                                            AsyncImage(
                                                model = builder.build(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = quoteText,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            when (message.contentType) {
                                MessageItem.CONTENT_TYPE_TEXT,
                                MessageItem.CONTENT_TYPE_MARKDOWN -> {
                                    if (message.contentType == MessageItem.CONTENT_TYPE_MARKDOWN) {
                                        MarkdownRenderer.Render(
                                            content = message.content
                                        )
                                    } else {
                                        Text(
                                            text = message.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                MessageItem.CONTENT_TYPE_IMAGE,
                                MessageItem.CONTENT_TYPE_STICKER -> {
                                    message.imageUrl?.let { url ->
                                        val builder = ImageRequest.Builder(context)
                                            .data(url)

                                        if (url.contains("chat-img.jwznb.com") ||
                                            url.contains("jwznb.com") ||
                                            url.contains("myapp.jwznb.com")) {
                                            builder.setHeader("Referer", "https://myapp.jwznb.com")
                                        }

                                        Box {
                                            AsyncImage(
                                                model = builder.build(),
                                                contentDescription = null,
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier
                                                    .then(
                                                        if (isLastFromSender || message.quoteMsgText != null)
                                                            Modifier.clip(
                                                                RoundedCornerShape(
                                                                    topStart = 16.dp,
                                                                    topEnd = 16.dp
                                                                )
                                                            )
                                                         else Modifier
                                                    )
                                                    .combinedClickable(
                                                        onClick = {},
                                                        onLongClick = { showMenu = true }
                                                    )
                                            )

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(end = 6.dp, bottom = 6.dp)
                                                    .background(
                                                        color = Color.Black.copy(alpha = 0.3f),
                                                        shape = RoundedCornerShape(50.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                if (message.contentType == MessageItem.CONTENT_TYPE_STICKER) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Mood,
                                                        contentDescription = "mood",
                                                        modifier = Modifier.size(12.dp),
                                                        tint = Color.White
                                                    )
                                                }
                                                Text(
                                                    text = timestampDisplay,
                                                    fontSize = 10.sp,
                                                    lineHeight = 16.sp,
                                                    maxLines = 1,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                MessageItem.CONTENT_TYPE_FILE -> {
                                    message.fileName?.let { fileName ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(
                                                    if (isLastFromSender || message.quoteMsgText != null)
                                                        Modifier.clip(
                                                            RoundedCornerShape(
                                                                topStart = 16.dp,
                                                                topEnd = 16.dp
                                                            )
                                                        )
                                                     else Modifier
                                                )
                                                .background(
                                                    if (isMine)
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                                                    else
                                                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
                                                )
                                                .clickable { /* TODO: 打开/下载文件 */ }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = getFileIcon(fileName),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = fileName,
                                                    fontSize = 14.sp,
                                                    lineHeight = 20.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )

                                                Row(modifier = Modifier.padding(top = 2.dp)) {
                                                    message.fileSize?.let { size ->
                                                        Text(
                                                            text = formatFileSize(size),
                                                            fontSize = 12.sp,
                                                            lineHeight = 18.sp,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                            modifier = Modifier.padding(end = 4.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = timestampDisplay,
                                                        fontSize = 12.sp,
                                                        lineHeight = 18.sp,
                                                        maxLines = 1,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }

                                            Icon(
                                                imageVector = Icons.Rounded.Download,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }

                                MessageItem.CONTENT_TYPE_VIDEO -> {
                                    // TODO: 视频消息 UI
                                }

                                MessageItem.CONTENT_TYPE_AUDIO -> {
                                    // TODO: 音频消息 UI
                                }
                            }

                            if (!hideMsgCard) {
                                Row(
                                    modifier = Modifier.align(if (isMine) Alignment.End else Alignment.Start)
                                ) {
                                    Text(
                                        text = timestampDisplay,
                                        fontSize = 10.sp,
                                        lineHeight = 16.sp,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    if (message.isEdited) {
                                        Text(
                                            text = "已编辑",
                                            fontSize = 10.sp,
                                            lineHeight = 16.sp,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.align(if (isMine) Alignment.TopStart else Alignment.TopEnd)
                ) {
                    if (message.content.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("复制") },
                            onClick = {
                                scope.launch {
                                    clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("msg", message.content)))
                                }
                                Toast.makeText(context, "复制成功", Toast.LENGTH_SHORT).show()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("引用") },
                        onClick = {
                            showMenu = false
                            onReply()
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.FormatQuote, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )

                    if (isMine || isAdmin) {
                        DropdownMenuItem(
                            text = { Text("撤回") },
                            onClick = {
                                showMenu = false
                                onRecall()
                            },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                    }

                    if (isMine && message.content.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }

            if (isMine && showAvatar) {
                Spacer(modifier = Modifier.width(8.dp))
                Avatar(
                    url = message.senderAvatar,
                    size = 36.dp
                )
            } else if (isMine) {
                Spacer(modifier = Modifier.width(44.dp))
            }
        }
    }
}

@Composable
fun EditMessageDialog(
    state: EditDialogState,
    onDismiss: () -> Unit,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onToggleMarkdown: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑消息") },
        text = {
            Column {
                OutlinedTextField(
                    value = state.newContent,
                    onValueChange = onContentChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("新内容") },
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Markdown")
                    Switch(
                        checked = state.isMarkdown,
                        onCheckedChange = { onToggleMarkdown() }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun getFileIcon(fileName: String): androidx.compose.ui.graphics.vector.ImageVector {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "apk" -> Icons.Rounded.Android
        "pdf" -> Icons.Rounded.PictureAsPdf
        "doc", "docx" -> Icons.Rounded.Description
        "xls", "xlsx" -> Icons.Rounded.TableChart
        "ppt", "pptx" -> Icons.Rounded.Slideshow
        "zip", "rar", "7z", "tar", "gz" -> Icons.Rounded.FolderZip
        "mp3", "wav", "aac", "flac", "ogg" -> Icons.Rounded.AudioFile
        "mp4", "avi", "mkv", "mov", "flv" -> Icons.Rounded.VideoFile
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> Icons.Rounded.Image
        "txt", "md", "json", "xml", "html", "css", "js", "kt", "java" -> Icons.Rounded.Code
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "${size}B"
        size < 1024 * 1024 -> "${size / 1024}KB"
        size < 1024 * 1024 * 1024 -> "${"%.1f".format(size.toFloat() / (1024 * 1024))}MB"
        else -> "${"%.2f".format(size.toFloat() / (1024 * 1024 * 1024))}GB"
    }
}