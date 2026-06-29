package com.juhao.murexide.ui.contact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.juhao.murexide.data.ContactItem
import com.juhao.murexide.ui.components.Avatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    token: String,
    onContactClick: (ContactItem) -> Unit,
    viewModel: ContactViewModel = remember { ContactViewModel(token) }
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val uiState by viewModel.uiState.collectAsState()

    val groupStates = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(uiState.contactGroups) {
        uiState.contactGroups.forEach { group ->
            if (!groupStates.containsKey(group.groupName)) {
                groupStates[group.groupName] = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
                title = { Text("通讯录") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateRightPadding(LayoutDirection.Ltr)
                ),
        ) {
            if (uiState.isLoading && uiState.contactGroups.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null && uiState.contactGroups.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = uiState.error ?: "未知错误", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.loadContacts() }) {
                        Text("重试")
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    uiState.contactGroups.forEach { group ->
                        val isExpanded = groupStates[group.groupName] ?: false
                        
                        item {
                            ContactGroupHeader(
                                name = group.groupName,
                                isExpanded = isExpanded,
                                onToggle = {
                                    groupStates[group.groupName] = !isExpanded
                                }
                            )
                        }
                        
                        if (isExpanded) {
                            items(group.contacts) { contact ->
                                ContactItemRow(contact = contact, onClick = { onContactClick(contact) })
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 72.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactGroupHeader(
    name: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (isExpanded) "折叠" else "展开",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ContactItemRow(
    contact: ContactItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(url = contact.avatarUrl, size = 40.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = contact.remark ?: contact.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "ID: ${contact.chatId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}