package com.juhao.murexide.ui.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.juhao.murexide.ui.webview.WebViewActivity
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode

object MarkdownRenderer {
    @Composable
    fun Render(
        modifier: Modifier = Modifier,
        content: String,
        onLinkClick: ((String) -> Unit)? = null,
        onImageClick: ((String, String) -> Unit)? = null
    ) {
        val context = LocalContext.current

        val customUriHandler = remember(onLinkClick) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    if (onLinkClick != null) {
                        onLinkClick(uri)
                    } else {
                        val isHttp = uri.startsWith("http://") || uri.startsWith("https://")
                        
                        if (isHttp) {
                            try {
                                val intent = Intent(context, WebViewActivity::class.java).apply {
                                    putExtra(WebViewActivity.EXTRA_URL, uri)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, uri.toUri())
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        } else {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, uri.toUri())
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }

        val components = remember(onImageClick) {
            markdownComponents(
                image = { componentData ->
                    val url = componentData.content
                    val altText = componentData.node.getAltTextFromNode(url)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                onImageClick?.invoke(url, altText)
                            }
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(url)
                                .apply {
                                    if (url.contains("chat-img.jwznb.com") ||
                                        url.contains("jwznb.com") ||
                                        url.contains("myapp.jwznb.com")
                                    ) {
                                        setHeader("Referer", "https://myapp.jwznb.com")
                                    }
                                }
                                .build(),
                            contentDescription = altText.ifEmpty { null },
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            )
        }

        CompositionLocalProvider(LocalUriHandler provides customUriHandler) {
            Markdown(
                content = content,
                modifier = modifier,
                components = components,
                imageTransformer = Coil2ImageTransformerImpl,
                typography = markdownTypography(
                    h1 = MaterialTheme.typography.headlineLarge,
                    h2 = MaterialTheme.typography.headlineMedium,
                    h3 = MaterialTheme.typography.headlineSmall,
                    h4 = MaterialTheme.typography.titleLarge,
                    h5 = MaterialTheme.typography.titleMedium,
                    h6 = MaterialTheme.typography.bodyLarge,
                    text = MaterialTheme.typography.bodyMedium,
                    paragraph = MaterialTheme.typography.bodyMedium,
                    code = MaterialTheme.typography.bodyMedium,
                    inlineCode = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        background = androidx.compose.ui.graphics.Color.LightGray
                    ),
                    quote = MaterialTheme.typography.bodyMedium,
                    ordered = MaterialTheme.typography.bodyLarge,
                    bullet = MaterialTheme.typography.bodyLarge,
                    list = MaterialTheme.typography.bodyMedium,
                    textLink = TextLinkStyles(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    ),
                    table = MaterialTheme.typography.bodyMedium
                )
            )
        }
    }

    private fun org.intellij.markdown.ast.ASTNode.getAltTextFromNode(content: String): String {
        val linkTextNode = findChildOfType(org.intellij.markdown.MarkdownElementTypes.LINK_TEXT)
        if (linkTextNode != null) {
            val text = linkTextNode.getTextInNode(content).toString()
            return text.removeSurrounding("[", "]")
        }
        return ""
    }
}