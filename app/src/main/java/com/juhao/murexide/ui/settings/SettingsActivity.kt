package com.juhao.murexide.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.juhao.murexide.datastore.TokenStorage
import com.juhao.murexide.ui.login.LoginActivity
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tokenStorage = TokenStorage(this)

        setContent {
            MurexideTheme {
                SettingsScreen(
                    onBack = { finish() },
                    onLogout = {
                        lifecycleScope.launch {
                            tokenStorage.clearToken()
                            Toast.makeText(this@SettingsActivity, "已登出", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@SettingsActivity, LoginActivity::class.java)
                            intent.flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    }
                )
            }
        }
    }
}