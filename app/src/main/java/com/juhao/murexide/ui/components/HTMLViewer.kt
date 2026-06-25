package com.juhao.murexide.ui.components

import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun UnifiedHtmlWebView(
    htmlContent: String,
    modifier: Modifier = Modifier,
    onImageClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val codeBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    
    val styledHtml = remember(htmlContent, backgroundColor, textColor, linkColor, codeBackgroundColor, isDarkTheme) {
        generateStyledHtml(htmlContent, backgroundColor, textColor, linkColor, codeBackgroundColor, isDarkTheme)
    }
    
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    webViewRef.value?.onPause()
                    webViewRef.value?.pauseTimers()
                }
                Lifecycle.Event.ON_RESUME -> {
                    webViewRef.value?.onResume()
                    webViewRef.value?.resumeTimers()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    webViewRef.value?.let {
                        it.loadUrl("about:blank")
                        it.destroy()
                    }
                    webViewRef = null
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewRef.value?.let {
                it.loadUrl("about:blank")
                it.destroy()
            }
        }
    }
    
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            WebView(ctx).apply {
                setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)
                
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        if (url == null) return true
                        
                        if (url.startsWith("image://")) {
                            val imageUrl = url.removePrefix("image://")
                            onImageClick?.invoke(imageUrl)
                            return true
                        }
                        
                        if (url.startsWith("yunhu://")) {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            return true
                        }
                        
                        try {
                            val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                "https://$url"
                            } else {
                                url
                            }
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(finalUrl))
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        return true
                    }
                    
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                        
                        if (url.startsWith("https://chat-img.jwznb.com")) {
                            try {
                                val connection = URL(url).openConnection() as HttpURLConnection
                                connection.setRequestProperty("Referer", "https://myapp.jwznb.com")
                                connection.connect()
                                val contentType = connection.contentType
                                val encoding = connection.contentEncoding ?: "UTF-8"
                                val inputStream = connection.inputStream
                                return WebResourceResponse(contentType, encoding, inputStream)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                    
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(
                            """
                            window.ImageClickHandler = {
                                onImageClick: function(url) {
                                    window.location.href = 'image://' + url;
                                }
                            };
                            """.trimIndent(),
                            null
                        )
                    }
                }
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportZoom(true)
                    builtInZoomControls = false
                    displayZoomControls = false
                    cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                    loadsImagesAutomatically = true
                    blockNetworkImage = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        offscreenPreRaster = false
                    }
                }
                setBackgroundColor(backgroundColor)
                
                if (onImageClick != null) {
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onImageClick(imageUrl: String) {
                            onImageClick(imageUrl)
                        }
                    }, "ImageClickHandler")
                }
                
                webViewRef = this
            }
        },
        update = { webView ->
            val lastLoadedHtml = webView.getTag(android.R.id.content) as? String
            if (lastLoadedHtml != styledHtml) {
                webView.setTag(android.R.id.content, styledHtml)
                val encoded = android.util.Base64.encodeToString(
                    styledHtml.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_PADDING
                )
                webView.loadData(encoded, "text/html; charset=utf-8", "base64")
            }
        }
    )
}

private fun generateStyledHtml(
    htmlContent: String,
    backgroundColor: Int,
    textColor: Int,
    linkColor: Int,
    codeBackgroundColor: Int,
    isDark: Boolean
): String {
    val bgHex = String.format("#%06X", backgroundColor and 0xFFFFFF)
    val textHex = String.format("#%06X", textColor and 0xFFFFFF)
    val linkHex = String.format("#%06X", linkColor and 0xFFFFFF)
    val codeBgHex = String.format("#%06X", codeBackgroundColor and 0xFFFFFF)
    
    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>
            * {
                margin: 0;
                padding: 0;
                box-sizing: border-box;
            }
            body {
                background-color: $bgHex;
                color: $textHex;
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                font-size: 16px;
                line-height: 1.6;
                padding: 12px 16px;
                word-wrap: break-word;
                overflow-wrap: break-word;
            }
            p, div, span, h1, h2, h3, h4, h5, h6, li, blockquote {
                color: $textHex !important;
            }
            a {
                color: $linkHex !important;
                text-decoration: none;
                word-break: break-all;
            }
            a:hover, a:active {
                text-decoration: underline;
            }
            img {
                max-width: 100%;
                height: auto;
                display: block;
                margin: 8px 0;
                border-radius: 8px;
                cursor: pointer;
                background-color: ${if (isDark) "#2a2a2a" else "#f0f0f0"};
            }
            pre {
                background-color: $codeBgHex !important;
                color: $textHex !important;
                padding: 12px 16px;
                border-radius: 8px;
                overflow-x: auto;
                font-family: 'Courier New', monospace;
                font-size: 14px;
                line-height: 1.5;
                margin: 8px 0;
                border: 1px solid ${if (isDark) "#3a3a3a" else "#e0e0e0"};
            }
            code {
                background-color: $codeBgHex !important;
                color: $textHex !important;
                padding: 2px 6px;
                border-radius: 4px;
                font-family: 'Courier New', monospace;
                font-size: 0.9em;
            }
            pre code {
                background-color: transparent !important;
                padding: 0;
            }
            blockquote {
                border-left: 4px solid $linkHex;
                margin: 8px 0;
                padding: 8px 12px;
                background-color: ${if (isDark) "#2a2a2a" else "#f5f5f5"};
                border-radius: 0 4px 4px 0;
                color: $textHex !important;
            }
            table {
                border-collapse: collapse;
                width: 100%;
                margin: 8px 0;
                font-size: 14px;
            }
            th, td {
                border: 1px solid ${if (isDark) "#3a3a3a" else "#e0e0e0"};
                padding: 8px 12px;
                text-align: left;
                color: $textHex !important;
            }
            th {
                background-color: $codeBgHex !important;
                font-weight: 600;
            }
            ul, ol {
                padding-left: 24px;
                margin: 4px 0;
            }
            li {
                margin: 2px 0;
            }
            h1, h2, h3, h4, h5, h6 {
                margin: 16px 0 8px 0;
                font-weight: 600;
                line-height: 1.3;
                color: $textHex !important;
            }
            h1 { font-size: 24px; }
            h2 { font-size: 20px; }
            h3 { font-size: 18px; }
            h4 { font-size: 16px; }
            h5, h6 { font-size: 14px; }
            hr {
                border: none;
                border-top: 1px solid ${if (isDark) "#3a3a3a" else "#e0e0e0"};
                margin: 12px 0;
            }
            ::selection {
                background-color: ${if (isDark) "#4a4a4a" else "#cce0ff"};
                color: $textHex;
            }
        </style>
        <script>
            (function() {
                document.addEventListener('click', function(e) {
                    var target = e.target;
                    if (target.tagName === 'IMG' && target.src) {
                        e.preventDefault();
                        e.stopPropagation();
                        try {
                            var imgSrc = target.src;
                            if (window.ImageClickHandler && typeof window.ImageClickHandler.onImageClick === 'function') {
                                window.ImageClickHandler.onImageClick(imgSrc);
                            }
                        } catch (err) {
                            console.error('Image click error:', err);
                        }
                    }
                }, true);
            })();
        </script>
    </head>
    <body>
        $htmlContent
    </body>
    </html>
    """.trimIndent()
}