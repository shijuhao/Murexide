package com.juhao.murexide

import android.content.Intent
import com.juhao.murexide.ui.settings.SettingsActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.juhao.murexide.datastore.TokenStorage
import com.juhao.murexide.ui.chat.ChatActivity
import com.juhao.murexide.ui.login.LoginActivity
import com.juhao.murexide.ui.contact.ContactListScreen
import com.juhao.murexide.ui.conversation.ConversationListScreen
import com.juhao.murexide.ui.mine.MineScreen
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.chat.ChatScreen
import com.juhao.murexide.ui.chat.ChatViewModel
import com.juhao.murexide.ui.chat.getDeviceId

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
                        MainScreen(token) {
                            lifecycleScope.launch {
                                tokenStorage.clearToken()
                                Toast.makeText(this@MainActivity, "已登出", Toast.LENGTH_SHORT).show()
                                LoginActivity.start(this@MainActivity)
                                finish()
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(token: String, onLogout: () -> Unit) {
    val context = LocalContext.current

    val settingsStorage = remember { SettingsStorage(context) }
    val bigScreenEnabled by settingsStorage.bigScreenFlow.collectAsState(initial = true)

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var currentConversation by remember { mutableStateOf<ConversationItem?>(null) }

    BoxWithConstraints {
        val useNavigationRail = maxWidth >= 600.dp

        Column (
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                if (useNavigationRail && bigScreenEnabled) {
                    MainNavigationRail(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigateToTopLevelRoute(route)
                        }
                    )
                }
                MainNavHost(
                    token = token,
                    onConversationClick = { currentChat ->
                        if (currentChat == null) {
                            currentConversation = null
                        } else {
                            if (useNavigationRail && bigScreenEnabled) {
                                currentConversation = currentChat
                            } else {
                                ChatActivity.start(
                                    context = context,
                                    chatId = currentChat.chatId,
                                    chatType = currentChat.chatType,
                                    chatName = currentChat.displayName,
                                    chatAvatar = currentChat.avatarUrl,
                                )
                            }
                        }
                    },
                    currentConversation = currentConversation,
                    bigScreenMode = useNavigationRail,
                    bigScreenEnabled = bigScreenEnabled,
                    onLogout = onLogout,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    navController = navController
                )
            }
            if (!useNavigationRail || !bigScreenEnabled) {
                MainNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigateToTopLevelRoute(route)
                    }
                )
            }
        }
    }
}

@Composable
private fun MainNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar {
        navItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = {
                    AnimatedVisibility(
                        visible = selected,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(item.title)
                    }
                }
            )
        }
    }
}

@Composable
private fun MainNavigationRail(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationRail {
        Spacer(Modifier.height(16.dp))
        
        navItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = {
                    AnimatedVisibility(
                        visible = selected,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(item.title)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainNavHost(
    modifier: Modifier,
    token: String,
    onConversationClick: (ConversationItem?) -> Unit,
    currentConversation: ConversationItem? = null,
    bigScreenMode: Boolean = false,
    bigScreenEnabled: Boolean = true,
    onLogout: () -> Unit,
    navController: androidx.navigation.NavHostController,
) {
    val context = LocalContext.current
    
    NavHost(
        navController = navController,
        startDestination = "conversations",
        modifier = modifier
    ) {
        composable("conversations") {
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                ConversationListScreen(
                    modifier = Modifier
                        .weight(if (bigScreenMode) 4f else 1f)
                        .fillMaxSize(),
                    token = token,
                    currentConversation = currentConversation,
                    onConversationClick = onConversationClick,
                    bigScreenMode = bigScreenMode
                )
                if (bigScreenMode && bigScreenEnabled) {
                    if (currentConversation != null) {
                        BackHandler {
                            onConversationClick(null)
                        }
                        key(currentConversation!!.chatId) {
                            ChatScreen(
                                modifier = Modifier.weight(7f).fillMaxHeight(),
                                chatAvatar = currentConversation!!.avatarUrl,
                                chatName = currentConversation!!.name,
                                chatType = currentConversation!!.chatType,
                                onBackClick = {
                                    onConversationClick(null)
                                },
                                bigScreenMode = true,
                                viewModel = viewModel(
                                    key = "chat_" + currentConversation!!.chatId,
                                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
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
                            modifier = Modifier.weight(7f).fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ChatBubble,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp).alpha(0.6f)
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
            Scaffold (
                topBar = {
                    TopAppBar(
                        title = {
                            Text("社区")
                        }
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = it.calculateTopPadding(),
                            end = it.calculateRightPadding(LayoutDirection.Ltr)
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("社区", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
        composable("mine") {
            MineScreen(
                token = token,
                onLogout = onLogout,
                onSettingsClick = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }
            )
        }
    }
}

private fun androidx.navigation.NavHostController.navigateToTopLevelRoute(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}