package com.juhao.murexide.ui.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juhao.murexide.utils.Avatar
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ConversationListScreen(
    token: String,
    onConversationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationViewModel = remember { ConversationViewModel(token) }
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is ConversationUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is ConversationUiState.Success -> {
            if (state.conversations.isEmpty()) {
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无会话")
                }
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.conversations, key = { it.chatId }) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            onClick = { onConversationClick(conversation.chatId) }
                        )
                    }
                }
            }
        }
        is ConversationUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败: ${state.message}")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.refresh() }) {
                        Text("重试")
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationItem(
    conversation: com.juhao.murexide.data.ConversationItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(conversation.avatarUrl)
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = formatTime(conversation.timestampMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.chatContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (conversation.hasUnread || conversation.isAtMentioned) {
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Badges(
                            hasUnread = conversation.hasUnread,
                            isAtMentioned = conversation.isAtMentioned
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Badges(hasUnread: Boolean, isAtMentioned: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isAtMentioned) {
            Badge(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) {
                Text("@", style = MaterialTheme.typography.labelSmall)
            }
        }
        
        if (hasUnread) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text("新", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    
    return when {
        diff < 60 * 1000 -> "刚刚"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
        else -> {
            val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
            dateFormat.format(Date(timestampMs))
        }
    }
}
