package com.juhao.murexide.utils

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun Avatar(
    url: String,
    size: Dp = 48.dp
) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
    )
}