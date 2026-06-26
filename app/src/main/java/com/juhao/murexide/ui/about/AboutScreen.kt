package com.juhao.murexide.ui.about

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.juhao.murexide.R
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.utils.getAppVersionInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            // 应用信息
            SettingsGroup {
                CustomItemCell {
                    Image(
                        painter = painterResource(R.drawable.app_icon),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "一个云湖第三方客户端",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SettingsItemCell(
                    icon = Icons.Rounded.Info,
                    title = "版本号",
                    subtitle = context.getAppVersionInfo().versionName,
                    onClick = { /* 不执行任何操作 */ }
                )
            }

            // 开发者信息
            SettingsGroup(title = "开发者信息") {
                SettingsItem(
                    icon = Icons.Rounded.Person,
                    title = "开发者",
                    subtitle = "JuHao",
                    onClick = { /* 不执行任何操作 */ }
                )
                
                SettingsItem(
                    icon = Icons.Rounded.Code,
                    title = "编程语言",
                    subtitle = "Kotlin, Jetpack Compose",
                    onClick = { /* 不执行任何操作 */ }
                )
            }

            // 版权信息
            SettingsGroup(title = "许可证") {
                SettingsItemCell(
                    icon = Icons.Rounded.Book,
                    title = "许可证",
                    subtitle = "GNU General Public License v3.0",
                    onClick = { /* 不执行任何操作 */ }
                )
            }

            // 链接信息
            SettingsGroup(title = "相关链接") {
                SettingsItem(
                    icon = Icons.Rounded.Code,
                    title = "GitHub",
                    subtitle = "访问项目源码",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/shijuhao/Murexide".toUri()))
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}