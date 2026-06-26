package com.juhao.murexide.ui.chat

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.datastore.TokenStorage
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.runBlocking

class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val chatId = intent.getStringExtra("chat_id") ?: return finish()
        val chatType = intent.getIntExtra("chat_type", 1)
        val chatName = intent.getStringExtra("chat_name") ?: ""
        val chatAvatar = intent.getStringExtra("chat_avatar") ?: ""

        val tokenStorage = TokenStorage(this)
        val token = runBlocking { tokenStorage.getToken() } ?: return finish()

        setContent {
            MurexideTheme {
                ChatScreen(
                    chatType = chatType,
                    chatName = chatName,
                    chatAvatar = chatAvatar,
                    onBackClick = { finish() },
                    viewModel = viewModel(
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                return ChatViewModel(
                                    token = token,
                                    chatId = chatId,
                                    chatType = chatType,
                                    deviceId = com.juhao.murexide.ui.chat.getDeviceId()
                                ) as T
                            }
                        }
                    )
                )
            }
        }
    }

    companion object {
        fun start(
            context: Context,
            chatId: String,
            chatType: Int,
            chatName: String,
            chatAvatar: String
        ) {
            val intent = android.content.Intent(context, ChatActivity::class.java).apply {
                putExtra("chat_id", chatId)
                putExtra("chat_type", chatType)
                putExtra("chat_name", chatName)
                putExtra("chat_avatar", chatAvatar)
            }
            context.startActivity(intent)
        }
    }
}
