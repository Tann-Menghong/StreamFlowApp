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
import com.streamflow.data.local.AppPreferences
import com.streamflow.ui.navigation.NavGraph
import com.streamflow.ui.theme.StreamFlowTheme
import com.streamflow.ui.theme.toAppTheme

class MainActivity : ComponentActivity() {

    var isInPip by mutableStateOf(false)
        private set

    var isPlayerActive by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
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

        val sharedUrl = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                Regex("https?://(?:www\\.|m\\.)?(?:youtube\\.com|youtu\\.be)\\S+").find(text)?.value
            }
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }

        enableHighRefreshRate()
        enableEdgeToEdge()
        setContent {
            val themeStr by prefs.theme.collectAsState(initial = "DARK")
            val accentStr by prefs.accentColor.collectAsState(initial = "RED")
            val fontScaleStr by prefs.fontScale.collectAsState(initial = "DEFAULT")
            val fontScale = when (fontScaleStr) {
                "SMALL" -> 0.9f
                "LARGE" -> 1.12f
                else -> 1f
            }
            StreamFlowTheme(theme = themeStr.toAppTheme(), accent = accentStr, fontScale = fontScale) {
                NavGraph(startUrl = sharedUrl)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayerActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9)).build()
            enterPictureInPictureMode(params)
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
