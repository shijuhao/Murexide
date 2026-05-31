package com.juhao.murexide.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.*

@Composable
fun MessageInput(
    inputText: String,
    selectedImages: List<String>,
    isMarkdown: Boolean,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAddImageClick: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onToggleMarkdown: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(
            topStart = 12.dp,
            topEnd = 12.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .navigationBarsPadding()
        ) {
            if (selectedImages.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(selectedImages) { index, imageUri ->
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            AsyncImage(
                                model = imageUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { onRemoveImage(index) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(20.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    Lucide.X,
                                    contentDescription = "移除",
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onAddImageClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Lucide.Image,
                        contentDescription = "添加图片",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onToggleMarkdown,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isMarkdown)
                            MaterialTheme.colorScheme.primary
                        else
                            Color.Transparent,
                        contentColor = if (isMarkdown)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = "M",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(5.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息...") },
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.width(5.dp))

                IconButton(
                    onClick = onSendClick,
                    modifier = Modifier.size(40.dp),
                    enabled = inputText.isNotBlank() || selectedImages.isNotEmpty()
                ) {
                    Icon(
                        Lucide.SendHorizontal,
                        contentDescription = "发送"
                    )
                }
            }
        }
    }
}
