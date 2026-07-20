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

// ── Ad-blocking (shared by the Donghua, Drama and MKissa tabs) ───────────────
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
    "connect.facebook.net",
    // Streaming-site popunder / redirect / push-ad networks (the big offenders
    // on donghua/drama/mkissa-style sites — these are what open ad tabs on tap)
    "propu.sh", "propellerads.net", "poptm.com", "popmyads.com", "popunder.net",
    "onclickalgo.com", "onclckads.com", "onclickmax.com", "onclickpredictiv.com",
    "onclickperformance.com", "clickadilla.com", "adcash.com", "cpmstar.com",
    "admaven.com", "ad-maven.com", "admaven.pro", "galaksion.com", "adnium.com",
    "revenuehits.com", "coinzilla.com", "cointraffic.io", "a-ads.com",
    "bebi.com", "mgid.com", "clicksgear.com", "adplusplus.fr", "adbutler.com",
    "servedbyadbutler.com", "pushpad.xyz", "pushwhy.com", "richpush.co",
    // Web-push notification-ad networks (the ones that spam the notification bar)
    "onesignal.com", "os.tc", "wonderpush.com", "pushengage.com", "pushnami.com",
    "webpushs.com", "pushpushgo.com", "notix.co", "notix.io", "sendpulse.com",
    "subscribers.com", "izooto.com", "truepush.com", "foxpush.net", "cleverpush.com",
    "vibrantmedia.com", "adthrive.com", "mediavine.com", "ezoic.net",
    "histats.com", "luckyorange.com", "luckyorange.net", "statcounter.com",
    "yandex.ru", "mc.yandex.ru", "vidoomy.com", "admixer.net",
    // Common in-page crypto-miners bundled with pirate-stream ads
    "coinhive.com", "coin-hive.com", "jsecoin.com", "cryptoloot.pro",
    "webminepool.com", "crypto-loot.com", "minero.cc"
)

private val AD_URL_PATTERNS = listOf(
    "/ads/", "/ad/", "/advert", "banner_ad", "pop-ad", "popup_ad",
    "/pagead/", "/adframe", "ad_slot", "adsense", "/serve/",
    "adclick", "clickthrough", "impression", "/track/", "/pixel/",
    "prebid", "bidder", "openrtb", "vast.xml", "vpaid",
    "popunder", "pop-under", "/popup", "/pop.js", "/pop.php",
    "/aclk", "/adx/", "syndication", "notification-ad", "push-ad",
    "/sw-ad", "adblock-detect", "/interstitial",
    // Web-push / service-worker ad plumbing
    "onesignalsdk", "onesignal", "wonderpush", "pushengage", "pushnami",
    "webpush", "web-push", "/push.js", "pushsdk", "push-sdk", "/push/subscribe",
    "service-worker-ad", "sw-push", "/notification.js", "notifications.js"
)

// Infra hosts that main-frame navigation may legitimately reach (captcha /
// CDN challenge pages) even though they differ from the site's own domain.
private val INFRA_ALLOW = setOf(
    "google.com", "gstatic.com", "recaptcha.net", "cloudflare.com",
    "challenges.cloudflare.com", "hcaptcha.com"
)

// internal: also reused by the PlayerScreen direct-stream WebView so ad defense
// is identical everywhere the app renders web content.
internal fun isAdRequest(url: String): Boolean {
    val lower = url.lowercase()
    return AD_DOMAINS.any { lower.contains(it) } || AD_URL_PATTERNS.any { lower.contains(it) }
}

// Titles ad scripts set on document.title to fake a notification in the tab bar.
private val AD_TITLE_RE = Regex(
    """\(\d+\)\s*new\s*message|you.?ve\s+won|new\s+message\s*!|premium\s+account|claim\s+your|congratulation""",
    RegexOption.IGNORE_CASE
)

/** Registrable domain ("www.kisskh.co" -> "kisskh.co") for same-site checks. */
private fun baseDomainOf(url: String): String =
    runCatching { android.net.Uri.parse(url).host ?: "" }.getOrDefault("")
        .split('.').filter { it.isNotEmpty() }.takeLast(2).joinToString(".")

// A fresh response per request: shouldInterceptRequest runs on multiple WebView
// threads, and a single shared WebResourceResponse (one InputStream instance)
// handed to concurrent requests is not thread-safe
internal fun emptyResponse() = WebResourceResponse("text/plain", "utf-8", "".byteInputStream())

private val AD_BLOCK_JS = """
(function(){
  // ── Desktop mode ─────────────────────────────────────────────────────────
  // Paired with the desktop Chrome UA, force a desktop-width viewport so these
  // sites lay out as their (far-less-ad-infested) desktop version but still fit
  // the phone screen via useWideViewPort + loadWithOverviewMode scaling.
  try{
    var mv=document.querySelector('meta[name="viewport"]');
    if(!mv){ mv=document.createElement('meta'); mv.name='viewport'; (document.head||document.documentElement).appendChild(mv); }
    mv.setAttribute('content','width=1024');
  }catch(e){}
  var noop = function(){};
  var noopObj = new Proxy({}, { get: function(){ return noop; } });
  ['adsbygoogle','googletag','ga','_gaq','dataLayer','pbjs','apntag',
   '__cmp','__tcfapi','__uspapi','ExoLoader','ExoJSPlayerAPI',
   'PopAds','popns','adsbytrafficjunky'].forEach(function(k){
    try{ if(!window[k]) Object.defineProperty(window,k,{get:function(){return noopObj},set:noop}); }catch(e){}
  });
  // Kill popups/popunders at the source. window.open is the #1 ad vector on
  // these sites; blackhole it (and the rarer showModalDialog).
  try{ window.open = function(){ return null; }; }catch(e){}
  try{ window.showModalDialog = noop; }catch(e){}
  // ── Timer-sniffing (restored from the old "Brave-level" build) ────────────
  // Ad/pop scriptlets — including the fake-notification overlay — schedule
  // themselves via setTimeout/setInterval. Refuse any timer whose function
  // source carries pop / window.open / obfuscation / ad-network / scam markers.
  // Targeted tokens (NOT a bare 'ad'/'pop') so the video player's own timers
  // (buffer/append/load) survive; the players run in iframes this main-frame
  // override never reaches anyway. This was only removed because PDTV used to be
  // a WebView — it's a native ExoPlayer screen now, so it's safe to bring back.
  try{
    var _si=window.setInterval, _st=window.setTimeout;
    var AD_FN=/popunder|popup|popmagic|popns|window\.open|adsby|exoloader|onclick(algo|max|ads)|nativead|showad|_0x[0-9a-f]{4}|atob\(|premium account|new message|congratulation|claim your|you.?ve won|activate.{0,14}account/i;
    window.setInterval=function(fn){ try{ if(AD_FN.test(String(fn))) return 0; }catch(e){} return _si.apply(window, arguments); };
    window.setTimeout =function(fn){ try{ if(AD_FN.test(String(fn))) return 0; }catch(e){} return _st.apply(window, arguments); };
  }catch(e){}
  // ── Web-push / notification ADS ──────────────────────────────────────────
  // These sites hook you into a push-ad network (via a background service
  // worker) that then spams NOTIFICATION ads even after you leave the page.
  // Deny the Notification API, block service-worker registration, tear down
  // any worker already installed, and kill PushManager. None of this affects
  // video playback (players don't use push).
  try{
    var denyPerm = function(cb){ try{ if(typeof cb==='function') cb('denied'); }catch(e){} return Promise.resolve('denied'); };
    if(window.Notification){
      try{ Object.defineProperty(window,'Notification',{value:function(){ return {close:noop,onclick:null,addEventListener:noop,removeEventListener:noop}; },writable:false,configurable:false}); }catch(e){}
      try{ window.Notification.permission='denied'; }catch(e){}
      try{ window.Notification.requestPermission=denyPerm; }catch(e){}
    }
  }catch(e){}
  try{
    if(navigator.serviceWorker){
      try{ navigator.serviceWorker.register=function(){ return Promise.reject(new Error('blocked')); }; }catch(e){}
      try{ Object.getPrototypeOf(navigator.serviceWorker).register=function(){ return Promise.reject(new Error('blocked')); }; }catch(e){}
      try{ navigator.serviceWorker.getRegistrations().then(function(rs){ (rs||[]).forEach(function(r){ try{r.unregister();}catch(e){} }); }); }catch(e){}
    }
  }catch(e){}
  try{ if(window.PushManager){ delete window.PushManager; } }catch(e){}
  try{ if('PushManager' in window){ window.PushManager=undefined; } }catch(e){}
  // The registrable domain of THIS page — anything pointing elsewhere is off-site.
  var BASE = (function(){ var p=location.hostname.split('.'); return p.slice(-2).join('.'); })();
  // Popunder pattern: a tap on the player/page opens an ad in a new tab or
  // redirects the whole page to an ad domain. Capture clicks before the site's
  // own handlers run and cancel any anchor that targets a NEW window or an
  // OFF-SITE URL. Same-site links (the site's own navigation, play buttons)
  // pass through untouched, so the player is never affected.
  document.addEventListener('click', function(e){
    try{
      var a = e.target && e.target.closest ? e.target.closest('a') : null;
      if(!a) return;
      if(a.target && a.target !== '_self' && a.target !== '_top' && a.target !== '_parent'){
        a.target = '_self';
      }
      var href = a.getAttribute('href') || '';
      if(/^https?:/i.test(href)){
        var host = '';
        try{ host = a.hostname || ''; }catch(_){}
        if(host && BASE && host.indexOf(BASE) === -1){
          e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation();
        }
      } else if(/^(intent:|market:|tg:|whatsapp:)/i.test(href)){
        e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation();
      }
    }catch(err){}
  }, true);
  // NOTE: no setInterval/setTimeout source-sniffing here. Substring checks for
  // 'ad'/'pop' false-positive on ordinary code ('load', 'padding', queue.pop())
  // and silently killed main-frame players (broke Clappr/hls.js on pdtvhd.com).
  var css = [
    '[class*="ad-"],[class*="-ad"],[id*="ad-"],[id*="-ad"]',
    '.ads,.advertisement,.adsbygoogle,.ad-banner,.ad-slot,.ad-unit,.ad-container,.ad-wrapper',
    'iframe[src*="ads"],iframe[src*="doubleclick"],iframe[src*="pagead"],iframe[src*="popunder"]',
    // Ad-specific overlays only — a bare [class*="overlay"] also hides many
    // video players own control overlays, so keep these tightly ad-scoped.
    '[class*="popup-ad"],[class*="ad-popup"],[class*="popunder"],[class*="ads-overlay"]',
    '[class*="ad-overlay"],[class*="adoverlay"],[class*="modal-ad"],[id*="ad-modal"]',
    '.gdpr-overlay,.cookie-consent-overlay,.consent-banner',
    '[class*="interstitial"],[class*="splash-ad"],[class*="banner-ad"]',
    'ins.adsbygoogle'
  ].join(',');
  var s = document.createElement('style');
  s.textContent = css + '{display:none!important;visibility:hidden!important;opacity:0!important;pointer-events:none!important}';
  (document.head||document.documentElement).appendChild(s);
  var blocked = /doubleclick|googlesyndication|adsbygoogle|exoclick|propellerads|popcash|popads|popunder|adsterra|hilltopads|clickadu|juicyads|adcash|admaven|onclick(algo|max|ads|predictiv)|galaksion|revenuehits|coinhive|cryptoloot/i;
  // ── In-page overlay / fake-dialog ADS ────────────────────────────────────
  // The nastiest ones inject a DOM element styled as a "New message / your
  // premium account is activated" popup whose Accept/Cancel lead off-site.
  // Kill any FLOATING element that carries scam text or an off-site link — the
  // video player is explicitly protected so playback is never touched.
  var AD_TXT = /(premium|vip)\s*account.{0,40}activ|follow the instruction on the next page|you.{0,3}ve\s+won|claim your (prize|reward|gift|account)|congratulation.{0,30}(won|winner|selected|prize)|your (device|phone).{0,24}(infected|at risk|virus)|activate.{0,20}premium|download.{0,20}official app|new message.{0,40}(account|proceed|instruction)/i;
  // Ad hosts we recognise in an iframe src even when the iframe is cross-origin
  // (its DOM can't be read, but the iframe ELEMENT can still be removed).
  var AD_HOST = /doubleclick|googlesyndication|adservice|adsystem|adnxs|exoclick|propellerads|propu\.sh|popads|popcash|popunder|adsterra|hilltopads|clickadu|juicyads|adcash|admaven|galaksion|revenuehits|onclick(algo|max|ads|predictiv)|mgid|outbrain|taboola|trafficjunky|onesignal|wonderpush|pushengage|pushnami|clickadilla|a-ads|coinzilla|bidgear|adprovider/i;
  function adHasOffsite(el){
    try{
      var as = el.querySelectorAll ? el.querySelectorAll('a[href]') : [];
      for(var i=0;i<as.length;i++){ var h=''; try{ h=as[i].hostname||''; }catch(e){}
        if(h && BASE && h.indexOf(BASE)===-1) return true; }
    }catch(e){} return false;
  }
  function adIsPlayer(el){
    try{ return !!(el.querySelector && el.querySelector('video,[class*="player" i],[id*="player" i],[class*="jwplayer" i],[class*="clappr" i],[class*="plyr" i]')); }catch(e){ return false; }
  }
  function adKill(n){
    try{ n.style.setProperty('display','none','important'); }catch(e){}
    try{ n.parentNode&&n.parentNode.removeChild(n); }catch(e){}
  }
  function adScan(n){
    if(!n||n.nodeType!==1) return;
    // 1) An ad iframe (by src) — remove it whether or not we can read inside it.
    try{ if(n.tagName==='IFRAME'){ var isrc=n.src||n.getAttribute('src')||''; if(isrc && AD_HOST.test(isrc)){ adKill(n); return; } } }catch(e){}
    var s; try{ s=getComputedStyle(n); }catch(e){ return; } if(!s) return;
    var pos=s.position, z=parseInt(s.zIndex,10)||0;
    if(pos!=='fixed' && !(pos==='absolute' && z>=50)) return;
    if(adIsPlayer(n)) return;                       // never remove the player
    // 2) Scam-text overlay (the fake "premium account activated" dialog).
    var txt=''; try{ txt=(n.innerText||n.textContent||''); }catch(e){}
    if(AD_TXT.test(txt)){ adKill(n); return; }
    // 3) Floating box that links off-site (Accept -> ad site).
    if((pos==='fixed'||z>=100) && adHasOffsite(n)){ adKill(n); return; }
    // 4) A floating box that WRAPS an ad iframe.
    try{ var ifr=n.querySelector&&n.querySelector('iframe'); if(ifr){ var s2=ifr.src||''; if(s2 && AD_HOST.test(s2)){ adKill(n); return; } } }catch(e){}
    // 5) A screen-covering fixed overlay that carries any ad signal (off-site
    //    link or an iframe). Gated on a signal so the player is never removed.
    try{
      if(pos==='fixed'){
        var r=n.getBoundingClientRect();
        var vw=window.innerWidth||document.documentElement.clientWidth;
        var vh=window.innerHeight||document.documentElement.clientHeight;
        if(r.width>=vw*0.9 && r.height>=vh*0.6 && (adHasOffsite(n) || (n.querySelector&&n.querySelector('iframe')))){ adKill(n); return; }
      }
    }catch(e){}
  }
  function adSweep(){
    try{
      var sel='div,ins,aside,section,a,dialog,center,table,iframe';
      var els=document.body?document.body.querySelectorAll(sel):[];
      for(var i=0;i<els.length;i++) adScan(els[i]);
      // Also every direct child of body/html regardless of tag (custom elements,
      // <dialog>, ad frames appended straight to the root).
      var roots=[document.body,document.documentElement];
      for(var r=0;r<roots.length;r++){ if(!roots[r]) continue; var ch=roots[r].children;
        for(var j=0;j<ch.length;j++) adScan(ch[j]); }
    }catch(e){}
  }
  // These ads inject on a delay and re-inject, so sweep immediately, a few more
  // times, then keep watch on a short interval.
  [0,200,500,1000,1800,2800,4200,6000,9000].forEach(function(t){ setTimeout(adSweep,t); });
  setInterval(adSweep, 2000);
  new MutationObserver(function(muts){
    muts.forEach(function(m){
      m.addedNodes.forEach(function(n){
        if(n.nodeType!==1) return;
        // Judge the node by its OWN attributes only. Testing outerHTML matched
        // descendants too, so a legitimate container with one ad snippet
        // inside got removed wholesale (blanked whole sections of some sites).
        var own = (n.src||'')+' '+(n.id||'')+' '+(n.className||'');
        if(blocked.test(own)){
          try{ n.parentNode&&n.parentNode.removeChild(n); }catch(e){}
          return;
        }
        adScan(n);                                  // catch injected overlays too
      });
    });
  }).observe(document.documentElement,{childList:true,subtree:true});
})();
""".trimIndent()

/**
 * Ad-blocked in-app browser tab pinned to one streaming site. Used by the
 * Donghua (donghuafun.com), Drama (kisskh.co) and MKissa (mkissa.to) tabs.
 * (PDTV got its own native player — see ui/pdtv/PdTvScreen.)
 *
 * Ad defense is layered so nothing legitimate breaks: (1) network blocklist of
 * ad/tracker/miner hosts + URL patterns, (2) window.open / target=_blank /
 * onCreateWindow all refused so no popup tab can spawn, (3) a capture-phase
 * click guard that cancels off-site and app-open anchor taps (the popunder
 * pattern) while same-site navigation and the player pass through, (4) a
 * gesture-scoped cross-domain main-frame block for tap-triggered redirects,
 * (5) web-push / NOTIFICATION-ad defense: the Notification API is denied,
 * service-worker registration is blocked and existing workers are unregistered
 * (JS), device-permission + geolocation prompts are auto-denied, and any
 * already-installed push-ad worker's requests are dropped via ServiceWorkerClient.
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

    val siteBase = remember(homeUrl) { baseDomainOf(homeUrl) }

    fun buildWebView(ctx: Context): WebView = WebView(ctx).also { wv ->
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = true
            // No geolocation — ad frames use it to fingerprint; video needs none.
            setGeolocationEnabled(false)
            // Refuse pop-up windows outright: with this off, window.open and
            // target=_blank cannot spawn the ad tabs these sites rely on. The
            // video always plays inline, so nothing legitimate needs a new window.
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            // Pinch-zoom to read the desktop layout on a phone, without the
            // legacy on-screen +/- buttons. Combined with useWideViewPort +
            // loadWithOverviewMode (above) the desktop page scales to fit width.
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // DESKTOP mode (user request: "show in Brave, desktop mode"). Desktop
            // Chrome UA makes these sites serve their desktop site, which carries
            // far fewer of the aggressive mobile popunder/notification ads; the
            // AD_BLOCK_JS also forces a desktop-width viewport so it fits the screen.
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }

        // Network-level backstop against push/notification ADS delivered by a
        // service worker. A push-ad worker keeps fetching and firing notification
        // ads even with no tab open; the JS blocks NEW registrations, and this
        // starves any worker already installed on a previous visit by dropping
        // its ad requests (API 24+). Global controller — safe to set repeatedly.
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            try {
                android.webkit.ServiceWorkerController.getInstance().setServiceWorkerClient(
                    object : android.webkit.ServiceWorkerClient() {
                        override fun shouldInterceptRequest(
                            request: WebResourceRequest
                        ): WebResourceResponse? =
                            if (isAdRequest(request.url.toString())) emptyResponse() else null
                    }
                )
            } catch (_: Exception) {}
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
            // of letting them replace the site.
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                if (isAdRequest(request.url.toString())) return true
                if (request.isForMainFrame) {
                    val scheme = request.url.scheme?.lowercase()
                    // intent://, market://, tg:// … = "open another app" ad links.
                    if (scheme != null && scheme != "http" && scheme != "https") return true
                    // Block ONLY user-tap-triggered jumps to another site — that's
                    // the popunder/redirect ad. Gesture-less redirects (a site
                    // moving itself to a new mirror domain on load) are allowed
                    // through so the tab doesn't dead-end.
                    if (request.hasGesture()) {
                        val host = request.url.host?.lowercase().orEmpty()
                        if (host.isNotEmpty() && siteBase.isNotEmpty() &&
                            !host.endsWith(siteBase) &&
                            INFRA_ALLOW.none { host.endsWith(it) }
                        ) return true
                    }
                }
                return false
            }
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                // Never blank the main document — only sub-resources get filtered,
                // so a slipped-through ad URL can't turn the whole tab white.
                if (request.isForMainFrame) return null
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
            // Deny every request to spawn a new window. Combined with
            // setSupportMultipleWindows(false) this is the hard backstop for
            // popunders that slip past the JS guard — the site cannot open an
            // ad tab because the host simply refuses to create one.
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message
            ): Boolean = false

            // Deny every device-permission prompt (mic, camera, protected media,
            // and geolocation below). Ad frames request these to fingerprint or
            // to re-enable push-notification ads; the site's video needs none.
            override fun onPermissionRequest(request: PermissionRequest) {
                mainHandler.post { request.deny() }
            }
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) { callback.invoke(origin, false, false) }

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
                // Ads hijack document.title to fake a notification ("(1) New
                // Message!", "You won!"). Ignore those and keep the real title.
                if (AD_TITLE_RE.containsMatchIn(title)) return
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
