package com.juhao.murexide.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juhao.murexide.data.ExpressionItem
import com.juhao.murexide.data.StickerItem
import com.juhao.murexide.data.StickerPack
import com.juhao.murexide.data.resolveStickerUrl
import kotlinx.coroutines.launch

private const val PANEL_HEIGHT_RATIO = 0.55f

@Composable
fun EmojiPanel(
    expressions: List<ExpressionItem>,
    stickerPacks: List<StickerPack>,
    isLoading: Boolean,
    onExpressionClick: (ExpressionItem) -> Unit,
    onStickerItemClick: (StickerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabTitles = buildList {
        add("收藏")
        stickerPacks.forEach { add(it.name) }
    }

    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth()) {
        // Tab 栏
        SecondaryScrollableTabRow (
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 4.dp,
            divider = { }, // 不显示底部分割线
            modifier = Modifier.fillMaxWidth()
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            text = title,
                            maxLines = 1,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            if (isLoading && page == 0 && expressions.isEmpty() && stickerPacks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (page == 0) {
                ExpressionGridPage(
                    expressions = expressions,
                    onItemClick = onExpressionClick
                )
            } else {
                val packIndex = page - 1
                if (packIndex in stickerPacks.indices) {
                    StickerPackGridPage(
                        items = stickerPacks[packIndex].stickerItems,
                        onItemClick = onStickerItemClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressionGridPage(
    expressions: List<ExpressionItem>,
    onItemClick: (ExpressionItem) -> Unit
) {
    if (expressions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无收藏表情",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        return
    }
    EmojiGrid(
        count = expressions.size,
        contentPadding = PaddingValues(8.dp)
    ) { index ->
        val item = expressions[index]
        EmojiGridItem(
            url = resolveStickerUrl(item.url),
            onClick = { onItemClick(item) }
        )
    }
}

@Composable
private fun StickerPackGridPage(
    items: List<StickerItem>,
    onItemClick: (StickerItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无表情",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        return
    }
    EmojiGrid(
        count = items.size,
        contentPadding = PaddingValues(8.dp)
    ) { index ->
        val item = items[index]
        EmojiGridItem(
            url = resolveStickerUrl(item.url),
            onClick = { onItemClick(item) }
        )
    }
}

/** 通用表情网格：4 列 LazyVerticalGrid */
@Composable
private fun EmojiGrid(
    count: Int,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable (index: Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(count) { index ->
            content(index)
        }
    }
}

@Composable
private fun EmojiGridItem(
    url: String?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .apply {
                if (url?.contains("jwznb.com") == true) {
                    setHeader("Referer", "https://myapp.jwznb.com")
                }
            }
            .crossfade(true)
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = null,
        modifier = Modifier
            .size(80.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick),
        contentScale = ContentScale.Fit
    )
}
