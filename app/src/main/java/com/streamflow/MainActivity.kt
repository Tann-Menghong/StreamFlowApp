package com.streamflow

import android.Manifest
import android.app.KeyguardManager
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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

    // Share/open-link and shortcut destinations as state so onNewIntent
    // (singleTask launch mode) can route into the already-running UI
    private var pendingUrl by mutableStateOf<String?>(null)
    private var pendingDest by mutableStateOf<String?>(null)
    // Bumped on every routed intent so NavGraph re-navigates even when the
    // url/dest string is identical to the previous one (same video shared twice)
    private var intentNonce by mutableStateOf(0)

    // ── App lock (Settings > App lock) ───────────────────────────────────────
    // appLockEnabled is read synchronously from a plain-prefs mirror at cold
    // start (DataStore is async → would flash content before locking), then kept
    // live by a collector. appUnlocked drives the Compose gate.
    private var appLockEnabled = false
    private var appUnlocked by mutableStateOf(true)
    private var unlockInFlight = false
    private lateinit var unlockLauncher: ActivityResultLauncher<Intent>

    private fun deviceSecure(): Boolean =
        (getSystemService(KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true

    private fun requestUnlock() {
        if (unlockInFlight) return
        val km = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
        @Suppress("DEPRECATION")
        val intent = km?.createConfirmDeviceCredentialIntent("StreamFlow", "Unlock to continue")
        if (intent != null) {
            unlockInFlight = true
            try { unlockLauncher.launch(intent) }
            catch (_: Exception) { unlockInFlight = false; appUnlocked = true }
        } else {
            // No secure lock configured on the device — can't lock, so allow in
            appUnlocked = true
        }
    }

    private fun handleIntent(intent: Intent?) {
        // Relaunching from Recents redelivers the ORIGINAL intent — without this
        // guard, reopening the app after a share yanked the user back into that
        // video instead of resuming where they were
        if (intent != null &&
            (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return
        val url = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                Regex("https?://(?:www\\.|m\\.)?(?:youtube\\.com|youtu\\.be)\\S+").find(text)?.value
            }
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        val dest = when (intent?.action) {
            "com.streamflow.shortcut.FEED" -> "feed"
            "com.streamflow.shortcut.LIBRARY" -> "library"
            "com.streamflow.shortcut.SEARCH" -> "search"
            "com.streamflow.shortcut.SETTINGS" -> "settings"
            else -> null
        }
        // Only overwrite on a real destination — a plain launcher re-open
        // (ACTION_MAIN) must not yank the user anywhere. Setting one side
        // clears the other so the newest request always wins.
        if (url != null) { pendingUrl = url; pendingDest = null; intentNonce++ }
        else if (dest != null) { pendingDest = dest; pendingUrl = null; intentNonce++ }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Branded splash (must be installed before super.onCreate)
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Request notification permission for media player controls on Android 13+.
        // Ask at most ONCE automatically — re-prompting on every launch after a
        // denial is nagging (and Android auto-denies after two refusals anyway).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val flags = getSharedPreferences("app_flags", MODE_PRIVATE)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED &&
                !flags.getBoolean("asked_notif_perm", false)
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
                )
                flags.edit().putBoolean("asked_notif_perm", true).apply()
            }
        }

        val prefs = AppPreferences.get(this)
        lifecycleScope.launch {
            prefs.autoPip.collect { autoPipEnabled = it }
        }

        // App lock: read the synchronous mirror so the gate is decided on the very
        // first frame (no content flash), then keep it live for re-lock decisions.
        appLockEnabled = getSharedPreferences(
            AppPreferences.LOCK_MIRROR_PREFS, MODE_PRIVATE
        ).getBoolean(AppPreferences.LOCK_MIRROR_KEY, false)
        appUnlocked = !(appLockEnabled && deviceSecure())
        unlockLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            unlockInFlight = false
            if (result.resultCode == RESULT_OK) appUnlocked = true
            // else: stay locked — the lock screen offers a manual retry
        }
        lifecycleScope.launch { prefs.appLock.collect { appLockEnabled = it } }

        handleIntent(intent)

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
                    if (appLockEnabled && !appUnlocked) {
                        // Locked: authenticate before anything else is shown
                        com.streamflow.ui.lock.LockScreen(onUnlock = { requestUnlock() })
                        androidx.compose.runtime.LaunchedEffect(Unit) { requestUnlock() }
                    } else when (onboardingDone) {
                        null  -> Unit // waiting for DataStore, hidden behind the splash
                        false -> com.streamflow.ui.onboarding.OnboardingScreen(prefs) {}
                        else  -> NavGraph(startUrl = pendingUrl, startDest = pendingDest,
                                          intentNonce = intentNonce)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Re-lock when the app actually leaves the foreground. Skip while the
        // system credential sheet is up (that itself stops us), during PiP (still
        // in use), and across config changes.
        if (appLockEnabled && deviceSecure() && !unlockInFlight &&
            !isInPip && !isChangingConfigurations
        ) {
            appUnlocked = false
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
                // Resolution first, refresh second: phones with resolution
                // switching (e.g. iQOO's 1.5K panels also expose a downscaled
                // 1080p mode) must not get a blurry mode just because it is
                // fast — we want full resolution AT its highest refresh rate.
                .maxWithOrNull(compareBy(
                    { it.physicalWidth.toLong() * it.physicalHeight },
                    { it.refreshRate }
                ))
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
