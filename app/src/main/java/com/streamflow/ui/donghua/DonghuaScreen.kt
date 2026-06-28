package com.streamflow.ui.donghua

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel

private const val SITE_URL = "https://donghuafun.com/"

private fun isVideoStream(url: String): Boolean {
    val lower = url.lowercase()
    if (!lower.startsWith("http")) return false
    // Exclude common non-video resources
    val excluded = listOf(".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".svg",
        ".woff", ".woff2", ".ttf", ".ico", ".json", "analytics", "doubleclick",
        "googlesyndication", "google-analytics", "pagead", "adsbygoogle")
    if (excluded.any { lower.contains(it) }) return false
    // Match HLS / progressive video
    return lower.contains(".m3u8") || lower.contains(".mp4") ||
           lower.contains(".webm") || lower.contains("/hls/") ||
           lower.contains("/stream/") || lower.contains("playlist") && lower.contains("m3u")
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonghuaScreen(onPlayNative: (String) -> Unit, vm: DonghuaViewModel = viewModel()) {
    val detectedUrl by vm.detectedStreamUrl.collectAsState()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var pageTitle by remember { mutableStateOf("Donghua Fun") }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    BackHandler(enabled = canGoBack) { webViewRef?.goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { webViewRef?.goBack() }, enabled = canGoBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    IconButton(onClick = { webViewRef?.reload(); vm.clearDetectedStream() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                    IconButton(onClick = { webViewRef?.loadUrl(SITE_URL); vm.clearDetectedStream() }) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).also { wv ->
                        webViewRef = wv
                        wv.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            mediaPlaybackRequiresUserGesture = false
                            allowContentAccess = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            // Desktop UA helps with some players
                            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/121.0.0.0 Mobile Safari/537.36"
                        }
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(wv, true)
                        }

                        wv.webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                mainHandler.post {
                                    isLoading = true
                                    canGoBack = view.canGoBack()
                                    vm.clearDetectedStream()
                                }
                            }
                            override fun onPageFinished(view: WebView, url: String) {
                                mainHandler.post {
                                    isLoading = false
                                    canGoBack = view.canGoBack()
                                }
                            }
                            // Intercept every network request — detect video streams
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest
                            ): WebResourceResponse? {
                                val url = request.url.toString()
                                if (isVideoStream(url)) {
                                    mainHandler.post { vm.onStreamDetected(url) }
                                }
                                return null // let WebView handle it normally
                            }
                            override fun onReceivedSslError(
                                view: WebView,
                                handler: SslErrorHandler,
                                error: android.net.http.SslError
                            ) {
                                handler.proceed() // accept SSL for video CDNs
                            }
                        }

                        wv.webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView, title: String) {
                                mainHandler.post { pageTitle = title }
                            }
                        }

                        wv.loadUrl(SITE_URL)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Loading bar at top
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            }

            // Stream detected banner at bottom
            AnimatedVisibility(
                visible = detectedUrl != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                detectedUrl?.let { url ->
                    Card(
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Video stream found",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = { onPlayNative(url) }) {
                                Text("Play in App", color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(
                                onClick = { vm.clearDetectedStream() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}
