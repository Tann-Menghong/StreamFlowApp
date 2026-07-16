package com.streamflow

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.streamflow.data.local.AppPreferences
import com.streamflow.ui.navigation.NavGraph
import com.streamflow.ui.theme.LocalHapticsEnabled
import com.streamflow.ui.theme.LocalThumbCorner
import com.streamflow.ui.theme.StreamFlowTheme
import com.streamflow.ui.theme.cornerDpFor
import com.streamflow.ui.theme.toAppTheme

class MainActivity : ComponentActivity() {

    var isInPip by mutableStateOf(false)
        private set

    var isPlayerActive by mutableStateOf(false)

    // Mirrors the autoPip pref for the synchronous onUserLeaveHint callback
    private var autoPipEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Branded splash (must be installed before super.onCreate)
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Request notification permission for media player controls on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
                )
            }
        }

        val prefs = AppPreferences.get(this)
        lifecycleScope.launch {
            prefs.autoPip.collect { autoPipEnabled = it }
        }

        val sharedUrl = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                Regex("https?://(?:www\\.|m\\.)?(?:youtube\\.com|youtu\\.be)\\S+").find(text)?.value
            }
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        // Launcher shortcut / widget destinations
        val shortcutDest = when (intent?.action) {
            "com.streamflow.shortcut.FEED" -> "feed"
            "com.streamflow.shortcut.LIBRARY" -> "library"
            "com.streamflow.shortcut.SEARCH" -> "search"
            "com.streamflow.shortcut.SETTINGS" -> "settings"
            else -> null
        }

        enableHighRefreshRate()
        enableEdgeToEdge()
        setContent {
            val themeStr by prefs.theme.collectAsState(initial = "DARK")
            val accentStr by prefs.accentColor.collectAsState(initial = "RED")
            val fontScaleStr by prefs.fontScale.collectAsState(initial = "DEFAULT")
            val cornerStyle by prefs.cornerStyle.collectAsState(initial = "ROUNDED")
            val hapticsOn by prefs.hapticsEnabled.collectAsState(initial = true)
            val designStyle by prefs.designStyle.collectAsState(initial = "MODERN")
            val fontFamilyStr by prefs.fontFamily.collectAsState(initial = "DEFAULT")
            val fontScale = when (fontScaleStr) {
                "SMALL" -> 0.9f
                "LARGE" -> 1.12f
                else -> 1f
            }
            // Status bar icons must follow the APP theme, not the system theme —
            // otherwise dark icons vanish over our dark background (looks like
            // the app is "overlaying" the clock/battery/wifi)
            val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themeStr.toAppTheme()) {
                com.streamflow.ui.theme.AppTheme.LIGHT  -> false
                com.streamflow.ui.theme.AppTheme.SYSTEM -> sysDark
                else -> true
            }
            androidx.compose.runtime.LaunchedEffect(darkTheme) {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = !darkTheme
            }
            StreamFlowTheme(theme = themeStr.toAppTheme(), accent = accentStr,
                fontScale = fontScale, fontFamilyPref = fontFamilyStr) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalThumbCorner provides cornerDpFor(cornerStyle),
                    LocalHapticsEnabled provides hapticsOn,
                    com.streamflow.ui.theme.LocalDesignStyle provides designStyle
                ) {
                    // First launch: quick setup (country, interests, theme) before the feed
                    val onboardingDone by androidx.compose.runtime.produceState<Boolean?>(null) {
                        prefs.onboardingDone.collect { value = it }
                    }
                    when (onboardingDone) {
                        null  -> Unit // waiting for DataStore, hidden behind the splash
                        false -> com.streamflow.ui.onboarding.OnboardingScreen(prefs) {}
                        else  -> NavGraph(startUrl = sharedUrl, startDest = shortcutDest)
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Only float a mini video over other apps when the user opted in;
        // otherwise playback continues in the media notification only
        if (isPlayerActive && autoPipEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9)).build()
            // Some OEMs throw IllegalStateException here when the user has
            // revoked the PiP permission in system settings — never crash
            // the whole app just because the mini window couldn't open
            try { enterPictureInPictureMode(params) } catch (_: Exception) {}
        }
    }

    private fun enableHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val best = windowManager.defaultDisplay.supportedModes
                .maxByOrNull { it.refreshRate }
            if (best != null) {
                window.attributes = window.attributes.also {
                    it.preferredDisplayModeId = best.modeId
                }
            }
        } else {
            window.attributes = window.attributes.also {
                it.preferredRefreshRate = Float.MAX_VALUE
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
    }
}
