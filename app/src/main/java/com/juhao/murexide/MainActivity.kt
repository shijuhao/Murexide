package com.juhao.murexide

import android.content.Intent
import com.juhao.mixue.ui.settings.SettingsActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.composables.icons.lucide.*
import com.juhao.murexide.datastore.TokenStorage
import com.juhao.murexide.ui.chat.ChatActivity
import com.juhao.murexide.ui.login.LoginActivity
import com.juhao.murexide.ui.conversation.ConversationListScreen
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

private data class NavItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem("conversations", "消息", Lucide.MessageCircle),
    NavItem("contacts", "联系人", Lucide.Users),
    NavItem("mine", "我的", Lucide.User),
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
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    BoxWithConstraints {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                MainTopAppBar(
                    currentRoute = currentRoute,
                    onLogout = onLogout
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    MainNavigationBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigateToTopLevelRoute(route)
                        }
                    )
                }
            }
        ) {
            if (useNavigationRail) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(it)
                ) {
                    MainNavigationRail(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigateToTopLevelRoute(route)
                        }
                    )
                    MainNavHost(
                        token = token,
                        onConversationClick = { currentChat ->
                            ChatActivity.start(
                                context = context,
                                chatId = currentChat.chatId,
                                chatType = currentChat.chatType,
                                chatName = currentChat.displayName,
                                chatAvatar = currentChat.avatarUrl,
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        navController = navController
                    )
                }
            } else {
                MainNavHost(
                    token = token,
                    onConversationClick = { currentChat ->
                        ChatActivity.start(
                            context = context,
                            chatId = currentChat.chatId,
                            chatType = currentChat.chatType,
                            chatName = currentChat.displayName,
                            chatAvatar = currentChat.avatarUrl,
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(it),
                    navController = navController
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainTopAppBar(
    currentRoute: String?,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current

    TopAppBar(
        title = {
            Text(
                text = when (currentRoute) {
                    "conversations" -> stringResource(R.string.app_name)
                    "contacts" -> "通讯录"
                    else -> "我的"
                }
            )
        },
        actions = {
            when (currentRoute) {
                "conversations" -> {
                    IconButton(onClick = {}) {
                        Icon(Lucide.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = {}) {
                        Icon(Lucide.Plus, contentDescription = "添加")
                    }
                }

                "mine" -> {
                    IconButton(onClick = onLogout) {
                        Icon(Lucide.LogOut, contentDescription = "登出")
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(Lucide.Settings, contentDescription = "设置")
                    }
                }
            }
        }
    )
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

@Composable
private fun MainNavHost(
    token: String,
    onConversationClick: (com.juhao.murexide.data.ConversationItem) -> Unit,
    modifier: Modifier,
    navController: androidx.navigation.NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = "conversations",
        modifier = modifier
    ) {
        composable("conversations") {
            ConversationListScreen(
                token = token,
                onConversationClick = onConversationClick
            )
        }
        composable("contacts") {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("联系人", style = MaterialTheme.typography.headlineMedium)
            }
        }
        composable("mine") {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("我的", style = MaterialTheme.typography.headlineMedium)
            }
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