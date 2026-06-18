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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MessageInput(
    inputText: String,
    isMarkdown: Boolean,
    isSending: Boolean = false,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAddImageClick: () -> Unit,
    onToggleMarkdown: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .navigationBarsPadding()
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
                            text = { Text("Markdown") },
                            onClick = {
                                showMenu = false
                                onToggleMarkdown()
                            },
                            leadingIcon = {
                                Text(
                                    text = "M",
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            trailingIcon = {
                                if (isMarkdown) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "已开启",
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
                    modifier = Modifier.weight(1f),
                    enabled = !isSending,
                    placeholder = { Text("输入消息...") },
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 5
                )
                
                Spacer(modifier = Modifier.width(5.dp))
                
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.Mood,
                        contentDescription = "表情"
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
                            modifier = Modifier.size(36.dp)
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