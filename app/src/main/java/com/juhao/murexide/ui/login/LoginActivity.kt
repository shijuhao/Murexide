package com.juhao.murexide.ui.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.juhao.murexide.datastore.TokenStorage
import com.juhao.murexide.MainActivity
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tokenStorage = TokenStorage(this)

        setContent {
            var showTokenDialog by remember { mutableStateOf(false) }

            MurexideTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LoginScreen(
                        onLoginSuccess = { token ->
                            lifecycleScope.launch {
                                tokenStorage.saveToken(token)
                                Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                finish()
                            }
                        },
                        onTokenLogin = {
                            showTokenDialog = true
                        }
                    )

                    if (showTokenDialog) {
                        TokenInputDialog(
                            onDismiss = { showTokenDialog = false },
                            onConfirm = { token ->
                                showTokenDialog = false
                                lifecycleScope.launch {
                                    tokenStorage.saveToken(token)
                                    Toast.makeText(this@LoginActivity, "Token登录成功", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                    finish()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, LoginActivity::class.java))
        }
    }
}

@Composable
fun TokenInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.Key,
                contentDescription = null
            )
        },
        title = { Text("Token 登录") },
        text = {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                placeholder = { Text("请输入 Token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(token) },
                enabled = token.isNotBlank()
            ) {
                Text("登录")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}