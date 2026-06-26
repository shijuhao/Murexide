package com.juhao.murexide.ui.chat.components

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.Coil
import androidx.compose.ui.viewinterop.AndroidView
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotBottomSheet(
    messages: List<MessageItem>,
    chatName: String,
    chatAvatar: String,
    onDismiss: () -> Unit,
    onSaveImage: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var screenshotView by remember { mutableStateOf<View?>(null) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    
    val screenshotImageLoader = remember {
        ImageLoader.Builder(context)
            .allowHardware(false)
            .build()
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AndroidView(
                factory = { ctx ->
                    ComposeView(activity!!).apply {
                        setContent {
                            Coil.setImageLoader(screenshotImageLoader)
                            
                            MurexideTheme {
                                ScreenshotContent(
                                    messages = messages,
                                    chatName = chatName,
                                    chatAvatar = chatAvatar
                                )
                            }
                        }
                        screenshotView = this
                    }
                },
                modifier = Modifier.wrapContentSize()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                ScreenshotActionCard(
                    icon = Icons.Rounded.Save,
                    label = "保存图片",
                    onClick = {
                        scope.launch {
                            val view = screenshotView ?: return@launch
                            withContext(Dispatchers.Main) {
                                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bitmap)
                                view.draw(canvas)
                                onSaveImage(bitmap)
                                onDismiss()
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.width(20.dp))
                ScreenshotActionCard(
                    icon = Icons.Rounded.Share,
                    label = "分享",
                    onClick = {
                        scope.launch {
                            val view = screenshotView ?: return@launch
                            withContext(Dispatchers.Main) {
                                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bitmap)
                                view.draw(canvas)
                                saveAndShareBitmap(context, bitmap)
                                onDismiss()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ScreenshotContent(
    messages: List<MessageItem>,
    chatName: String,
    chatAvatar: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部信息
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Avatar(url = chatAvatar, size = 36.dp)
                Spacer(modifier = Modifier.width(8.dp))
                
                Column {
                    Text(
                        text = chatName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
    
            // 消息列表 - 按实际位置计算连体效果
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(12.dp)
                    )
            ) {
                messages.forEachIndexed { index, message ->
                    if (!message.isRecalled && message.contentType != MessageItem.CONTENT_TYPE_TIP) {
                        val newerMessage = if (index > 0) messages[index - 1] else null
                        val olderMessage = if (index < messages.size - 1) messages[index + 1] else null
    
                        val isFirstFromSender = newerMessage == null ||
                                newerMessage.isRecalled ||
                                newerMessage.contentType == MessageItem.CONTENT_TYPE_TIP ||
                                newerMessage.senderId != message.senderId
    
                        val isLastFromSender = olderMessage == null ||
                                olderMessage.isRecalled ||
                                olderMessage.contentType == MessageItem.CONTENT_TYPE_TIP ||
                                olderMessage.senderId != message.senderId
    
                        val isOlderSameSender = olderMessage != null &&
                                !olderMessage.isRecalled &&
                                olderMessage.contentType != MessageItem.CONTENT_TYPE_TIP &&
                                olderMessage.senderId == message.senderId
    
                        val isNewerSameSender = newerMessage != null &&
                                !newerMessage.isRecalled &&
                                newerMessage.contentType != MessageItem.CONTENT_TYPE_TIP &&
                                newerMessage.senderId == message.senderId
    
                        MessageBubble(
                            message = message,
                            isLastFromSender = isLastFromSender,
                            isFirstFromSender = isFirstFromSender,
                            isOlderSameSender = isOlderSameSender,
                            isNewerSameSender = isNewerSameSender,
                            showAvatar = isFirstFromSender
                        )
                    }
                }
            }
    
            // 底部水印
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(0.6f)
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "由 Murexide 生成",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun ScreenshotActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap) =
    withContext(Dispatchers.IO) {
        val filename = "chat_screenshot_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        )
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "截图已保存到相册", Toast.LENGTH_SHORT).show()
            }
        } ?: withContext(Dispatchers.Main) {
            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

private fun saveAndShareBitmap(context: Context, bitmap: Bitmap) {
    kotlinx.coroutines.MainScope().launch {
        withContext(Dispatchers.IO) {
            val filename = "chat_share_${System.currentTimeMillis()}.png"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
            )
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(shareIntent, "分享截图")
                    )
                }
            }
        }
    }
}