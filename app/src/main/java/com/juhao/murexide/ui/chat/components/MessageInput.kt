package com.juhao.murexide.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.juhao.murexide.R

@Composable
fun MessageInput(
    inputText: String,
    sendType: String,
    isSending: Boolean = false,
    bigScreenMode: Boolean = false,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAddImageClick: () -> Unit,
    onAddVideoClick: () -> Unit,
    onAddFileClick: () -> Unit,
    onToggleSendType: (String) -> Unit,
    requestFocus: Boolean = false,
    onFocusConsumed: () -> Unit = {},
    isEmojiPanelVisible: Boolean = false,
    onEmojiClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocusConsumed()
        }
    }
    
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .then(
                    if (!bigScreenMode)
                        Modifier.navigationBarsPadding()
                    else
                        Modifier
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("图片") },
                            onClick = {
                                showMenu = false
                                onAddImageClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Image, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("视频") },
                            onClick = {
                                showMenu = false
                                onAddVideoClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Movie, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("文件") },
                            onClick = {
                                showMenu = false
                                onAddFileClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.AttachFile, contentDescription = null)
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        
                        DropdownMenuItem(
                            text = { Text("文本") },
                            onClick = {
                                showMenu = false
                                onToggleSendType("text")
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.TextFields, contentDescription = null)
                            },
                            trailingIcon = {
                                if (sendType == "text") {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "已选择",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Markdown") },
                            onClick = {
                                showMenu = false
                                onToggleSendType("markdown")
                            },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.markdown), contentDescription = null)
                            },
                            trailingIcon = {
                                if (sendType == "markdown") {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "已选择",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("HTML") },
                            onClick = {
                                showMenu = false
                                onToggleSendType("html")
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Code, contentDescription = null)
                            },
                            trailingIcon = {
                                if (sendType == "html") {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "已选择",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(5.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder = { Text("输入消息...") },
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.width(5.dp))

                IconButton(
                    onClick = onEmojiClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isEmojiPanelVisible) {
                            Icons.Rounded.Keyboard
                        } else {
                            Icons.Rounded.Mood
                        },
                        contentDescription = if (isEmojiPanelVisible) "键盘" else "表情"
                    )
                }

                AnimatedVisibility(
                    visible = inputText.isNotBlank(),
                    enter = expandHorizontally(
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(150)),
                    exit = shrinkHorizontally(
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(150))
                ) {
                    Row {
                        Spacer(modifier = Modifier.width(5.dp))
                        IconButton(
                            onClick = onSendClick,
                            enabled = !isSending,
                            modifier = Modifier
                                .size(36.dp)
                                .focusProperties { canFocus = false }
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "发送"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}