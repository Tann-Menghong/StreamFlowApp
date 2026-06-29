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

// ── Ad-blocking ───────────────────────────────────────────────────────────────
private val AD_DOMAINS = setOf(
    // Google ads
    "doubleclick.net", "googlesyndication.com", "googletagmanager.com",
    "googletagservices.com", "google-analytics.com", "googleadservices.com",
    "adservice.google.com", "analytics.google.com", "pagead2.googlesyndication.com",
    "adwords.google.com", "googletag.com",
    // Programmatic / exchanges
    "adnxs.com", "adsrvr.org", "adform.net", "rubiconproject.com",
    "pubmatic.com", "openx.net", "moatads.com", "scorecardresearch.com",
    "amazon-adsystem.com", "media.net", "criteo.com", "taboola.com",
    "outbrain.com", "revcontent.com", "mgid.com", "ads.yahoo.com",
    "advertising.com", "casalemedia.com", "turn.com",
    "smartadserver.com", "bidswitch.net", "contextweb.com", "spotxchange.com",
    "lijit.com", "appnexus.com", "33across.com", "triplelift.com",
    "sharethrough.com", "yieldmo.com", "sovrn.com", "indexexchange.com",
    // Pop/push/redirect ad networks
    "trafficjunky.net", "exoclick.com", "propellerads.com", "popcash.net",
    "clksite.com", "popads.net", "adsterra.com", "hilltopads.net",
    "clickadu.com", "yllix.com", "juicyads.com", "trafficstars.com",
    "plugrush.com", "ero-advertising.com", "tsyndicate.com",
    "adspyglass.com", "adskeeper.com", "mgid.com", "evadav.com",
    // Trackers & analytics
    "hotjar.com", "mixpanel.com", "segment.io", "segment.com",
    "fullstory.com", "mouseflow.com", "clarity.ms", "quantserve.com",
    "chartbeat.com", "parsely.com", "optimizely.com", "ab.tasty.com",
    "facebook.com/tr", "connect.facebook.net"
)

private val AD_URL_PATTERNS = listOf(
    "/ads/", "/ad/", "/advert", "banner_ad", "pop-ad", "popup_ad",
    "/pagead/", "/adframe", "ad_slot", "adsense", "/serve/",
    "adclick", "clickthrough", "impression", "/track/", "/pixel/",
    "prebid", "bidder", "openrtb", "vast.xml", "vpaid"
)

private fun isAdRequest(url: String): Boolean {
    val lower = url.lowercase()
    if (AD_DOMAINS.any { lower.contains(it) }) return true
    if (AD_URL_PATTERNS.any { lower.contains(it) }) return true
    return false
}

private val EMPTY_RESPONSE = WebResourceResponse("text/plain", "utf-8", "".byteInputStream())

// Brave-style scriptlet + CSS injection — kills ad containers, pop-ups, and overlays
private val AD_BLOCK_JS = """
(function(){
  // 1. Noop known ad globals before they run
  var noop = function(){};
  var noopObj = new Proxy({}, { get: function(){ return noop; } });
  ['adsbygoogle','googletag','ga','_gaq','dataLayer','pbjs','apntag',
   '__cmp','__tcfapi','__uspapi','ExoLoader','ExoJSPlayerAPI',
   'PopAds','popns','adsbytrafficjunky'].forEach(function(k){
    try{ if(!window[k]) Object.defineProperty(window,k,{get:function(){return noopObj},set:noop}); }catch(e){}
  });

  // 2. Block window.open (pop-unders)
  window.open = noop;

  // 3. Kill setInterval/setTimeout used by ad overlays
  var _si = window.setInterval, _st = window.setTimeout;
  window.setInterval = function(fn, t){ try{ var s=String(fn); if(s.indexOf('ad')>-1||s.indexOf('pop')>-1) return 0; }catch(e){} return _si(fn,t); };
  window.setTimeout  = function(fn, t){ try{ var s=String(fn); if(s.indexOf('pop')>-1) return 0; }catch(e){} return _st(fn,t); };

  // 4. CSS — hide ad containers
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

  // 5. MutationObserver — remove injected ad nodes dynamically
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

// ── Stream detection ─────────────────────────────────────────────────────────
private fun isVideoStream(url: String): Boolean {
    val lower = url.lowercase()
    if (!lower.startsWith("http")) return false
    val excluded = listOf(".js", ".css", ".png", ".jpg", ".jpeg", ".gif",
        ".svg", ".woff", ".woff2", ".ttf", ".ico", "analytics", "doubleclick",
        "googlesyndication", "pagead", "adsbygoogle")
    if (excluded.any { lower.contains(it) }) return false
    return lower.contains(".m3u8") || lower.contains(".mp4") ||
           lower.contains(".webm") || lower.contains("/hls/") ||
           lower.contains("/stream/") ||
           (lower.contains("playlist") && lower.contains("m3u"))
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
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
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
                                // Early injection — noop ad globals before page scripts run
                                view.evaluateJavascript("javascript:(function(){$AD_BLOCK_JS})()", null)
                            }
                            override fun onPageFinished(view: WebView, url: String) {
                                mainHandler.post {
                                    isLoading = false
                                    canGoBack = view.canGoBack()
                                }
                                // Re-inject after page fully loads (catches late-injected ads)
                                view.evaluateJavascript("javascript:(function(){$AD_BLOCK_JS})()", null)
                            }
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest
                            ): WebResourceResponse? {
                                val url = request.url.toString()
                                // Block ads
                                if (isAdRequest(url)) return EMPTY_RESPONSE
                                // Detect video stream
                                if (isVideoStream(url)) {
                                    mainHandler.post { vm.onStreamDetected(url) }
                                }
                                return null
                            }
                            override fun onReceivedSslError(
                                view: WebView,
                                handler: SslErrorHandler,
                                error: android.net.http.SslError
                            ) { handler.proceed() }
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

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            }

            AnimatedVisibility(
                visible = detectedUrl != null,
                enter = slideInVertically { it },
                exit  = slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                detectedUrl?.let { url ->
                    Card(
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlayCircle, null,
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
                                Icon(Icons.Default.Close, null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }
        }
    }
}
