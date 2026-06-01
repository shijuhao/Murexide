package com.juhao.murexide.ui.chat.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.composables.icons.lucide.*
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.ui.chat.EditDialogState
import com.juhao.murexide.ui.components.Avatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: MessageItem,
    onRecall: () -> Unit,
    onEdit: () -> Unit,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onReply: () -> Unit,
    isAdmin: Boolean = false,
    isLastFromSender: Boolean = true,
    isFirstFromSender: Boolean = true,
    isOlderSameSender: Boolean = false,
    isNewerSameSender: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    val isMine = message.isMine
    val context = LocalContext.current

    val timestampDisplay = remember(message.timestamp) {
        try {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            sdf.format(Date(message.timestamp))
        } catch (_: Exception) {
            ""
        }
    }

    if (message.isRecalled) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 250.dp)
            ) {
                Text(
                    text = "${message.senderName} 撤回了一条消息",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { },
                    onLongClick = { showMenu = true }
                )
                .padding(
                    start = 8.dp,
                    end = 8.dp,
                    top = if (isOlderSameSender) 0.dp else 4.dp,
                    bottom = if (isNewerSameSender) 0.dp else 4.dp
                ),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
        ) {
            if (!isMine && isFirstFromSender) {
                Avatar(
                    url = message.senderAvatar,
                    size = 36.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else if (!isMine) {
                Spacer(modifier = Modifier.width(44.dp))
            }

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
                            containerColor = if (isMine)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else
                                MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            if (!isMine && isLastFromSender) {
                                Text(
                                    text = message.senderName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 2.dp)
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
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (message.content.isNotBlank()) {
                                Text(
                                    text = message.content,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            message.imageUrl?.let { url ->
                                val builder = ImageRequest.Builder(context)
                                    .data(url)
                                
                                if (url.contains("chat-img.jwznb.com") || 
                                    url.contains("jwznb.com") || 
                                    url.contains("myapp.jwznb.com")) {
                                    builder.setHeader("Referer", "https://myapp.jwznb.com")
                                }
                            
                                Spacer(modifier = Modifier.height(2.dp))
                                AsyncImage(
                                    model = builder.build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { }
                                )
                            }

                            Row(
                                modifier = Modifier.align(if (isMine) Alignment.End else Alignment.Start)
                            ) {
                                Text(
                                    text = timestampDisplay,
                                    fontSize = 10.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                if (message.isEdited) {
                                    Text(
                                        text = "已编辑",
                                        fontSize = 10.sp,
                                        lineHeight = 16.sp,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
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
                                clipboardManager.setText(AnnotatedString(message.content))
                                Toast.makeText(context, "复制成功", Toast.LENGTH_SHORT).show()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Lucide.Copy,
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
                            Icon(Lucide.MessageSquareQuote, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                Icon(Lucide.Undo2, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                    Lucide.Pencil,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }

            if (isMine && isFirstFromSender) {
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
