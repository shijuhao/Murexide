package com.juhao.murexide.ui.about

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.juhao.murexide.ui.theme.MurexideTheme

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MurexideTheme {
                AboutScreen(
                    onBack = { onBackPressedDispatcher.onBackPressed() }
                )
            }
        }
    }
}