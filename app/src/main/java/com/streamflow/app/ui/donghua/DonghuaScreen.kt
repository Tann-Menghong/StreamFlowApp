package com.streamflow.app.ui.donghua

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val DONGHUA_URL = "https://www.bilibili.tv/en"
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/126.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DonghuaScreen() {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        webViewRef.value?.takeIf { it.canGoBack() }?.goBack()
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                with(settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    userAgentString = DESKTOP_UA
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    mediaPlaybackRequiresUserGesture = false
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        view.loadUrl(request.url.toString())
                        return true
                    }
                }
                webChromeClient = WebChromeClient()
                webViewRef.value = this
                loadUrl(DONGHUA_URL)
            }
        }
    )
}
