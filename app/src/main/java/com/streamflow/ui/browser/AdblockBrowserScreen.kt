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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    "webminepool.com", "crypto-loot.com", "minero.cc",
    // More popunder / redirect / interstitial networks seen on pirate-stream
    // mirrors — these are the ones that keep opening ad tabs and full-page redirects
    "hilltopads.com", "adnntwrk.com", "adexchangeprebid.com", "adkernel.com",
    "adtng.com", "adtng.net", "syndication.exdynsrv.com", "exdynsrv.com",
    "go.pub2srv.com", "pubads.g.doubleclick.net", "servedby-buysellads.com",
    "buysellads.com", "highperformanceformat.com", "displaycontentnetwork.com",
    "brainlyads.com", "admatterra.com", "adsbnativ.com", "asoftwareadvice.com",
    "monetag.com", "clickaine.com", "adpaths.com", "runnative.com", "adnimation.com",
    // Monetag / PropellerAds delivery hosts + common streaming-mirror ad CDNs
    "vignette.wiki", "zeusadx.com", "zeus-adx.com", "dsp.adfarm1.adition.com",
    "waframedia5.com", "wpadmngr.com", "cdn.adsafeprotected.com", "adsafeprotected.com",
    "onclickclear.com", "onclicksuper.com", "onclickperformancetrack.com",
    "gotrackier.com", "trackwilltrk.com", "prpops.com", "pushground.com",
    "adservetx.media.net", "smartyads.com", "adop.cc", "bidgear.com",
    "toponad.com", "unityads.unity3d.com", "applovin.com", "startapp.com",
    "chumsradar.com", "chumsads.com", "mm9nu.com", "luckypushh.com", "pushvibe.com",
    "notifica.click", "pushzilla.co", "getpushads.com", "push-notifications.io",
    "adventurefeeds.com", "smartnativeads.com", "popup.click", "popmonetize.com"
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
    "service-worker-ad", "sw-push", "/notification.js", "notifications.js",
    // More ad-serving path markers seen on pirate-stream mirrors. Kept specific
    // (ad*/pop*/redirector query shapes) so they can't match legitimate slugs.
    "/adserver", "/adserve", "/adservice", "/adman", "/adhandler", "/adengine",
    "popunder.js", "/pu.php", "/click.php", "/redirect.php?", "/go.php?u=",
    "/smartpop", "/native-ad", "/adtag"
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

// How long after a page starts loading a gesture-less off-site navigation is
// still treated as a legitimate mirror redirect rather than an ad hijack.
private const val REDIRECT_GRACE_MS = 3500L

// A fresh response per request: shouldInterceptRequest runs on multiple WebView
// threads, and a single shared WebResourceResponse (one InputStream instance)
// handed to concurrent requests is not thread-safe
internal fun emptyResponse() = WebResourceResponse("text/plain", "utf-8", "".byteInputStream())

private val AD_BLOCK_JS = """
(function(){
  // This script is injected into EVERY frame (main page + the cross-origin
  // player iframe). Some defences must be gentler inside a sub-frame so they
  // can't break the video player itself — see AD_FN below.
  var IS_TOP = (function(){ try{ return window.top === window; }catch(e){ return false; } })();
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
  // Exit traps: ad scripts hook beforeunload so leaving the page throws a
  // "Are you sure you want to leave?" dialog (and a second ad on confirm).
  try{
    window.onbeforeunload = null;
    var _ael = window.addEventListener;
    window.addEventListener = function(t){
      if(String(t).toLowerCase() === 'beforeunload') return;
      return _ael.apply(window, arguments);
    };
  }catch(e){}
  // document.write ad injection: many popunder loaders write a whole
  // <script src="//adnetwork/..."> or ad <iframe> straight into the document.
  try{
    var AD_WRITE=/adsbygoogle|googlesyndication|doubleclick|popunder|popads|propu|exoclick|adsterra|hilltopads|onclick(algo|max|ads)|juicyads|trafficjunky|adcash|admaven|monetag|notix|onesignal|<script[^>]+(ads?|pop)[^>]*>/i;
    var _dw=document.write, _dwl=document.writeln;
    document.write=function(s){ try{ if(AD_WRITE.test(String(s))) return; }catch(e){} return _dw.apply(document, arguments); };
    document.writeln=function(s){ try{ if(AD_WRITE.test(String(s))) return; }catch(e){} return _dwl.apply(document, arguments); };
  }catch(e){}
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
    // FRAME-AWARE: this script now runs inside the player IFRAME too, and video
    // players are minified/obfuscated — `_0x1a2b` and `atob(` appear all over
    // legitimate player code. Applying those two tokens inside a sub-frame would
    // kill the player's own buffer/append timers (the v4.4.1 PDTV regression).
    // So: aggressive token set in the top frame only, unambiguous pop/ad tokens
    // everywhere (they never appear in real player code).
    var AD_FN = IS_TOP
      ? /popunder|popup|popmagic|popns|window\.open|adsby|exoloader|onclick(algo|max|ads)|nativead|showad|_0x[0-9a-f]{4}|atob\(|premium account|new message|congratulation|claim your|you.?ve won|activate.{0,14}account/i
      : /popunder|popmagic|popns|window\.open|adsby|exoloader|onclick(algo|max|ads)|nativead|showad|premium account|new message|congratulation|claim your|you.?ve won|activate.{0,14}account/i;
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
    // Named floating-promo widgets (incl. Chinese-template names). Kept specific
    // so they can't match ordinary layout classes like "float-left".
    '[class*="hongbao"],[class*="fudai"],[class*="float-ad"],[class*="ad-float"]',
    '[class*="gift-float"],[class*="float-gift"],[class*="floatgift"],[id*="hongbao"]',
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
  var AD_TXT = /(premium|vip)\s*account.{0,40}activ|follow the instruction|next page to proceed|you.{0,3}ve\s+won|claim your (prize|reward|gift|account)|congratulation.{0,30}(won|winner|selected|prize)|your (device|phone).{0,24}(infected|at risk|virus)|activate.{0,20}premium|download.{0,20}official app|new message.{0,40}(account|proceed|instruction)|your (account|subscription).{0,24}(activated|upgraded|is ready)/i;
  // Ad hosts we recognise in an iframe src even when the iframe is cross-origin
  // (its DOM can't be read, but the iframe ELEMENT can still be removed).
  var AD_HOST = /doubleclick|googlesyndication|adservice|adsystem|adnxs|exoclick|propellerads|propu\.sh|popads|popcash|popunder|adsterra|hilltopads|clickadu|juicyads|adcash|admaven|galaksion|revenuehits|onclick(algo|max|ads|predictiv)|mgid|outbrain|taboola|trafficjunky|onesignal|wonderpush|pushengage|pushnami|clickadilla|a-ads|coinzilla|bidgear|adprovider/i;
  // Promo-widget naming used by these templates (incl. Chinese sites: hongbao =
  // red packet, fuli = welfare/perks, fudai = lucky bag).
  var AD_WIDGET=/gift|hongbao|red.?bag|red.?pack|fudai|fuli|welfare|lucky.?(draw|box|bag)|coupon|prize|float.?(ad|gift|btn)|ad.?float|activity.?(box|icon|float)|xuanfu|suspend.?(ad|box)/i;
  // A tiny "x / close" control — legit floating buttons (scroll-to-top) have none.
  function adHasClose(el){
    try{
      var k=el.querySelectorAll('*');
      for(var i=0;i<k.length && i<80;i++){
        var t=(k[i].textContent||'').trim();
        if(t==='×'||t==='✕'||t==='✖'||t==='❌'||t==='x'||t==='X'){ return true; }
        var a=((k[i].className&&typeof k[i].className==='string'?k[i].className:'')+' '+(k[i].getAttribute&&k[i].getAttribute('aria-label')||'')).toLowerCase();
        if(a.indexOf('close')>-1) return true;
      }
    }catch(e){}
    return false;
  }
  // A small numeric "unread" badge (the red 1) — mimics a notification count.
  function adHasBadge(el){
    try{
      var k=el.querySelectorAll('*');
      for(var i=0;i<k.length && i<80;i++){
        var t=(k[i].textContent||'').trim();
        if(/^\d{1,2}$/.test(t) && k[i].children.length===0) return true;
      }
    }catch(e){}
    return false;
  }
  function adHasImage(el){
    try{ return !!(el.querySelector && el.querySelector('img,svg,picture')); }catch(e){ return false; }
  }
  // A RASTER graphic — a real <img src>, <picture>, or a CSS background-image on
  // the element or a near descendant. This is the ad's actual artwork. It is
  // deliberately distinct from a plain inline <svg> (which is how the site's own
  // icon buttons — scroll-to-top, settings — are drawn), so a promo banner can
  // be told apart from a benign vector-icon control.
  function adHasRaster(el){
    try{
      if(el.querySelector && el.querySelector('img[src],picture,image[href]')) return true;
      var bg=''; try{ bg=getComputedStyle(el).backgroundImage||''; }catch(e){}
      if(/url\(/i.test(bg)) return true;
      var k=el.querySelectorAll?el.querySelectorAll('*'):[];
      for(var i=0;i<k.length && i<40;i++){
        var b2=''; try{ b2=getComputedStyle(k[i]).backgroundImage; }catch(e){}
        if(b2 && /url\(/i.test(b2)) return true;
      }
    }catch(e){}
    return false;
  }
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
    if(adIsPlayer(n)) return;                       // never remove the player
    // 2) HIGH-CONFIDENCE SCAM TEXT — checked BEFORE any positioning heuristic.
    //    This used to sit AFTER the fixed/absolute+z-index gate below, so a fake
    //    "Your premium account is activated / follow the instruction on the next
    //    page" card that was static, relative, sticky, or absolute with z-index
    //    'auto' (parseInt -> 0) returned early and its text was NEVER tested.
    //    That is exactly the ad that kept getting through. Wording like this is a
    //    certain ad however it is positioned; the length gate means we can never
    //    take out a page-sized container that merely contains the phrase.
    //    textContent (not innerText) — it needs no layout pass, so this stays
    //    cheap enough to run over every element on a timer.
    var txt=''; try{ txt=(n.textContent||''); }catch(e){}
    if(txt && txt.length<600 && n.tagName!=='BODY' && n.tagName!=='HTML' &&
       AD_TXT.test(txt)){ adKill(n); return; }
    // The cheap text test above runs on EVERY element; the positional tests below
    // call getComputedStyle, which forces layout — restrict those to tags that
    // could plausibly BE an overlay container so sweeping stays smooth.
    var tn=n.tagName;
    if(tn!=='DIV'&&tn!=='INS'&&tn!=='ASIDE'&&tn!=='SECTION'&&tn!=='A'&&tn!=='DIALOG'&&
       tn!=='CENTER'&&tn!=='TABLE'&&tn!=='IFRAME'&&tn!=='ARTICLE'&&tn!=='FORM'&&
       tn!=='HEADER'&&tn!=='FOOTER'&&tn!=='NAV') return;
    var s; try{ s=getComputedStyle(n); }catch(e){ return; } if(!s) return;
    if(s.display==='none' || s.visibility==='hidden') return;
    var pos=s.position, z=parseInt(s.zIndex,10)||0;
    // Only something LIFTED OUT of normal flow can be a pop-up overlay: fixed,
    // sticky, or absolute raised above the page (z>=50). Everything else is
    // ordinary in-flow content and is left completely alone.
    if(pos!=='fixed' && pos!=='sticky' && !(pos==='absolute' && z>=50)) return;
    var r; try{ r=n.getBoundingClientRect(); }catch(e){ return; }
    if(r.width<=0 || r.height<=0) return;
    var vw=window.innerWidth||document.documentElement.clientWidth||1;
    var vh=window.innerHeight||document.documentElement.clientHeight||1;
    // A) A screen-covering fixed overlay: remove ONLY when it carries an ad
    //    signal (off-site link or ad iframe); otherwise it's the video-player
    //    wrapper or the site's own modal and must survive.
    if(pos==='fixed' && r.width>=vw*0.9 && r.height>=vh*0.6){
      if(adHasOffsite(n) || (n.querySelector&&n.querySelector('iframe[src]'))){ adKill(n); return; }
      return;
    }
    // B) A bar spanning (almost) the full width is the site's real header /
    //    bottom nav — never a floating ad. Its children were scanned already.
    if(r.width>=vw*0.85) return;
    // ── C) WEIGHTED SCORING for a small floating widget ───────────────────────
    // No single trait is proof — a legit scroll-to-top button is ALSO a small
    // fixed icon hugging a corner. So we add up independent ad signals and only
    // remove when they clearly outweigh a benign control. This uBlock-style
    // heuristic is far more robust than the old single rigid rule that missed
    // any widget using a CSS background-image or a pseudo-element badge.
    var idcls=((n.id||'')+' '+(typeof n.className==='string'?n.className:'')).toLowerCase();
    var score=0;
    if(AD_WIDGET.test(idcls)) score+=2;     // named gift/prize/coupon/hongbao/float-ad
    if(adHasClose(n))         score+=2;     // carries its own × close control
    if(adHasBadge(n))         score+=2;     // fake red unread-count badge
    if(adHasOffsite(n))       score+=3;     // a link that leaves the site = ad
    if(adHasImage(n))         score+=1;     // has a graphic of any kind
    // Corner/edge-anchored — the floating-ad hallmark (a benign icon scores here
    // too, which is why this alone is never enough to remove).
    var nearR=(vw-r.right)<=24, nearL=r.left<=24, nearB=(vh-r.bottom)<=48, nearT=r.top<=24;
    var corner=(nearR||nearL)&&(nearB||nearT);
    if(corner) score+=1;
    // A RASTER banner (real photo/img) bigger than a normal icon button, sitting
    // in a corner, is promo artwork — the site's own controls are small vector
    // icons, so this cleanly separates the gift/prize graphic from scroll-to-top.
    if(corner && adHasRaster(n) && (r.width>64 || r.height>64)) score+=2;
    // Wraps a KNOWN ad iframe — decisive on its own.
    try{ var ifr=n.querySelector&&n.querySelector('iframe'); if(ifr){ var s2=ifr.src||ifr.getAttribute('src')||''; if(s2 && AD_HOST.test(s2)) score+=4; } }catch(e){}
    if(score>=3){ adKill(n); return; }
  }
  // Walk a root (document or shadowRoot), scanning every element and RECURSING
  // into open shadow roots. querySelectorAll does NOT pierce shadow DOM, so an
  // overlay attached to a shadow root was completely invisible to the old sweep
  // — a standard way these cards evade blockers.
  function adWalk(root, depth){
    if(!root || depth>6) return;
    var els;
    try{ els = root.querySelectorAll('*'); }catch(e){ return; }
    for(var i=0;i<els.length;i++){
      var el = els[i];
      adScan(el);
      try{ if(el.shadowRoot) adWalk(el.shadowRoot, depth+1); }catch(e){}
    }
  }
  function adSweep(){
    try{
      adWalk(document, 0);
      // Same-origin iframes: if this WebView can't inject into sub-frames we can
      // still reach any same-origin frame's document from here.
      var frames; try{ frames=document.querySelectorAll('iframe'); }catch(e){ frames=[]; }
      for(var f=0;f<frames.length;f++){
        try{
          var d = frames[f].contentDocument;
          if(d) adWalk(d, 1);
        }catch(e){}                                  // cross-origin: not readable
      }
    }catch(e){}
  }
  // Manual override: tapping the shield in the toolbar calls this. Removes every
  // SMALL floating widget on the page (plus anything the rules already flag), so
  // if a brand-new ad shape ever slips past the heuristics the user can always
  // clear it themselves. Size-capped so the site's real header/nav (full width)
  // and the video player survive; a reload restores anything over-zealous.
  try{
    window.__sfZap = function(){
      var out=0;
      try{
        var all=document.querySelectorAll('*');
        var vw1=window.innerWidth||document.documentElement.clientWidth||1;
        var vh1=window.innerHeight||document.documentElement.clientHeight||1;
        for(var i=0;i<all.length;i++){
          var el=all[i];
          try{
            if(adIsPlayer(el)) continue;
            var st=getComputedStyle(el); if(!st) continue;
            if(st.position!=='fixed' && st.position!=='sticky') continue;
            var r=el.getBoundingClientRect();
            if(r.width<=0||r.height<=0) continue;
            if(r.width<=vw1*0.6 && r.height<=vh1*0.4){ adKill(el); out++; }
          }catch(e){}
        }
      }catch(e){}
      return out;
    };
  }catch(e){}
  // Schedule with the SAVED originals: our own setTimeout/setInterval wrappers
  // filter by function source, and a future token added to AD_FN could silently
  // stop every sweep from ever running. The originals can't be sabotaged.
  var _sched=function(fn,t){ try{ return _st.call(window,fn,t); }catch(e){ return setTimeout(fn,t); } };
  var _repeat=function(fn,t){ try{ return _si.call(window,fn,t); }catch(e){ return setInterval(fn,t); } };
  // These ads inject on a delay and re-inject, so sweep immediately, a few more
  // times, then keep watch on a short interval.
  [0,120,300,600,1000,1800,2800,4200,6000,9000].forEach(function(t){ _sched(adSweep,t); });
  _repeat(adSweep, 1200);
  // Debounced full sweep on ANY DOM change: scanning only the added node missed
  // a card injected deep inside an added subtree.
  var _pending=0;
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
    if(!_pending){ _pending=1; _sched(function(){ _pending=0; adSweep(); },60); }
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
    // Live "ads blocked" tally shown in the toolbar. shouldInterceptRequest runs
    // on WebView worker threads, so the count is an AtomicInteger and only the
    // display value is pushed to Compose on the main thread.
    val blockedCounter = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    var blockedCount by remember { mutableIntStateOf(0) }

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
            // MOBILE UA: the desktop-mode experiment made some of these sites
            // (donghuafun in particular) load unreliably AND surfaced more ads,
            // so we serve their responsive mobile layout — it fits the phone and
            // is what the ad-blocking script below is tuned against.
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            // Google Safe Browsing: an extra network-level shield that blocks
            // navigation to known malware / social-engineering domains — many of
            // the popunder/redirect ad destinations on pirate mirrors are already
            // on that list, so this stops them before a page even loads.
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                try { safeBrowsingEnabled = true } catch (_: Throwable) {}
            }
        }
        // ── THE key injection ────────────────────────────────────────────────
        // evaluateJavascript (below, in onPageStarted/Finished) only ever runs in
        // the MAIN frame. These sites embed the video in a third-party IFRAME and
        // that is where the ad scripts live — so the pop-up blackhole, timer
        // sniffing, click guard and overlay killer never applied where the ads
        // actually fire. addDocumentStartJavaScript injects into EVERY frame
        // (allowedOriginRules "*" covers cross-origin iframes) and runs BEFORE any
        // page script, so window.open/setTimeout are already neutered by the time
        // an ad loader grabs its references.
        try {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(
                    androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
                androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
                    wv, AD_BLOCK_JS, setOf("*"))
            }
        } catch (_: Throwable) {}

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

        // When the current document started loading — used to tell a genuine
        // on-load mirror redirect from a later ad hijack (see below).
        var pageStartedAt = 0L
        // Bump the visible "ads blocked" tally (called from WebView worker threads)
        fun countBlocked() {
            val n = blockedCounter.incrementAndGet()
            mainHandler.post { blockedCount = n }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                pageStartedAt = System.currentTimeMillis()
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
                    // Only remember SAME-SITE pages. Saving an off-site ad/redirect
                    // URL (or an about:blank) meant the tab reopened on a broken /
                    // ad page next time — "the Donghua tab isn't working".
                    if (url.startsWith("http") && baseDomainOf(url) == siteBase &&
                        !isAdRequest(url)) {
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
                if (isAdRequest(request.url.toString())) { countBlocked(); return true }
                if (request.isForMainFrame) {
                    val scheme = request.url.scheme?.lowercase()
                    // intent://, market://, tg:// … = "open another app" ad links.
                    if (scheme != null && scheme != "http" && scheme != "https") {
                        countBlocked(); return true
                    }
                    val host = request.url.host?.lowercase().orEmpty()
                    val offSite = host.isNotEmpty() && siteBase.isNotEmpty() &&
                        !host.endsWith(siteBase) && INFRA_ALLOW.none { host.endsWith(it) }
                    if (offSite) {
                        // A tap-triggered jump off-site is the classic popunder ad.
                        if (request.hasGesture()) { countBlocked(); return true }
                        // Gesture-LESS off-site navigation: a genuine mirror redirect
                        // happens right as the page loads, so allow it only inside a
                        // short grace window. After that, a script silently replacing
                        // the whole page is an ad hijack — this was the last big hole
                        // (an ad could do location.href = adUrl with no tap at all).
                        if (System.currentTimeMillis() - pageStartedAt > REDIRECT_GRACE_MS) {
                            countBlocked(); return true
                        }
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
                if (isAdRequest(url)) { countBlocked(); return emptyResponse() }
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

        // Restore the last page only if it's still a valid same-site URL;
        // otherwise (blank, off-site ad page, or an old rotated domain) fall back
        // to the home page so the tab always opens on something that works.
        val saved = sitePrefs.getString("last_url", null)
        val startUrl = if (saved != null && saved.startsWith("http") &&
            baseDomainOf(saved) == siteBase && !isAdRequest(saved)) saved else homeUrl
        wv.loadUrl(startUrl)
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
                    // Live proof the shield is working — TAP IT to force-remove any
                    // floating pop-up the automatic rules missed.
                    if (blockedCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    webViewRef?.evaluateJavascript(
                                        "(window.__sfZap?window.__sfZap():0)"
                                    ) { r ->
                                        val n = r?.trim()?.toIntOrNull() ?: 0
                                        android.widget.Toast.makeText(
                                            context,
                                            if (n > 0) "Removed $n pop-up${if (n == 1) "" else "s"}"
                                            else "No pop-ups found",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Shield, contentDescription = "Ads blocked",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                if (blockedCount > 999) "999+" else "$blockedCount",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
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
