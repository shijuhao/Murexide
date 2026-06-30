package com.juhao.murexide

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.juhao.murexide.datastore.TokenStorage
import com.juhao.murexide.ui.chat.ChatActivity
import com.juhao.murexide.ui.contact.ContactListScreen
import com.juhao.murexide.ui.conversation.ConversationListScreen
import com.juhao.murexide.ui.login.LoginActivity
import com.juhao.murexide.ui.mine.MineScreen
import com.juhao.murexide.ui.theme.MurexideTheme
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.ui.chat.ChatScreen
import com.juhao.murexide.ui.chat.ChatViewModel
import com.juhao.murexide.ui.chat.getDeviceId
import com.juhao.murexide.ui.community.CommunityScreen
import com.juhao.murexide.ui.settings.SettingsActivity

private data class NavItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem("conversations", "消息", Icons.Rounded.ChatBubbleOutline),
    NavItem("contacts", "通讯录", Icons.Rounded.Contacts),
    NavItem("community", "社区", Icons.Rounded.People),
    NavItem("mine", "我的", Icons.Rounded.Person),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tokenStorage = TokenStorage(this)

        lifecycleScope.launch {
            val token = tokenStorage.getToken()
            if (token == null) {
                LoginActivity.start(this@MainActivity)
                finish()
                return@launch
            }

            setContent {
                MurexideTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MainScreen(token)
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(token: String) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val settingsStorage = remember { SettingsStorage(context) }
    val bigScreenEnabled by settingsStorage.bigScreenFlow.collectAsState(initial = true)
    
    val isBigScreen = LocalConfiguration.current.screenWidthDp >= 600

    NavigationSuiteScaffold(
        layoutType = if (bigScreenEnabled && isBigScreen) {
            NavigationSuiteType.NavigationRail
        } else {
            NavigationSuiteType.NavigationBar
        },
        navigationSuiteItems = {
            navItems.forEach { item ->
                val selected = currentRoute == item.route
                item(
                    icon = { Icon(item.icon, contentDescription = item.title) },
                    label = {
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Text(item.title)
                        }
                    },
                    selected = selected,
                    onClick = {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = "conversations",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("conversations") {
                var currentConversation by remember { mutableStateOf<ConversationItem?>(null) }

                Row(modifier = Modifier.fillMaxSize()) {
                    ConversationListScreen(
                        modifier = Modifier
                            .weight(if (isBigScreen && bigScreenEnabled) 0.4f else 1f)
                            .fillMaxHeight(),
                        token = token,
                        currentConversation = if (isBigScreen && bigScreenEnabled) currentConversation else null,
                        onConversationClick = { conversation ->
                            if (isBigScreen && bigScreenEnabled) {
                                currentConversation = conversation
                            } else {
                                ChatActivity.start(
                                    context = context,
                                    chatId = conversation.chatId,
                                    chatType = conversation.chatType,
                                    chatName = conversation.displayName,
                                    chatAvatar = conversation.avatarUrl,
                                )
                            }
                        }
                    )

                    if (isBigScreen && bigScreenEnabled) {
                        if (currentConversation != null) {
                            BackHandler {
                                currentConversation = null
                            }
                            key(currentConversation!!.chatId) {
                                ChatScreen(
                                    modifier = Modifier
                                        .weight(0.7f)
                                        .fillMaxHeight(),
                                    chatAvatar = currentConversation!!.avatarUrl,
                                    chatName = currentConversation!!.name,
                                    chatType = currentConversation!!.chatType,
                                    onBackClick = { currentConversation = null },
                                    bigScreenMode = true,
                                    viewModel = viewModel(
                                        key = "chat_" + currentConversation!!.chatId,
                                        factory = object : ViewModelProvider.Factory {
                                            @Suppress("UNCHECKED_CAST")
                                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                                return ChatViewModel(
                                                    token = token,
                                                    chatId = currentConversation!!.chatId,
                                                    chatType = currentConversation!!.chatType,
                                                    deviceId = getDeviceId()
                                                ) as T
                                            }
                                        }
                                    )
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.weight(0.7f).fillMaxHeight(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ChatBubble,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp).alpha(0.6f)
                                )
                            }
                        }
                    }
                }
            }

            composable("contacts") {
                ContactListScreen(
                    token = token,
                    onContactClick = { contact ->
                        ChatActivity.start(
                            context = context,
                            chatId = contact.chatId,
                            chatType = contact.chatType,
                            chatName = contact.remark ?: contact.name,
                            chatAvatar = contact.avatarUrl
                        )
                    }
                )
            }

            composable("community") {
                CommunityScreen(
                    token = token
                )
            }

            composable("mine") {
                MineScreen(
                    token = token,
                    onSettingsClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }
                )
            }
        }
    }
}