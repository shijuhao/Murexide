package com.juhao.murexide

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
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
import androidx.window.core.layout.WindowWidthSizeClass
import com.composables.icons.lucide.*
import com.juhao.murexide.datastore.TokenStorage
import com.juhao.murexide.ui.chat.ChatActivity
import com.juhao.murexide.ui.login.LoginActivity
import com.juhao.murexide.ui.conversation.ConversationListScreen
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.launch

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
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val useNavigationRail = adaptiveInfo.windowSizeClass.windowWidthSizeClass
        >= WindowWidthSizeClass.EXPANDED

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            navItems.forEach { item ->
                val isSelected = currentRoute == item.route
                item(
                    icon = { Icon(item.icon, contentDescription = item.title) },
                    label = {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Text(item.title)
                        }
                    },
                    selected = isSelected,
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
        },
        layoutType = if (useNavigationRail) NavigationSuiteType.NavigationRail
                     else NavigationSuiteType.NavigationBar,
        header = {
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
                                Icon(Lucide.Plus, contentDescription = "添加")
                            }
                        }
                        "contacts" -> {
                        
                        }
                        else -> {
                            IconButton(onClick = onLogout) {
                                Icon(Lucide.LogOut, contentDescription = "登出")
                            }
                            IconButton(onClick = { }) {
                                Icon(Lucide.Settings, contentDescription = "设置")
                            }
                        }
                    }
                }
            )
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = "conversations",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("conversations") {
                ConversationListScreen(
                    token = token,
                    onConversationClick = { currentChat ->
                        ChatActivity.start(
                            context = context,
                            chatId = currentChat.chatId,
                            chatType = currentChat.chatType,
                            chatName = currentChat.displayName,
                            chatAvatar = currentChat.avatarUrl,
                        )
                    }
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
}