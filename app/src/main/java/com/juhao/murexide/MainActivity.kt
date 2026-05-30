package com.juhao.murexide

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.juhao.murexide.datastore.TokenStorage
import com.juhao.murexide.ui.conversation.ConversationListScreen
import com.juhao.murexide.ui.login.LoginScreen
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tokenStorage = TokenStorage(this)
        
        setContent {
            var token by remember { mutableStateOf("") }
            
            lifecycleScope.launch {
                token = tokenStorage.getToken() ?: ""
            }
            
            MurexideTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (token != "") {
                        MainScreen(token) {
                            lifecycleScope.launch {
                                tokenStorage.clearToken()
                                token = ""
                                Toast.makeText(this@MainActivity, "已登出", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        LoginScreen(
                            onLoginSuccess = { successToken ->
                                lifecycleScope.launch {
                                    tokenStorage.saveToken(successToken)
                                    token = successToken
                                    Toast.makeText(this@MainActivity, "登录成功", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(token: String, onLogout: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("会话列表") },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("登出")
                    }
                }
            )
        }
    ) { paddingValues ->
        ConversationListScreen(
            token = token,
            onConversationClick = { chatId ->
                // TODO: 跳转到聊天页面
            },
            modifier = Modifier.padding(paddingValues)
        )
    }
}