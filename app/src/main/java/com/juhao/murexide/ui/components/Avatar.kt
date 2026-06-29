package com.juhao.murexide.ui.components

import androidx.compose.foundation.clickable
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
import com.juhao.murexide.ui.components.MultiImageViewer
import com.juhao.murexide.ui.theme.ThemeState

@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    url: String,
    size: Dp = 48.dp,
    canView: Boolean = false
) {
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    
    val squareAvatar by ThemeState.squareAvatar

    LaunchedEffect(Unit) {
        ThemeState.squareAvatar.value = settingsStorage.getSquareAvatar()
    }
    
    var viewerVisible by remember { mutableStateOf(false) }

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
        modifier = modifier
            .size(size)
            .clip(
                if (squareAvatar == true) {
                    RoundedCornerShape(size / 5)
                } else {
                    CircleShape
                }
            )
            .then(
                if (canView) 
                    Modifier.clickable { viewerVisible = true }
                else Modifier
            ),
        contentScale = ContentScale.Crop
    )
    
    if (viewerVisible) {
        MultiImageViewer(
            images = listOf(url),
            initialPage = 1,
            isVisible = true,
            onDismiss = { viewerVisible = false }
        )
    }
}