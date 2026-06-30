package com.streamflow

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.streamflow.data.local.AppPreferences
import com.streamflow.ui.navigation.NavGraph
import com.streamflow.ui.theme.StreamFlowTheme
import com.streamflow.ui.theme.toAppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

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

        // Biometric lock on launch
        val appLockEnabled = runBlocking { prefs.appLock.first() }
        if (appLockEnabled) {
            val bm = BiometricManager.from(this)
            if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
                val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationFailed() { finish() }
                        override fun onAuthenticationError(code: Int, msg: CharSequence) { finish() }
                    })
                prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock StreamFlow")
                    .setSubtitle("Use biometric to continue")
                    .setNegativeButtonText("Cancel")
                    .build())
            }
        }

        val sharedUrl = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            ?.let { text ->
                Regex("https?://(?:www\\.)?(?:youtube\\.com|youtu\\.be)\\S+").find(text)?.value
            }

        enableHighRefreshRate()
        enableEdgeToEdge()
        setContent {
            val themeStr    by prefs.theme.collectAsState(initial = "DARK")
            val accentStr   by prefs.accentColor.collectAsState(initial = "RED")
            val autoTheme   by prefs.autoTheme.collectAsState(initial = false)
            val nightStart  by prefs.nightThemeStart.collectAsState(initial = "21:00")
            val nightEnd    by prefs.nightThemeEnd.collectAsState(initial = "07:00")

            val effectiveTheme = if (autoTheme) {
                val now = java.util.Calendar.getInstance()
                val nowMin = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
                val startMin = nightStart.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 21) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }
                val endMin   = nightEnd.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 7)  * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }
                val isNight = if (startMin < endMin) nowMin in startMin until endMin
                              else nowMin >= startMin || nowMin < endMin
                if (isNight) "DARK" else "LIGHT"
            } else themeStr

            StreamFlowTheme(theme = effectiveTheme.toAppTheme(), accent = accentStr) {
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
