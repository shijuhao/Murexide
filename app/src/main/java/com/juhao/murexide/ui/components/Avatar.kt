package com.juhao.murexide.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juhao.murexide.datastore.SettingsStorage

private var cachedSquareAvatar: Boolean? = null

@Composable
fun Avatar(
    url: String,
    size: Dp = 48.dp
) {
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    var squareAvatar by remember { mutableStateOf(cachedSquareAvatar) }

    LaunchedEffect(Unit) {
        cachedSquareAvatar = settingsStorage.getSquareAvatar()
        squareAvatar = cachedSquareAvatar
    }

    val shape = if (squareAvatar == true) {
        RoundedCornerShape(size / 5)
    } else {
        CircleShape
    }

    val imageRequest = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .apply {
                if (url.contains("chat-img.jwznb.com") ||
                    url.contains("jwznb.com") ||
                    url.contains("myapp.jwznb.com")
                ) {
                    setHeader("Referer", "https://myapp.jwznb.com")
                }
            }
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .clip(shape),
        contentScale = ContentScale.Crop
    )
}