package com.streamflow.ui.donghua

import androidx.compose.runtime.Composable
import com.streamflow.ui.browser.AdblockBrowserScreen

// Donghua is a website tab, like Drama and MKissa: the same ad-blocked browser
// in ui/browser/AdblockBrowserScreen.kt, pointed at a different home page.
// "donghua_prefs" is the historical prefs name, kept so existing users'
// last-visited page carries over.
//
// v6.23.0 replaced this with a native YouTube-backed tab and v6.25.0 put it
// back at the user's request. What is worth keeping from that detour: the
// browser tabs cannot participate in the rest of the app. Nothing here is a
// VideoItem, so download, favourite, Watch Later, queue, history and resume
// position are all unavailable on this tab by construction, and a playback
// failure inside the page is the site's to fix, not StreamFlow's. Desktop mode
// (BrowserDisplayMode, on by default) and ad-blocking still apply.
@Composable
fun DonghuaScreen(onFullscreenChange: (Boolean) -> Unit = {}) = AdblockBrowserScreen(
    homeUrl = "https://donghuafun.com/",
    prefsName = "donghua_prefs",
    defaultTitle = "Donghua Fun",
    onFullscreenChange = onFullscreenChange
)
