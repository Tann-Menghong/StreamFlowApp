package com.streamflow.ui.donghua

import androidx.compose.runtime.Composable
import com.streamflow.ui.browser.AdblockBrowserScreen

// Both streaming tabs share the same ad-blocked browser implementation —
// see ui/browser/AdblockBrowserScreen.kt. "donghua_prefs" is the historical
// prefs name, kept so existing users' last-visited page carries over.
@Composable
fun DonghuaScreen(onFullscreenChange: (Boolean) -> Unit = {}) = AdblockBrowserScreen(
    homeUrl = "https://donghuafun.com/",
    prefsName = "donghua_prefs",
    defaultTitle = "Donghua Fun",
    onFullscreenChange = onFullscreenChange
)
