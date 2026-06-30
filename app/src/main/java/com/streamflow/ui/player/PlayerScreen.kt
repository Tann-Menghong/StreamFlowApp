package com.streamflow.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.streamflow.MainActivity
import com.streamflow.PlaybackService
import com.streamflow.data.local.AppPreferences
import com.streamflow.ui.components.MiniPlayerData
import com.streamflow.ui.components.MiniPlayerState
import com.streamflow.ui.components.VideoCard
import com.streamflow.ui.components.formatViews
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(
    videoUrl: String,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    vm: PlayerViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val isFavorite by vm.isFavorite.collectAsState(initial = false)
    val isInWatchLater by vm.isInWatchLater.collectAsState(initial = false)
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = AppPreferences.get(context)

    val isDirectStream = remember(videoUrl) {
        val lower = videoUrl.lowercase()
        lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".webm") ||
        lower.contains("/hls/") || lower.contains("/stream/")
    }
    var isFullscreen by remember { mutableStateOf(true) }

    // ── MediaController ──────────────────────────────────────────────────────
    var mediaController by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try { mediaController = future.get() } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            future.cancel(false)
            mediaController?.release()
            mediaController = null
        }
    }

    // ── Tell MainActivity we're in the player (for auto-PiP) ─────────────────
    val mainActivity = context as? MainActivity
    DisposableEffect(Unit) {
        mainActivity?.isPlayerActive = true
        onDispose { mainActivity?.isPlayerActive = false }
    }

    // ── Cleanup on exit ──────────────────────────────────────────────────────
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val ic = WindowCompat.getInsetsController(act.window, act.window.decorView)
                ic.show(WindowInsetsCompat.Type.systemBars())
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val latestController = rememberUpdatedState(mediaController)
    DisposableEffect(videoUrl) {
        onDispose {
            vm.savePosition(videoUrl, latestController.value?.currentPosition ?: 0L)
        }
    }

    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    val heartScale by animateFloatAsState(
        targetValue   = if (isFavorite) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "heart"
    )
    val heartColor by animateColorAsState(
        targetValue   = if (isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        animationSpec = tween(200),
        label         = "heart_color"
    )

    LaunchedEffect(videoUrl) { vm.loadVideo(videoUrl) }

    // ── Update MiniPlayerState when ready ────────────────────────────────────
    LaunchedEffect(state) {
        val r = state as? PlayerUiState.Ready ?: return@LaunchedEffect
        MiniPlayerState.update(MiniPlayerData(
            url = videoUrl,
            title = r.details.title,
            thumbnailUrl = r.details.thumbnailUrl,
            uploaderName = r.details.uploaderName,
            isPlaying = true
        ))
    }

    LaunchedEffect(state, mediaController) {
        if (isDirectStream) return@LaunchedEffect
        val ready = state as? PlayerUiState.Ready ?: return@LaunchedEffect
        val mc = mediaController ?: return@LaunchedEffect
        val d = ready.details

        val extras = Bundle().apply {
            if (d.audioUrl != null) putString("audioUrl", d.audioUrl)
        }
        val mediaItem = MediaItem.Builder()
            .setUri(d.streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(d.title)
                    .setArtist(d.uploaderName)
                    .setArtworkUri(d.thumbnailUrl.toUri())
                    .build()
            )
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
            .build()

        mc.setMediaItem(mediaItem)
        mc.prepare()
        mc.play()
        val savedPos = vm.getSavedPosition(videoUrl)
        if (savedPos > 0L) mc.seekTo(savedPos)

        val speedStr = prefs.defaultSpeed.first()
        mc.setPlaybackSpeed(speedStr.toFloatOrNull() ?: 1f)
    }

    // ── Fullscreen orientation ───────────────────────────────────────────────
    LaunchedEffect(isFullscreen) {
        activity?.let { act ->
            val win = act.window
            val ic  = WindowCompat.getInsetsController(win, win.decorView)
            if (isFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ic.hide(WindowInsetsCompat.Type.systemBars())
                ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    win.attributes.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                ic.show(WindowInsetsCompat.Type.systemBars())
                win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // ── Speed & seek state ───────────────────────────────────────────────────
    var showSpeedMenu by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(1f) }
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    LaunchedEffect(Unit) { currentSpeed = prefs.defaultSpeed.first().toFloatOrNull() ?: 1f }
    var skipMs by remember { mutableLongStateOf(10_000L) }
    LaunchedEffect(Unit) { skipMs = (prefs.skipSeconds.first().toLongOrNull() ?: 10L) * 1000L }

    var seekFeedback by remember { mutableStateOf("") }
    var showSeekFeedback by remember { mutableStateOf(false) }
    if (showSeekFeedback) {
        LaunchedEffect(seekFeedback) { delay(700); showSeekFeedback = false }
    }

    // ── Repeat mode ──────────────────────────────────────────────────────────
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    LaunchedEffect(repeatMode) { mediaController?.repeatMode = repeatMode }

    // ── Auto-save position every 5 s while playing ──────────────────────────
    LaunchedEffect(videoUrl, mediaController) {
        while (true) {
            delay(5_000L)
            val mc = mediaController ?: continue
            if (mc.isPlaying && mc.currentPosition > 0L) {
                vm.savePosition(videoUrl, mc.currentPosition)
            }
        }
    }

    // ── Lock state ───────────────────────────────────────────────────────────
    var isLocked by remember { mutableStateOf(false) }

    // ── Sleep timer state ────────────────────────────────────────────────────
    var sleepMinutes by remember { mutableIntStateOf(0) }
    var sleepRemaining by remember { mutableLongStateOf(0L) }
    var showSleepMenu by remember { mutableStateOf(false) }

    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes <= 0) { sleepRemaining = 0L; return@LaunchedEffect }
        sleepRemaining = sleepMinutes * 60L
        while (sleepRemaining > 0L) {
            delay(1000L)
            sleepRemaining--
        }
        mediaController?.pause()
        sleepMinutes = 0
        sleepRemaining = 0L
    }

    // ── Brightness/volume drag state ─────────────────────────────────────────
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableFloatStateOf(-1f) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0f) }

    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }

    // ── Pinch-to-zoom (fullscreen) ───────────────────────────────────────────
    var zoom by remember { mutableFloatStateOf(1f) }
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        zoom = (zoom * zoomChange).coerceIn(0.8f, 4f)
    }

    // ── Stats overlay ────────────────────────────────────────────────────────
    var showStats by remember { mutableStateOf(false) }
    var bufferPct by remember { mutableIntStateOf(0) }
    LaunchedEffect(mediaController) {
        while (true) {
            delay(1000L)
            val mc = mediaController ?: continue
            if (mc.duration > 0L)
                bufferPct = ((mc.bufferedPosition * 100L) / mc.duration).toInt().coerceIn(0, 100)
        }
    }

    // ── Auto-play countdown (when video ends, auto-navigate to next) ─────────
    var autoPlayCountdown by remember { mutableIntStateOf(0) }
    var autoPlayTarget by remember { mutableStateOf("") }
    LaunchedEffect(state, mediaController) {
        val ready = state as? PlayerUiState.Ready ?: return@LaunchedEffect
        val nextUrl = ready.details.relatedVideos.firstOrNull()?.url ?: return@LaunchedEffect
        autoPlayCountdown = 0; autoPlayTarget = ""
        while (true) {
            delay(500L)
            val mc = mediaController ?: continue
            if (mc.playbackState == Player.STATE_ENDED && mc.currentPosition > 0L) {
                autoPlayTarget = nextUrl
                for (i in 5 downTo 1) { autoPlayCountdown = i; delay(1000L) }
                autoPlayCountdown = 0
                if (autoPlayTarget.isNotEmpty()) onVideoClick(autoPlayTarget)
                break
            }
        }
    }

    @Composable
    fun SeekFeedback() {
        if (showSeekFeedback) {
            Text(
                seekFeedback, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }

    @Composable
    fun DoubleTapZones() {
        val mc = mediaController ?: return
        val skipSec = skipMs / 1000L
        val haptic = LocalHapticFeedback.current
        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(bottom = 72.dp)) {
            Box(
                Modifier.weight(0.3f).fillMaxHeight()
                    .pointerInput(skipMs) {
                        detectTapGestures(onDoubleTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            mc.seekTo((mc.currentPosition - skipMs).coerceAtLeast(0))
                            seekFeedback = "- ${skipSec}s"; showSeekFeedback = true
                        })
                    }
            )
            Box(Modifier.weight(0.4f).fillMaxHeight())
            Box(
                Modifier.weight(0.3f).fillMaxHeight()
                    .pointerInput(skipMs) {
                        detectTapGestures(onDoubleTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            mc.seekTo(mc.currentPosition + skipMs)
                            seekFeedback = "+ ${skipSec}s"; showSeekFeedback = true
                        })
                    }
            )
        }
    }

    // ── Direct stream (WebView) ───────────────────────────────────────────────
    if (isDirectStream) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).also { wv ->
                        wv.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                            allowContentAccess = true
                        }
                        android.webkit.CookieManager.getInstance().apply {
                            setAcceptCookie(true); setAcceptThirdPartyCookies(wv, true)
                        }
                        wv.webChromeClient = android.webkit.WebChromeClient()
                        val safeUrl = videoUrl.replace("\\", "\\\\").replace("\"", "\\\"")
                        val html = """<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<style>*{margin:0;padding:0;box-sizing:border-box}
body{background:#000;width:100vw;height:100vh;overflow:hidden;display:flex;align-items:center;justify-content:center}
video{width:100%;height:100%;object-fit:contain}</style></head><body>
<video id="v" src="$safeUrl" autoplay playsinline controls></video>
<script>document.getElementById('v').play().catch(function(){});</script>
</body></html>"""
                        wv.loadDataWithBaseURL("https://donghuafun.com/", html, "text/html", "utf-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
        return
    }

    // ── Fullscreen player ─────────────────────────────────────────────────────
    if (isFullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black).transformable(transformableState)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = mediaController
                        useController = !isLocked
                        keepScreenOn = true
                    }
                },
                update = { pv ->
                    pv.player = mediaController
                    pv.useController = !isLocked
                },
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }
            )

            if (!isLocked) {
                DoubleTapZones()
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SeekFeedback() }

            // ── FAR LEFT brightness zone ─────────────────────────────────
            if (!isLocked) {
                Box(
                    Modifier
                        .width(50.dp)
                        .fillMaxHeight()
                        .padding(bottom = 72.dp)
                        .align(Alignment.CenterStart)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    val attrs = activity?.window?.attributes
                                    brightnessLevel = if ((attrs?.screenBrightness ?: -1f) < 0f) 0.5f else attrs!!.screenBrightness
                                    showBrightnessIndicator = true
                                },
                                onDragEnd = { showBrightnessIndicator = false },
                                onDragCancel = { showBrightnessIndicator = false },
                                onDrag = { _, dragAmount ->
                                    brightnessLevel = (brightnessLevel - dragAmount.y / 600f).coerceIn(0.01f, 1f)
                                    activity?.window?.attributes = activity?.window?.attributes?.also { it.screenBrightness = brightnessLevel }
                                }
                            )
                        }
                )
                if (showBrightnessIndicator) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                            .background(Color.Black.copy(0.55f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.Brightness6, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("${(brightnessLevel * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                    }
                }

                // ── FAR RIGHT volume zone ────────────────────────────────
                Box(
                    Modifier
                        .width(50.dp)
                        .fillMaxHeight()
                        .padding(bottom = 72.dp)
                        .align(Alignment.CenterEnd)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    volumeLevel = (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat()
                                    showVolumeIndicator = true
                                },
                                onDragEnd = { showVolumeIndicator = false },
                                onDragCancel = { showVolumeIndicator = false },
                                onDrag = { _, dragAmount ->
                                    val delta = -dragAmount.y / 600f * maxVolume
                                    volumeLevel = (volumeLevel + delta).coerceIn(0f, maxVolume.toFloat())
                                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volumeLevel.toInt(), 0)
                                }
                            )
                        }
                )
                if (showVolumeIndicator) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                            .background(Color.Black.copy(0.55f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("${((volumeLevel / maxVolume) * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                    }
                }
            }

            // ── Lock overlay ─────────────────────────────────────────────
            if (isLocked) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { } },
                    contentAlignment = Alignment.TopCenter
                ) {
                    IconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.Lock, "Unlock", tint = Color.White)
                    }
                }
            }

            // ── Top bar buttons ──────────────────────────────────────────
            if (!isLocked) {
                Row(
                    modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isFullscreen = false }) {
                            Icon(Icons.Default.FullscreenExit, null, tint = Color.White)
                        }
                        if (sleepRemaining > 0L) {
                            val mm = sleepRemaining / 60
                            val ss = sleepRemaining % 60
                            Text(
                                "Sleep: %02d:%02d".format(mm, ss),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .background(Color.Black.copy(0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = { showSleepMenu = true }) {
                                Icon(Icons.Default.Bedtime, "Sleep timer", tint = if (sleepMinutes > 0) MaterialTheme.colorScheme.primary else Color.White)
                            }
                            DropdownMenu(expanded = showSleepMenu, onDismissRequest = { showSleepMenu = false }) {
                                listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "60 min").forEach { (mins, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontWeight = if (sleepMinutes == mins) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = { sleepMinutes = mins; showSleepMenu = false }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { isLocked = true }) {
                            Icon(Icons.Default.LockOpen, "Lock", tint = Color.White)
                        }
                        IconButton(onClick = { showStats = !showStats }) {
                            Icon(Icons.Default.Analytics, "Stats",
                                tint = if (showStats) MaterialTheme.colorScheme.primary else Color.White)
                        }
                        IconButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val params = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                                    activity?.enterPictureInPictureMode(params)
                                }
                            }
                        ) {
                            Icon(Icons.Default.PictureInPicture, "PiP", tint = Color.White)
                        }
                    }
                }
            }

            // ── Stats overlay ────────────────────────────────────────────
            if (showStats) {
                val dur = mediaController?.duration?.takeIf { it > 0 } ?: 1L
                val pos = mediaController?.currentPosition ?: 0L
                Box(
                    Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 80.dp)
                        .background(Color.Black.copy(0.7f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text("Buffer: $bufferPct%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("Position: ${pos / 1000}s / ${dur / 1000}s", color = Color.White, fontSize = 11.sp)
                        Text("Zoom: ${"%.1f".format(zoom)}x", color = Color.White, fontSize = 11.sp)
                    }
                }
            }

            // ── Autoplay countdown overlay ───────────────────────────────
            if (autoPlayCountdown > 0) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 80.dp)
                        .background(Color.Black.copy(0.8f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Auto-playing next in", color = Color.White.copy(0.8f), fontSize = 11.sp)
                        Text("$autoPlayCountdown", color = MaterialTheme.colorScheme.primary,
                            fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { autoPlayTarget = "" }) {
                            Text("Cancel", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        return
    }

    // ── Portrait layout ───────────────────────────────────────────────────────
    LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
            ) {
                when (val s = state) {
                    is PlayerUiState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(40.dp),
                        color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp
                    )
                    is PlayerUiState.Error -> Column(
                        Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(s.message, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { vm.loadVideo(videoUrl) }) { Text("Retry") }
                    }
                    is PlayerUiState.Ready -> {
                        AndroidView(
                            factory = { ctx -> PlayerView(ctx).apply { player = mediaController; useController = true; keepScreenOn = true } },
                            update = { pv -> pv.player = mediaController },
                            modifier = Modifier.fillMaxSize()
                        )
                        DoubleTapZones()
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SeekFeedback() }
                    }
                }
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                IconButton(onClick = { isFullscreen = true }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    Icon(Icons.Default.Fullscreen, null, tint = Color.White)
                }
                if (autoPlayCountdown > 0) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            .background(Color.Black.copy(0.8f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Next in $autoPlayCountdown s", color = Color.White, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { autoPlayTarget = "" },
                                contentPadding = PaddingValues(0.dp)) {
                                Text("Cancel", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        if (state is PlayerUiState.Ready) {
            val details = (state as PlayerUiState.Ready).details

            item {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            details.title,
                            style = MaterialTheme.typography.titleSmall.copy(lineHeight = 21.sp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(details.uploaderName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                                if (details.viewCount > 0) Text("${formatViews(details.viewCount)} views", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (details.likeCount > 0) {
                                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                                        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ThumbUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(formatViews(details.likeCount), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                IconButton(onClick = {
                                    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, videoUrl) }
                                    context.startActivity(Intent.createChooser(i, "Share video"))
                                }) { Icon(Icons.Default.Share, "Share", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
                                IconButton(onClick = { vm.toggleWatchLater() }) {
                                    Icon(
                                        if (isInWatchLater) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        "Watch Later",
                                        tint = if (isInWatchLater) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                IconButton(onClick = { vm.toggleFavorite() }) {
                                    Icon(
                                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        "Favourite", tint = heartColor, modifier = Modifier.size(24.dp).scale(heartScale)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Speed", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box {
                                TextButton(onClick = { showSpeedMenu = true }) {
                                    Text("${currentSpeed}x", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                                    speeds.forEach { speed ->
                                        DropdownMenuItem(
                                            text = { Text("${speed}x", fontWeight = if (speed == currentSpeed) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = { currentSpeed = speed; mediaController?.setPlaybackSpeed(speed); showSpeedMenu = false }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            // Repeat button
                            IconButton(onClick = {
                                repeatMode = if (repeatMode == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                            }) {
                                Icon(
                                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                    "Repeat",
                                    tint = if (repeatMode == Player.REPEAT_MODE_ONE) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (details.description.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(12.dp))
                            var expanded by remember { mutableStateOf(false) }
                            Text(
                                details.description, fontSize = 13.sp, lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                maxLines = if (expanded) Int.MAX_VALUE else 3,
                                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = !expanded }
                            )
                            Text(
                                if (expanded) "Show less" else "Show more", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 5.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = !expanded }
                            )
                        }
                    }
                }
            }

            if (details.relatedVideos.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Up Next",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                items(details.relatedVideos, key = { it.url }) { video ->
                    Box(Modifier.padding(horizontal = 14.dp)) {
                        VideoCard(video = video, onClick = { onVideoClick(video.url) })
                    }
                }
            }
        }
    }
}
