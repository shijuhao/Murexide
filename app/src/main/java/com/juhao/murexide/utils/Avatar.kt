package com.juhao.murexide.utils

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun Avatar(
    url: String,
    size: Dp = 48.dp
) {
    val context = LocalContext.current
    
    val builder = ImageRequest.Builder(context)
        .data(url)
        
    if (url.contains("chat-img.jwznb.com") || 
        url.contains("jwznb.com") || 
        url.contains("myapp.jwznb.com")) {
        builder.setHeader("Referer", "https://myapp.jwznb.com")
    }

    AsyncImage(
        model = builder.build(),
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
        contentScale = ContentScale.Crop
    )
}