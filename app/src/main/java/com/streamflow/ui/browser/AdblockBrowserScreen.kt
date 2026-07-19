package com.streamflow.ui.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// ── Ad-blocking (shared by the Donghua and Drama tabs) ───────────────────────
private val AD_DOMAINS = setOf(
    "doubleclick.net", "googlesyndication.com", "googletagmanager.com",
    "googletagservices.com", "google-analytics.com", "googleadservices.com",
    "adservice.google.com", "analytics.google.com", "pagead2.googlesyndication.com",
    "adwords.google.com", "googletag.com",
    "adnxs.com", "adsrvr.org", "adform.net", "rubiconproject.com",
    "pubmatic.com", "openx.net", "moatads.com", "scorecardresearch.com",
    "amazon-adsystem.com", "media.net", "criteo.com", "taboola.com",
    "outbrain.com", "revcontent.com", "mgid.com", "ads.yahoo.com",
    "advertising.com", "casalemedia.com", "turn.com",
    "smartadserver.com", "bidswitch.net", "contextweb.com", "spotxchange.com",
    "lijit.com", "appnexus.com", "33across.com", "triplelift.com",
    "sharethrough.com", "yieldmo.com", "sovrn.com", "indexexchange.com",
    "trafficjunky.net", "exoclick.com", "propellerads.com", "popcash.net",
    "clksite.com", "popads.net", "adsterra.com", "hilltopads.net",
    "clickadu.com", "yllix.com", "juicyads.com", "trafficstars.com",
    "plugrush.com", "ero-advertising.com", "tsyndicate.com",
    "adspyglass.com", "adskeeper.com", "evadav.com",
    "hotjar.com", "mixpanel.com", "segment.io", "segment.com",
    "fullstory.com", "mouseflow.com", "clarity.ms", "quantserve.com",
    "chartbeat.com", "parsely.com", "optimizely.com", "ab.tasty.com",
    "connect.facebook.net"
)

private val AD_URL_PATTERNS = listOf(
    "/ads/", "/ad/", "/advert", "banner_ad", "pop-ad", "popup_ad",
    "/pagead/", "/adframe", "ad_slot", "adsense", "/serve/",
    "adclick", "clickthrough", "impression", "/track/", "/pixel/",
    "prebid", "bidder", "openrtb", "vast.xml", "vpaid"
)

private fun isAdRequest(url: String): Boolean {
    val lower = url.lowercase()
    return AD_DOMAINS.any { lower.contains(it) } || AD_URL_PATTERNS.any { lower.contains(it) }
}

// A fresh response per request: shouldInterceptRequest runs on multiple WebView
// threads, and a single shared WebResourceResponse (one InputStream instance)
// handed to concurrent requests is not thread-safe
private fun emptyResponse() = WebResourceResponse("text/plain", "utf-8", "".byteInputStream())

private val AD_BLOCK_JS = """
(function(){
  var noop = function(){};
  var noopObj = new Proxy({}, { get: function(){ return noop; } });
  ['adsbygoogle','googletag','ga','_gaq','dataLayer','pbjs','apntag',
   '__cmp','__tcfapi','__uspapi','ExoLoader','ExoJSPlayerAPI',
   'PopAds','popns','adsbytrafficjunky'].forEach(function(k){
    try{ if(!window[k]) Object.defineProperty(window,k,{get:function(){return noopObj},set:noop}); }catch(e){}
  });
  window.open = noop;
  var _si = window.setInterval, _st = window.setTimeout;
  window.setInterval = function(fn, t){ try{ var s=String(fn); if(s.indexOf('ad')>-1||s.indexOf('pop')>-1) return 0; }catch(e){} return _si(fn,t); };
  window.setTimeout  = function(fn, t){ try{ var s=String(fn); if(s.indexOf('pop')>-1) return 0; }catch(e){} return _st(fn,t); };
  var css = [
    '[class*="ad-"],[class*="-ad"],[id*="ad-"],[id*="-ad"]',
    '.ads,.advertisement,.adsbygoogle,.ad-banner,.ad-slot,.ad-unit',
    'iframe[src*="ad"],iframe[src*="doubleclick"],iframe[src*="pagead"]',
    '[class*="popup"],[class*="overlay"],[id*="overlay"],[class*="modal-ad"]',
    '.gdpr-overlay,.cookie-consent-overlay,.consent-banner',
    '[class*="interstitial"],[class*="splash-ad"]',
    'ins.adsbygoogle'
  ].join(',');
  var s = document.createElement('style');
  s.textContent = css + '{display:none!important;visibility:hidden!important;opacity:0!important;pointer-events:none!important}';
  (document.head||document.documentElement).appendChild(s);
  var blocked = /doubleclick|googlesyndication|adsbygoogle|exoclick|propellerads|popcash|popads/i;
  new MutationObserver(function(muts){
    muts.forEach(function(m){
      m.addedNodes.forEach(function(n){
        if(n.nodeType!==1) return;
        var src = n.src||n.getAttribute&&n.getAttribute('src')||'';
        if(blocked.test(src)||blocked.test(n.outerHTML||'')){
          try{ n.parentNode&&n.parentNode.removeChild(n); }catch(e){}
        }
      });
    });
  }).observe(document.documentElement,{childList:true,subtree:true});
})();
""".trimIndent()

/**
 * Ad-blocked in-app browser tab pinned to one streaming site. Used by the
 * Donghua (donghuafun.com), Drama (kisskh.co) and PDTV (pdtvhd.com)
 * bottom-bar tabs.
 *
 * [prefsName] keys the per-site "last page" persistence so each tab reopens
 * where the user left off.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdblockBrowserScreen(
    homeUrl: String,
    prefsName: String,
    defaultTitle: String,
    onFullscreenChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val sitePrefs = remember { context.getSharedPreferences(prefsName, Context.MODE_PRIVATE) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var pageTitle by remember { mutableStateOf(defaultTitle) }
    var customView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Reset orientation when leaving the tab
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Back: exit fullscreen first, then go back in WebView
    BackHandler(enabled = canGoBack && customView == null) { webViewRef?.goBack() }
    BackHandler(enabled = customView != null) { customViewCallback?.onCustomViewHidden() }

    fun buildWebView(ctx: Context): WebView = WebView(ctx).also { wv ->
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = true
            // Pinch-zoom as a fallback for anything that still overflows,
            // without the legacy on-screen +/- buttons
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // MOBILE user agent: the old desktop UA made these sites serve
            // their desktop layout, which never fit the phone screen
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
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
                }
                view.evaluateJavascript("javascript:(function(){$AD_BLOCK_JS})()", null)
            }
            override fun onPageFinished(view: WebView, url: String) {
                mainHandler.post {
                    isLoading = false
                    canGoBack = view.canGoBack()
                    if (url.startsWith("http")) {
                        sitePrefs.edit().putString("last_url", url).apply()
                    }
                }
                view.evaluateJavascript("javascript:(function(){$AD_BLOCK_JS})()", null)
            }
            // Popunder/redirect blocking: streaming sites love navigating the
            // whole page to an ad URL on the first tap — swallow those instead
            // of letting them replace the site
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = isAdRequest(request.url.toString())
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                if (isAdRequest(url)) return emptyResponse()
                return null
            }
            // Never blanket-accept bad certificates: proceed() here silently
            // disabled TLS for the whole tab (classic MITM hole, and a Play
            // Store rejection reason). Cancel loads the failing resource not
            // at all — legitimate sites have valid certs.
            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: android.net.http.SslError
            ) { handler.cancel() }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(
                view: android.view.View,
                callback: CustomViewCallback
            ) {
                mainHandler.post {
                    customView = view
                    customViewCallback = callback
                    onFullscreenChange(true)
                    activity?.let { act ->
                        act.requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        WindowCompat.getInsetsController(
                            act.window, act.window.decorView
                        ).apply {
                            hide(WindowInsetsCompat.Type.systemBars())
                            systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                        act.window.addFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        )
                    }
                }
            }

            override fun onHideCustomView() {
                mainHandler.post {
                    customViewCallback = null
                    customView = null
                    onFullscreenChange(false)
                    activity?.let { act ->
                        act.requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        WindowCompat.getInsetsController(
                            act.window, act.window.decorView
                        ).show(WindowInsetsCompat.Type.systemBars())
                        act.window.clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        )
                    }
                }
            }

            override fun onReceivedTitle(view: WebView, title: String) {
                mainHandler.post { pageTitle = title }
            }
        }

        wv.loadUrl(sitePrefs.getString("last_url", homeUrl) ?: homeUrl)
    }

    // ── Fullscreen video overlay ──────────────────────────────────────────────
    if (customView != null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // key(): AndroidView's factory only runs once per slot, so when the
            // site swaps in a NEW fullscreen view (next episode) the old one kept
            // showing — keying forces a fresh AndroidView for each custom view
            key(customView) {
                AndroidView(
                    factory = { customView!! },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        return
    }

    // ── Normal WebView layout ────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { webViewRef?.goBack() }, enabled = canGoBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reload")
                    }
                    IconButton(onClick = { webViewRef?.loadUrl(homeUrl) }) {
                        Icon(Icons.Rounded.Home, contentDescription = "Home")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                // Reuse the live WebView when coming back from fullscreen —
                // creating a fresh one here reloaded the page (episode restarted)
                // while the old instance could keep playing audio underneath
                factory = { ctx -> webViewRef ?: buildWebView(ctx).also { webViewRef = it } },
                onRelease = { wv ->
                    // Entering fullscreen also releases this slot — the same
                    // WebView renders the fullscreen video, so only tear it down
                    // when the user actually leaves the tab. Without this the
                    // site's audio kept playing after switching tabs.
                    if (customView == null) {
                        wv.loadUrl("about:blank")
                        wv.destroy()
                        if (webViewRef == wv) webViewRef = null
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            }
        }
    }
}
