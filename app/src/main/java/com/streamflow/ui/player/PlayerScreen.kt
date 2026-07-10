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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
import com.streamflow.data.PlaybackQueue
import com.streamflow.data.local.AppPreferences
import com.streamflow.ui.components.MiniPlayerData
import com.streamflow.ui.components.MiniPlayerState
import com.streamflow.ui.components.VideoCard
import com.streamflow.ui.components.formatViews
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@android.annotation.SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoUrl: String,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onChannelClick: ((String) -> Unit)? = null,
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

    // ── Horizontal scrub (fullscreen) ────────────────────────────────────────
    var isScrubbing    by remember { mutableStateOf(false) }
    var scrubTargetMs  by remember { mutableLongStateOf(0L) }
    var scrubStartMs   by remember { mutableLongStateOf(0L) }

    // ── Comments ─────────────────────────────────────────────────────────────
    val comments         by vm.comments.collectAsState()
    val commentsLoading  by vm.commentsLoading.collectAsState()
    var showComments     by remember { mutableStateOf(false) }

    // ── Queue ─────────────────────────────────────────────────────────────────
    val queue           by vm.queue.collectAsState()
    var showQueueSheet  by remember { mutableStateOf(false) }

    // ── SponsorBlock ──────────────────────────────────────────────────────────
    val sponsorSegments  by vm.sponsorSegments.collectAsState()
    var showSkipBanner   by remember { mutableStateOf(false) }
    var skipBannerLabel  by remember { mutableStateOf("") }

    // ── Audio-only ────────────────────────────────────────────────────────────
    val audioOnly        by vm.audioOnly.collectAsState()

    // ── Current chapter ───────────────────────────────────────────────────────
    var currentChapterTitle by remember { mutableStateOf("") }

    // ── Portrait progress controls ────────────────────────────────────────────
    var playerPosition by remember { mutableLongStateOf(0L) }
    var playerDuration by remember { mutableLongStateOf(0L) }
    var playerIsPlaying by remember { mutableStateOf(false) }
    var isSeekingPortrait by remember { mutableStateOf(false) }
    var seekTargetPortrait by remember { mutableLongStateOf(0L) }

    // ── Fullscreen controls overlay ───────────────────────────────────────────
    var showFsControls by remember { mutableStateOf(true) }
    var fsTapTimestamp by remember { mutableLongStateOf(0L) }

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

    // ── Load sponsor segments on video load ───────────────────────────────────
    LaunchedEffect(videoUrl) { vm.loadSponsorSegments(videoUrl) }

    // ── SponsorBlock auto-skip ────────────────────────────────────────────────
    LaunchedEffect(sponsorSegments) {
        if (sponsorSegments.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(300L)
            val mc  = mediaController ?: continue
            val pos = mc.currentPosition
            val seg = sponsorSegments.firstOrNull { pos >= it.startMs && pos < it.endMs }
            if (seg != null) {
                mc.seekTo(seg.endMs)
                skipBannerLabel = seg.label
                showSkipBanner  = true
                delay(2000L)
                showSkipBanner  = false
            }
        }
    }

    // ── Chapter tracking ──────────────────────────────────────────────────────
    LaunchedEffect(state) {
        val chs = (state as? PlayerUiState.Ready)?.details?.chapters ?: return@LaunchedEffect
        if (chs.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(500L)
            val pos = mediaController?.currentPosition ?: 0L
            currentChapterTitle = chs.lastOrNull { pos >= it.startMs }?.title ?: ""
        }
    }

    // ── Position / playback state polling ─────────────────────────────────────
    LaunchedEffect(mediaController) {
        while (true) {
            delay(250L)
            val mc = mediaController ?: continue
            if (!isSeekingPortrait) playerPosition = mc.currentPosition.coerceAtLeast(0L)
            playerDuration = mc.duration.coerceAtLeast(0L)
            playerIsPlaying = mc.isPlaying
        }
    }

    // ── Fullscreen controls auto-hide (3 s after last interaction) ────────────
    LaunchedEffect(fsTapTimestamp) {
        if (fsTapTimestamp == 0L) return@LaunchedEffect
        delay(3000L)
        showFsControls = false
    }

    // ── Subtitle state ───────────────────────────────────────────────────────
    var showSubMenu by remember { mutableStateOf(false) }
    var selectedSubUrl by remember { mutableStateOf("") }
    var subUrlSeenByEffect by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedSubUrl, mediaController) {
        if (subUrlSeenByEffect == null) { subUrlSeenByEffect = selectedSubUrl; return@LaunchedEffect }
        if (selectedSubUrl == subUrlSeenByEffect) return@LaunchedEffect
        subUrlSeenByEffect = selectedSubUrl
        val mc = mediaController ?: return@LaunchedEffect
        val item = mc.currentMediaItem ?: return@LaunchedEffect
        val pos = mc.currentPosition
        val newItem = if (selectedSubUrl.isEmpty()) {
            item.buildUpon().setSubtitleConfigurations(emptyList()).build()
        } else {
            item.buildUpon().setSubtitleConfigurations(listOf(
                MediaItem.SubtitleConfiguration.Builder(selectedSubUrl.toUri())
                    .setMimeType("text/vtt").build()
            )).build()
        }
        mc.setMediaItem(newItem, pos)
        mc.prepare()
        mc.play()
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
                // Queue takes priority over related video auto-play
                if (PlaybackQueue.hasNext()) {
                    PlaybackQueue.popNext()?.let { onVideoClick(it.url) }; break
                }
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
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            Box(
                Modifier.weight(0.3f).fillMaxHeight()
                    .pointerInput(skipMs) {
                        detectTapGestures(
                            onTap = { showFsControls = !showFsControls; fsTapTimestamp = System.currentTimeMillis() },
                            onDoubleTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                mc.seekTo((mc.currentPosition - skipMs).coerceAtLeast(0))
                                seekFeedback = "- ${skipSec}s"; showSeekFeedback = true
                            })
                    }
            )
            Box(Modifier.weight(0.4f).fillMaxHeight()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            scrubStartMs  = mc.currentPosition
                            scrubTargetMs = scrubStartMs
                            isScrubbing   = true
                        },
                        onHorizontalDrag = { _, dx ->
                            val dur = mc.duration.coerceAtLeast(1L)
                            scrubTargetMs = (scrubStartMs + (dx / 700f * dur).toLong()).coerceIn(0L, dur)
                        },
                        onDragEnd    = { mc.seekTo(scrubTargetMs); isScrubbing = false },
                        onDragCancel = { isScrubbing = false }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { showFsControls = !showFsControls; fsTapTimestamp = System.currentTimeMillis() })
                })
            Box(
                Modifier.weight(0.3f).fillMaxHeight()
                    .pointerInput(skipMs) {
                        detectTapGestures(
                            onTap = { showFsControls = !showFsControls; fsTapTimestamp = System.currentTimeMillis() },
                            onDoubleTap = {
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
                        useController = false
                        keepScreenOn = true
                    }
                },
                update = { pv ->
                    pv.player = mediaController
                    pv.useController = false
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

            // ── Top bar ──────────────────────────────────────────────────
            AnimatedVisibility(
                visible = showFsControls && !isLocked,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.8f), Color.Transparent)))
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            IconButton(onClick = { isFullscreen = false }) {
                                Icon(Icons.Default.FullscreenExit, null, tint = Color.White)
                            }
                            val fsTitle = (state as? PlayerUiState.Ready)?.details?.title ?: ""
                            if (fsTitle.isNotEmpty()) {
                                Text(
                                    fsTitle, color = Color.White, fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            if (sleepRemaining > 0L) {
                                val mm = sleepRemaining / 60
                                val ss = sleepRemaining % 60
                                Text(
                                    "Sleep: %02d:%02d".format(mm, ss),
                                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .background(Color.Black.copy(0.5f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                IconButton(onClick = { showSleepMenu = true }) {
                                    Icon(Icons.Default.Bedtime, "Sleep timer",
                                        tint = if (sleepMinutes > 0) MaterialTheme.colorScheme.primary else Color.White)
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
                            IconButton(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val params = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                                    activity?.enterPictureInPictureMode(params)
                                }
                            }) {
                                Icon(Icons.Default.PictureInPicture, "PiP", tint = Color.White)
                            }
                        }
                    }
                }
            }

            // ── Stats overlay ────────────────────────────────────────────
            if (showStats) {
                val dur = mediaController?.duration?.takeIf { it > 0 } ?: 1L
                val pos = mediaController?.currentPosition ?: 0L
                Box(
                    Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 130.dp)
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

            // ── Scrub preview ────────────────────────────────────────────
            if (isScrubbing) {
                Box(Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(0.7f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)) {
                    val h = scrubTargetMs / 3_600_000
                    val m = (scrubTargetMs % 3_600_000) / 60_000
                    val s = (scrubTargetMs % 60_000) / 1_000
                    Text(if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s),
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ── Skip sponsor banner ──────────────────────────────────────
            if (showSkipBanner) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("Skipping $skipBannerLabel",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Chapter name ─────────────────────────────────────────────
            if (currentChapterTitle.isNotEmpty() && !isLocked) {
                Text(currentChapterTitle,
                    color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 120.dp)
                        .background(Color.Black.copy(0.5f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp))
            }

            // ── Autoplay countdown overlay ───────────────────────────────
            if (autoPlayCountdown > 0) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 110.dp)
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

            // ── Fullscreen bottom control bar ────────────────────────────
            AnimatedVisibility(
                visible = showFsControls && !isLocked,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f))))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        if (playerDuration > 0L) {
                            Slider(
                                value = (if (isScrubbing) scrubTargetMs else playerPosition).toFloat(),
                                onValueChange = { v ->
                                    scrubTargetMs = v.toLong(); isScrubbing = true
                                },
                                onValueChangeFinished = {
                                    mediaController?.seekTo(scrubTargetMs); isScrubbing = false
                                },
                                valueRange = 0f..playerDuration.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(0.35f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                val mc = mediaController ?: return@IconButton
                                if (mc.isPlaying) mc.pause() else mc.play()
                            }) {
                                Icon(
                                    if (playerIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    null, tint = Color.White, modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${fmtMs(playerPosition)} / ${fmtMs(playerDuration)}",
                                color = Color.White, fontSize = 12.sp
                            )
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
                            factory = { ctx -> PlayerView(ctx).apply { player = mediaController; useController = false; keepScreenOn = !audioOnly } },
                            update  = { pv -> pv.player = mediaController; pv.useController = false },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (audioOnly) {
                            // Audio-only overlay
                            Box(Modifier.fillMaxSize().background(Color(0xFF0D0D0D)),
                                contentAlignment = Alignment.Center) {
                                coil.compose.AsyncImage(
                                    model = (state as PlayerUiState.Ready).details.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    alpha = 0.3f
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.MusicNote, null, tint = Color.White,
                                        modifier = Modifier.size(64.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text("Audio Only", color = Color.White,
                                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                            }
                        }
                        if (!audioOnly) {
                            DoubleTapZones()
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SeekFeedback() }
                        }
                        // Skip sponsor banner (portrait)
                        if (showSkipBanner) {
                            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)) {
                                Text("Skipping $skipBannerLabel",
                                    color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp)
                            }
                        }
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

            // ── Portrait control bar ──────────────────────────────────────────
            item {
                Box(Modifier.fillMaxWidth().background(Color(0xFF0A0A0A))) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
                        if (playerDuration > 0L) {
                            Slider(
                                value = (if (isSeekingPortrait) seekTargetPortrait else playerPosition).toFloat(),
                                onValueChange = { v -> seekTargetPortrait = v.toLong(); isSeekingPortrait = true },
                                onValueChangeFinished = {
                                    mediaController?.seekTo(seekTargetPortrait); isSeekingPortrait = false
                                },
                                valueRange = 0f..playerDuration.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(0.25f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { mediaController?.let { mc -> if (mc.isPlaying) mc.pause() else mc.play() } },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        if (playerIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        null, tint = Color.White, modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${fmtMs(playerPosition)} / ${fmtMs(playerDuration)}",
                                    color = Color.White.copy(0.85f), fontSize = 12.sp
                                )
                            }
                            IconButton(
                                onClick = { isFullscreen = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Fullscreen, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

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
                            // Audio-only toggle
                            IconButton(onClick = { vm.toggleAudioOnly() }) {
                                Icon(Icons.Default.MusicNote, "Audio only",
                                    tint = if (audioOnly) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurface.copy(0.6f),
                                    modifier = Modifier.size(20.dp))
                            }
                            // Queue button
                            Box {
                                IconButton(onClick = { showQueueSheet = true }) {
                                    Icon(Icons.Default.QueueMusic, "Queue",
                                        tint = if (queue.isNotEmpty()) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurface.copy(0.6f),
                                        modifier = Modifier.size(20.dp))
                                }
                                if (queue.isNotEmpty()) {
                                    Badge(Modifier.align(Alignment.TopEnd)) { Text("${queue.size}") }
                                }
                            }
                            // Subtitle (CC)
                            if (details.subtitles.isNotEmpty()) {
                                Box {
                                    IconButton(onClick = { showSubMenu = true }) {
                                        Icon(Icons.Default.ClosedCaption, "Subtitles",
                                            tint = if (selectedSubUrl.isNotEmpty()) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurface.copy(0.6f),
                                            modifier = Modifier.size(20.dp))
                                    }
                                    DropdownMenu(expanded = showSubMenu, onDismissRequest = { showSubMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Off", fontWeight = if (selectedSubUrl.isEmpty()) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = { selectedSubUrl = ""; showSubMenu = false })
                                        details.subtitles.forEach { sub ->
                                            DropdownMenuItem(
                                                text = { Text(sub.name, fontWeight = if (selectedSubUrl == sub.url) FontWeight.Bold else FontWeight.Normal) },
                                                onClick = { selectedSubUrl = sub.url; showSubMenu = false })
                                        }
                                    }
                                }
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

            // ── Chapters ──────────────────────────────────────────────────────
            if (details.chapters.isNotEmpty()) {
                item(key = "chapters_header") {
                    var chapExpanded by remember { mutableStateOf(false) }
                    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { chapExpanded = !chapExpanded }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.List, null,
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text("Chapters  (${details.chapters.size})",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground)
                                }
                                Icon(if (chapExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (chapExpanded) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
                                details.chapters.forEach { ch ->
                                    val h = ch.startMs / 3_600_000; val m = (ch.startMs % 3_600_000) / 60_000; val s = (ch.startMs % 60_000) / 1_000
                                    val timeLabel = if (h > 0) "%d:%02d:%02d".format(h,m,s) else "%d:%02d".format(m,s)
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                                mediaController?.seekTo(ch.startMs)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(timeLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(48.dp))
                                        Text(ch.title, fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onBackground)
                                    }
                                    HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(0.1f))
                                }
                            }
                        }
                    }
                }
            }

            // ── Comments ──────────────────────────────────────────────────────
            item(key = "comments_header") {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!showComments) vm.loadComments(videoUrl)
                                showComments = !showComments
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Comment, null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text(
                                    if (comments.isEmpty()) "Comments" else "Comments  (${comments.size})",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            if (commentsLoading) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(if (showComments) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            if (showComments) {
                items(comments.take(30), key = { "c_${it.author}_${it.text.take(20)}" }) { comment ->
                    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (comment.avatarUrl.isNotEmpty()) {
                                    coil.compose.AsyncImage(comment.avatarUrl, null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize())
                                } else {
                                    Text(comment.author.firstOrNull()?.uppercase() ?: "?",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(comment.author, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                                        color = if (comment.isOwnerComment) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onBackground)
                                    if (comment.publishedTime.isNotEmpty()) {
                                        Text(comment.publishedTime, fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(comment.text, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onBackground, lineHeight = 18.sp)
                                if (comment.likeCount > 0) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Icon(Icons.Default.ThumbUp, null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp))
                                        Text(formatViews(comment.likeCount), fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(0.1f))
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
                        VideoCard(video = video, onClick = { onVideoClick(video.url) },
                            onChannelClick = onChannelClick?.let { cb -> { url: String -> cb(url) } })
                    }
                }
            }
        }
    }

    // ── Queue bottom sheet ────────────────────────────────────────────────────
    if (showQueueSheet) {
        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Queue  (${queue.size})", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    if (queue.isNotEmpty()) {
                        TextButton(onClick = { PlaybackQueue.clear() }) { Text("Clear all") }
                    }
                }
                HorizontalDivider()
                if (queue.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("Queue is empty — long-press a video and tap 'Add to queue'",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp))
                    }
                } else {
                    queue.forEachIndexed { idx, video ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("${idx + 1}", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(20.dp))
                            coil.compose.AsyncImage(
                                model = video.thumbnailUrl, contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(width = 80.dp, height = 45.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(video.title, fontSize = 13.sp, maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground)
                                Text(video.uploaderName, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { PlaybackQueue.remove(idx) },
                                modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(0.1f))
                    }
                }
            }
        }
    }
}

private fun fmtMs(ms: Long): String {
    val s = ms / 1000L; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
