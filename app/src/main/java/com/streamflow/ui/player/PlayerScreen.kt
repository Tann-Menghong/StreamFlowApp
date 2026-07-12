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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.streamflow.MainActivity
import com.streamflow.PlaybackService
import com.streamflow.data.PlaybackQueue
import com.streamflow.data.ai.AiEngine
import com.streamflow.data.local.AppPreferences
import com.streamflow.ui.components.MiniPlayerData
import com.streamflow.ui.components.MiniPlayerState
import com.streamflow.ui.components.VideoCard
import com.streamflow.ui.components.formatViews
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Drag distance (px) past which releasing the swipe-down-to-minimize gesture
// commits to minimizing; shared by the gesture handler and its visual feedback
// so the on-screen shrink/fade finishes exactly when the action arms.
private const val MINIMIZE_THRESHOLD_PX = 220f

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
    val isSubscribed by vm.isSubscribed.collectAsState()
    val autoQuality by vm.autoQuality.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = AppPreferences.get(context)

    val isDirectStream = remember(videoUrl) {
        val lower = videoUrl.lowercase()
        lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".webm") ||
        lower.contains("/hls/") || lower.contains("/stream/")
    }
    // Like YouTube: open in portrait; fullscreen only when the user asks
    var isFullscreen by remember { mutableStateOf(false) }

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
            // Save only when the controller is really on this video and past the
            // start — otherwise a not-yet-loaded or different item would wipe the
            // stored resume position with 0
            val mc = latestController.value ?: return@onDispose
            val pos = try { mc.currentPosition } catch (_: Exception) { 0L }
            val onThisVideo = try { mc.currentMediaItem?.mediaId == videoUrl } catch (_: Exception) { false }
            if (onThisVideo && pos > 1_000L) vm.savePosition(videoUrl, pos)
        }
    }

    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    LaunchedEffect(videoUrl) { vm.loadVideo(videoUrl) }

    // ── Controls overlay visibility (portrait + fullscreen) ──────────────────
    var showFsControls by remember { mutableStateOf(true) }
    var fsTapTimestamp by remember { mutableLongStateOf(0L) }

    // ── Update MiniPlayerState when ready ────────────────────────────────────
    LaunchedEffect(state) {
        val r = state as? PlayerUiState.Ready ?: return@LaunchedEffect
        fsTapTimestamp = System.currentTimeMillis() // start the controls auto-hide timer
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
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(d.streamUrl)
            // mediaId = video URL, so position saves can verify which video the
            // controller is actually playing (stream URLs change per quality)
            .setMediaId(videoUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(d.title)
                    .setArtist(d.uploaderName)
                    .setArtworkUri(d.thumbnailUrl.toUri())
                    .build()
            )
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
        if (d.isLive) {
            // Live manifest URLs often lack a file extension — give ExoPlayer the type
            mediaItemBuilder.setMimeType(
                if (d.streamUrl.contains("mpd", true) || d.streamUrl.contains("dash", true))
                    MimeTypes.APPLICATION_MPD else MimeTypes.APPLICATION_M3U8
            )
        }
        // Resume where the user left off — read BEFORE setMediaItem and hand the
        // position to the player atomically; a seekTo() after play() can be lost
        // if this effect restarts or another command lands in between.
        // Skip resuming when they'd basically finished (>95%): start over like YouTube.
        val savedPos = if (d.isLive) 0L else vm.getSavedPosition(videoUrl)
        val nearEnd = d.duration > 0L && savedPos >= d.duration * 1000L * 95 / 100
        val startPos = if (savedPos > 3_000L && !nearEnd) savedPos else 0L
        if (startPos > 0L) mc.setMediaItem(mediaItemBuilder.build(), startPos)
        else mc.setMediaItem(mediaItemBuilder.build())
        mc.prepare()
        mc.play()

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
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    val aiOutput by vm.aiOutput.collectAsState()
    val aiBusy by vm.aiBusy.collectAsState()
    var currentSpeed by remember { mutableFloatStateOf(1f) }
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    LaunchedEffect(Unit) { currentSpeed = prefs.defaultSpeed.first().toFloatOrNull() ?: 1f }
    var skipMs by remember { mutableLongStateOf(10_000L) }
    LaunchedEffect(Unit) { skipMs = (prefs.skipSeconds.first().toLongOrNull() ?: 10L) * 1000L }
    // Swipe brightness/volume zones can be turned off in Settings > Playback
    val playerGesturesOn by prefs.playerGestures.collectAsState(initial = true)

    // Double-tap seek feedback: repeated taps accumulate (10s, 20s, 30s…)
    var seekAccumMs by remember { mutableLongStateOf(0L) }
    var seekAccumDir by remember { mutableIntStateOf(0) } // -1 back, +1 forward
    var seekAccumAt by remember { mutableLongStateOf(0L) }
    var showSeekFeedback by remember { mutableStateOf(false) }
    if (showSeekFeedback) {
        LaunchedEffect(seekAccumAt) { delay(800); showSeekFeedback = false; seekAccumDir = 0 }
    }
    val registerDoubleTapSeek: (Int, Long) -> Unit = { dir, skip ->
        val now = System.currentTimeMillis()
        seekAccumMs = if (seekAccumDir == dir && now - seekAccumAt < 1500) seekAccumMs + skip else skip
        seekAccumDir = dir
        seekAccumAt = now
        showSeekFeedback = true
    }

    // ── Horizontal scrub (fullscreen) ────────────────────────────────────────
    var isScrubbing    by remember { mutableStateOf(false) }
    var scrubTargetMs  by remember { mutableLongStateOf(0L) }
    var scrubStartMs   by remember { mutableLongStateOf(0L) }

    // ── Comments ─────────────────────────────────────────────────────────────
    val comments         by vm.comments.collectAsState()
    val commentsLoading  by vm.commentsLoading.collectAsState()
    val repliesMap       by vm.replies.collectAsState()
    val repliesLoading   by vm.repliesLoading.collectAsState()
    var showComments     by remember { mutableStateOf(false) }
    var commentsSortByLikes by remember { mutableStateOf(false) }

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

    // ── Repeat mode ──────────────────────────────────────────────────────────
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    LaunchedEffect(repeatMode) { mediaController?.repeatMode = repeatMode }

    // ── A–B section loop ─────────────────────────────────────────────────────
    var loopA by remember { mutableStateOf<Long?>(null) }
    var loopB by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(videoUrl) { loopA = null; loopB = null }
    LaunchedEffect(loopA, loopB, mediaController) {
        val a = loopA ?: return@LaunchedEffect
        val b = loopB ?: return@LaunchedEffect
        while (true) {
            delay(250L)
            val mc = mediaController ?: continue
            if (mc.currentPosition >= b) mc.seekTo(a)
        }
    }

    // ── Auto-save position every 5 s while playing ──────────────────────────
    LaunchedEffect(videoUrl, mediaController) {
        while (true) {
            delay(5_000L)
            val mc = mediaController ?: continue
            // mediaId check: while this screen is loading, the controller may
            // still be playing the previous video (mini player) — don't write
            // its position under this video's URL
            if (mc.isPlaying && mc.currentPosition > 1_000L &&
                mc.currentMediaItem?.mediaId == videoUrl) {
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
    // Stays visible while paused or scrubbing; timer restarts on every interaction.
    LaunchedEffect(fsTapTimestamp, playerIsPlaying, isScrubbing, isSeekingPortrait) {
        if (fsTapTimestamp == 0L) return@LaunchedEffect
        if (!playerIsPlaying || isScrubbing || isSeekingPortrait) return@LaunchedEffect
        delay(3000L)
        showFsControls = false
    }

    // ── Quality menu state ───────────────────────────────────────────────────
    var showQualityMenu   by remember { mutableStateOf(false) }
    var showFsQualityMenu by remember { mutableStateOf(false) }

    // ── Hold-for-2x speed boost ──────────────────────────────────────────────
    var speedBoost by remember { mutableStateOf(false) }

    // ── Swipe-down-to-minimize (portrait) ────────────────────────────────────
    // Uses nestedScroll (not a raw pointerInput drag) so it never fights the
    // page's own vertical scroll: it only claims a downward drag once the list
    // is already scrolled to the very top and has nothing left to consume —
    // exactly the same "overscroll at top" signal pull-to-refresh uses elsewhere.
    var minimizeDrag by remember { mutableFloatStateOf(0f) }
    val playerListState = rememberLazyListState()
    val minimizeHaptic = LocalHapticFeedback.current
    // Ticks a haptic once as the drag crosses the point where releasing would
    // commit to minimizing — the same "about to trigger" feedback iOS uses on
    // pull-to-reveal actions, so the gesture has a tactile "lock-in" moment
    // instead of just a threshold you have to guess.
    var minimizeArmed by remember { mutableStateOf(false) }
    // Bumped on every fresh drag input so a spring-back from a PREVIOUS release
    // can detect a new grab starting mid-animation and bail out instead of
    // fighting the new drag for control of minimizeDrag.
    var minimizeGeneration by remember { mutableIntStateOf(0) }
    val minimizeScrollConnection = remember {
        object : NestedScrollConnection {
            fun applyDrag(delta: Float) {
                minimizeGeneration++
                minimizeDrag = (minimizeDrag + delta).coerceAtLeast(0f)
                val nowArmed = minimizeDrag > MINIMIZE_THRESHOLD_PX
                if (nowArmed != minimizeArmed) {
                    minimizeArmed = nowArmed
                    minimizeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (minimizeDrag > 0f && available.y != 0f) {
                    applyDrag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val atTop = playerListState.firstVisibleItemIndex == 0 &&
                    playerListState.firstVisibleItemScrollOffset == 0
                if (atTop && available.y > 0f) {
                    applyDrag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (minimizeDrag > 0f) {
                    // Commit on a clear pull past the threshold, OR a fast downward
                    // flick from partway — matches how sheet-dismiss gestures work
                    // elsewhere on Android instead of relying on distance alone.
                    val shouldMinimize = minimizeDrag > MINIMIZE_THRESHOLD_PX ||
                        (minimizeDrag > 70f && available.y > 1200f)
                    minimizeArmed = false
                    if (shouldMinimize) {
                        minimizeDrag = 0f
                        onBack()
                    } else {
                        // Smooth spring back instead of an instant snap to 0 — bails
                        // out early if the user already grabbed a new drag before
                        // this animation finished, instead of racing it.
                        val myGeneration = minimizeGeneration
                        animate(
                            initialValue = minimizeDrag,
                            targetValue = 0f,
                            initialVelocity = available.y.coerceAtMost(0f),
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium)
                        ) { value, _ ->
                            if (minimizeGeneration == myGeneration) minimizeDrag = value
                        }
                    }
                }
                return Velocity.Zero
            }
        }
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
                    .setMimeType("text/vtt")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
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

    // YouTube-style side ripple: a rounded scrim on the tapped half with the
    // accumulated skip amount
    @Composable
    fun SeekFeedback() {
        if (!showSeekFeedback || seekAccumDir == 0) return
        val back = seekAccumDir < 0
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .align(if (back) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.3f)
                    .clip(
                        if (back) RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100)
                        else RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100)
                    )
                    .background(Color.White.copy(0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (back) "◀◀◀" else "▶▶▶",
                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text("${seekAccumMs / 1000}s",
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    @Composable
    fun DoubleTapZones() {
        val mc = mediaController ?: return
        val skipSec = skipMs / 1000L
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()

        // YouTube-style: press & hold anywhere on the video for 2x speed
        val holdBoost: suspend PressGestureScope.(Offset) -> Unit = {
            val job = scope.launch {
                delay(500L)
                mc.setPlaybackSpeed(2f)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                speedBoost = true
            }
            tryAwaitRelease()
            job.cancel()
            if (speedBoost) {
                mc.setPlaybackSpeed(currentSpeed)
                speedBoost = false
            }
        }

        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            Box(
                Modifier.weight(0.3f).fillMaxHeight()
                    .pointerInput(skipMs) {
                        detectTapGestures(
                            onPress = holdBoost,
                            onTap = { showFsControls = !showFsControls; fsTapTimestamp = System.currentTimeMillis() },
                            onDoubleTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                mc.seekTo((mc.currentPosition - skipMs).coerceAtLeast(0))
                                registerDoubleTapSeek(-1, skipMs)
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
                    detectTapGestures(
                        onPress = holdBoost,
                        onTap = { showFsControls = !showFsControls; fsTapTimestamp = System.currentTimeMillis() })
                })
            Box(
                Modifier.weight(0.3f).fillMaxHeight()
                    .pointerInput(skipMs) {
                        detectTapGestures(
                            onPress = holdBoost,
                            onTap = { showFsControls = !showFsControls; fsTapTimestamp = System.currentTimeMillis() },
                            onDoubleTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                mc.seekTo(mc.currentPosition + skipMs)
                                registerDoubleTapSeek(1, skipMs)
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
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    }
                },
                update = { pv ->
                    pv.player = mediaController
                    pv.useController = false
                },
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }
            )

            // Loading / error state (video starts in fullscreen, so these must show here too)
            when (val s = state) {
                is PlayerUiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(44.dp),
                    color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp
                )
                is PlayerUiState.Error -> Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = Color.White.copy(0.7f),
                        modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(s.message, color = Color.White, fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { isFullscreen = false; onBack() }) {
                            Text("Go back", color = Color.White)
                        }
                        Button(onClick = { vm.loadVideo(videoUrl) }) { Text("Retry") }
                    }
                }
                else -> {}
            }

            if (!isLocked && state is PlayerUiState.Ready) {
                DoubleTapZones()
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SeekFeedback() }

            // ── FAR LEFT brightness zone ─────────────────────────────────
            if (!isLocked && playerGesturesOn) {
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
                        Icon(Icons.Rounded.Brightness6, null, tint = Color.White, modifier = Modifier.size(20.dp))
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
                        Icon(Icons.Rounded.VolumeUp, null, tint = Color.White, modifier = Modifier.size(20.dp))
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
                        Icon(Icons.Rounded.Lock, "Unlock", tint = Color.White)
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
                                Icon(Icons.Rounded.FullscreenExit, null, tint = Color.White)
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
                            // Quality selector (fullscreen)
                            val fsDetails = (state as? PlayerUiState.Ready)?.details
                            if (fsDetails != null && fsDetails.availableQualities.isNotEmpty()) {
                                Box {
                                    TextButton(onClick = { showFsQualityMenu = true; fsTapTimestamp = System.currentTimeMillis() }) {
                                        Text(
                                            when {
                                                autoQuality && fsDetails.currentQuality > 0 -> "Auto (${fsDetails.currentQuality}p)"
                                                fsDetails.currentQuality > 0 -> "${fsDetails.currentQuality}p"
                                                else -> "Auto"
                                            },
                                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White
                                        )
                                    }
                                    DropdownMenu(expanded = showFsQualityMenu, onDismissRequest = { showFsQualityMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Auto", fontWeight = if (autoQuality) FontWeight.Bold else FontWeight.Normal) },
                                            trailingIcon = {
                                                if (autoQuality) Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                                            },
                                            onClick = {
                                                showFsQualityMenu = false
                                                if (!autoQuality) {
                                                    vm.savePosition(videoUrl, mediaController?.currentPosition ?: 0L)
                                                    vm.rememberQuality(null)
                                                    vm.changeQuality(videoUrl, null)
                                                }
                                            }
                                        )
                                        fsDetails.availableQualities.forEach { h ->
                                            DropdownMenuItem(
                                                text = { Text("${h}p", fontWeight = if (!autoQuality && h == fsDetails.currentQuality) FontWeight.Bold else FontWeight.Normal) },
                                                trailingIcon = {
                                                    if (!autoQuality && h == fsDetails.currentQuality)
                                                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                                                },
                                                onClick = {
                                                    showFsQualityMenu = false
                                                    if (autoQuality || h != fsDetails.currentQuality) {
                                                        vm.savePosition(videoUrl, mediaController?.currentPosition ?: 0L)
                                                        vm.rememberQuality(h)
                                                        vm.changeQuality(videoUrl, h)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            Box {
                                IconButton(onClick = { showSleepMenu = true }) {
                                    Icon(Icons.Rounded.Bedtime, "Sleep timer",
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
                                Icon(Icons.Rounded.LockOpen, "Lock", tint = Color.White)
                            }
                            IconButton(onClick = { showStats = !showStats }) {
                                Icon(Icons.Rounded.Analytics, "Stats",
                                    tint = if (showStats) MaterialTheme.colorScheme.primary else Color.White)
                            }
                            IconButton(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val params = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                                    activity?.enterPictureInPictureMode(params)
                                }
                            }) {
                                Icon(Icons.Rounded.PictureInPicture, "PiP", tint = Color.White)
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
                        val stDetails = (state as? PlayerUiState.Ready)?.details
                        if (stDetails != null && stDetails.currentQuality > 0) {
                            Text("Quality: ${stDetails.currentQuality}p" +
                                (if (stDetails.videoCodec.isNotEmpty()) "  ·  ${stDetails.videoCodec}" else ""),
                                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("Buffer: $bufferPct%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("Position: ${pos / 1000}s / ${dur / 1000}s", color = Color.White, fontSize = 11.sp)
                        Text("Zoom: ${"%.1f".format(zoom)}x", color = Color.White, fontSize = 11.sp)
                    }
                }
            }

            // ── Center transport controls ────────────────────────────────
            AnimatedVisibility(
                visible = showFsControls && !isLocked && !isScrubbing && state is PlayerUiState.Ready,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    IconButton(onClick = {
                        mediaController?.let { mc -> mc.seekTo((mc.currentPosition - skipMs).coerceAtLeast(0L)) }
                        fsTapTimestamp = System.currentTimeMillis()
                    }) {
                        Icon(Icons.Rounded.Replay10, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(
                        onClick = {
                            mediaController?.let { mc -> if (mc.isPlaying) mc.pause() else mc.play() }
                            fsTapTimestamp = System.currentTimeMillis()
                        },
                        modifier = Modifier.size(64.dp).background(Color.Black.copy(0.45f), CircleShape)
                    ) {
                        Icon(
                            if (playerIsPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            null, tint = Color.White, modifier = Modifier.size(40.dp)
                        )
                    }
                    IconButton(onClick = {
                        mediaController?.let { mc -> mc.seekTo(mc.currentPosition + skipMs) }
                        fsTapTimestamp = System.currentTimeMillis()
                    }) {
                        Icon(Icons.Rounded.Forward10, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            }

            // ── Scrub preview (frame thumbnail + time + chapter) ─────────
            if (isScrubbing) {
                val scrubDetails = (state as? PlayerUiState.Ready)?.details
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    scrubDetails?.storyboard?.let { sb ->
                        StoryboardPreview(sb, scrubTargetMs,
                            Modifier.size(176.dp, 99.dp).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.height(8.dp))
                    }
                    Column(
                        Modifier.background(Color.Black.copy(0.7f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(fmtMs(scrubTargetMs),
                            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        val chTitle = scrubDetails?.chapters
                            ?.lastOrNull { scrubTargetMs >= it.startMs }?.title
                        if (!chTitle.isNullOrEmpty()) {
                            Text(chTitle, color = Color.White.copy(0.8f), fontSize = 12.sp, maxLines = 1)
                        }
                    }
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

            // ── 2x speed boost indicator ─────────────────────────────────
            if (speedBoost) {
                Row(Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                    .background(Color.Black.copy(0.65f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.FastForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("2x", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                    val fsIsLive = (state as? PlayerUiState.Ready)?.details?.isLive == true
                    val fsChapters = (state as? PlayerUiState.Ready)?.details?.chapters ?: emptyList()
                    Column(Modifier.fillMaxWidth()) {
                        if (playerDuration > 0L && !fsIsLive) {
                            Box(Modifier.fillMaxWidth()) {
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
                                ChapterTicks(fsChapters, playerDuration,
                                    Modifier.matchParentSize().padding(horizontal = 10.dp))
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                mediaController?.let { mc -> mc.seekTo((mc.currentPosition - skipMs).coerceAtLeast(0L)) }
                                fsTapTimestamp = System.currentTimeMillis()
                            }) {
                                Icon(Icons.Rounded.Replay10, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            IconButton(onClick = {
                                val mc = mediaController ?: return@IconButton
                                if (mc.isPlaying) mc.pause() else mc.play()
                                fsTapTimestamp = System.currentTimeMillis()
                            }) {
                                Icon(
                                    if (playerIsPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    null, tint = Color.White, modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton(onClick = {
                                mediaController?.let { mc -> mc.seekTo(mc.currentPosition + skipMs) }
                                fsTapTimestamp = System.currentTimeMillis()
                            }) {
                                Icon(Icons.Rounded.Forward10, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            if (queue.isNotEmpty()) {
                                IconButton(onClick = { PlaybackQueue.popNext()?.let { onVideoClick(it.url) } }) {
                                    Icon(Icons.Rounded.SkipNext, "Next in queue", tint = Color.White, modifier = Modifier.size(26.dp))
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            if (fsIsLive) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.size(8.dp).background(Color(0xFFE53935), CircleShape))
                                    Text("LIVE", color = Color.White, fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text(
                                    "${fmtMs(playerPosition)} / ${fmtMs(playerDuration)}",
                                    color = Color.White, fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // ── Portrait layout ───────────────────────────────────────────────────────
    // Ambient glow: the thumbnail's average color bleeds softly from behind the
    // player, like YouTube's ambient mode
    var ambientColor by remember(videoUrl) { mutableStateOf(Color.Transparent) }
    val batterySaverOn by prefs.batterySaver.collectAsState(initial = false)
    LaunchedEffect(state, batterySaverOn) {
        if (batterySaverOn) { ambientColor = Color.Transparent; return@LaunchedEffect }
        val d = (state as? PlayerUiState.Ready)?.details ?: return@LaunchedEffect
        if (d.thumbnailUrl.isNotEmpty()) {
            ambientColor = averageThumbColor(context, d.thumbnailUrl) ?: Color.Transparent
        }
    }
    val ambient by animateColorAsState(ambientColor, tween(700), label = "ambient")

    // statusBarsPadding: keep the portrait player below the clock/battery/wifi
    // status bar (fullscreen mode uses its own immersive Box and is unaffected)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Box(
        Modifier.fillMaxWidth().height(320.dp).background(
            Brush.verticalGradient(listOf(ambient.copy(alpha = 0.30f), Color.Transparent))
        )
    )
    LazyColumn(
        state = playerListState,
        modifier = Modifier.fillMaxSize().statusBarsPadding()
            .nestedScroll(minimizeScrollConnection)
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
                    .graphicsLayer {
                        // Progress reaches 1 right at the commit threshold, so what
                        // the user sees lines up with when the gesture will actually
                        // trigger — a corner radius grows in too, so the video visibly
                        // turns into a mini-player-shaped card as it shrinks.
                        val progress = (minimizeDrag / MINIMIZE_THRESHOLD_PX).coerceIn(0f, 1f)
                        translationY = minimizeDrag * 0.4f
                        scaleX = 1f - progress * 0.35f
                        scaleY = 1f - progress * 0.35f
                        alpha = 1f - progress * 0.35f
                        shape = RoundedCornerShape((progress * 14f).dp)
                        clip = true
                    }
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
                            factory = { ctx -> PlayerView(ctx).apply {
                                player = mediaController
                                useController = false
                                keepScreenOn = !audioOnly
                                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                            } },
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
                                    Icon(Icons.Rounded.MusicNote, null, tint = Color.White,
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

                            // ── YouTube-style controls overlay (tap video to toggle) ──
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showFsControls,
                                enter = fadeIn(), exit = fadeOut(),
                                modifier = Modifier.matchParentSize()
                            ) {
                                Box(
                                    Modifier.fillMaxSize()
                                        .background(Color.Black.copy(0.38f))
                                        .pointerInput(Unit) {
                                            detectTapGestures(onTap = { showFsControls = false })
                                        }
                                ) {
                                    // Center transport controls
                                    Row(
                                        Modifier.align(Alignment.Center),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                                    ) {
                                        IconButton(onClick = {
                                            mediaController?.let { mc -> mc.seekTo((mc.currentPosition - skipMs).coerceAtLeast(0L)) }
                                            fsTapTimestamp = System.currentTimeMillis()
                                        }) {
                                            Icon(Icons.Rounded.Replay10, null, tint = Color.White, modifier = Modifier.size(34.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                mediaController?.let { mc -> if (mc.isPlaying) mc.pause() else mc.play() }
                                                fsTapTimestamp = System.currentTimeMillis()
                                            },
                                            modifier = Modifier.size(64.dp).background(Color.Black.copy(0.35f), CircleShape)
                                        ) {
                                            Icon(
                                                if (playerIsPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                null, tint = Color.White, modifier = Modifier.size(44.dp)
                                            )
                                        }
                                        IconButton(onClick = {
                                            mediaController?.let { mc -> mc.seekTo(mc.currentPosition + skipMs) }
                                            fsTapTimestamp = System.currentTimeMillis()
                                        }) {
                                            Icon(Icons.Rounded.Forward10, null, tint = Color.White, modifier = Modifier.size(34.dp))
                                        }
                                    }

                                    // Bottom: time · queue-next · fullscreen, then seek bar
                                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (s.details.isLive) {
                                                Row(verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                                    Box(Modifier.size(8.dp).background(Color(0xFFE53935), CircleShape))
                                                    Text("LIVE", color = Color.White, fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                Text(
                                                    "${fmtMs(playerPosition)} / ${fmtMs(playerDuration)}",
                                                    color = Color.White, fontSize = 11.sp
                                                )
                                            }
                                            Spacer(Modifier.weight(1f))
                                            if (queue.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { PlaybackQueue.popNext()?.let { onVideoClick(it.url) } },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Rounded.SkipNext, "Next in queue",
                                                        tint = Color.White, modifier = Modifier.size(22.dp))
                                                }
                                            }
                                            IconButton(
                                                onClick = { isFullscreen = true },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Rounded.Fullscreen, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        if (playerDuration > 0L && !s.details.isLive) {
                                            Box(Modifier.fillMaxWidth()) {
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
                                                        inactiveTrackColor = Color.White.copy(0.3f)
                                                    ),
                                                    modifier = Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 4.dp)
                                                )
                                                ChapterTicks(s.details.chapters, playerDuration,
                                                    Modifier.matchParentSize().padding(horizontal = 12.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            // Seek preview while dragging the portrait seek bar
                            if (isSeekingPortrait) {
                                Column(
                                    Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    s.details.storyboard?.let { sb ->
                                        StoryboardPreview(sb, seekTargetPortrait,
                                            Modifier.size(144.dp, 81.dp).clip(RoundedCornerShape(8.dp)))
                                        Spacer(Modifier.height(6.dp))
                                    }
                                    val chTitle = s.details.chapters
                                        .lastOrNull { seekTargetPortrait >= it.startMs }?.title
                                    Text(
                                        fmtMs(seekTargetPortrait) +
                                            (if (!chTitle.isNullOrEmpty()) "  ·  $chTitle" else ""),
                                        color = Color.White, fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                                        modifier = Modifier
                                            .background(Color.Black.copy(0.65f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
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
                        // 2x speed boost indicator (portrait)
                        if (speedBoost) {
                            Row(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                                .background(Color.Black.copy(0.65f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.FastForward, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Text("2x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = showFsControls || state !is PlayerUiState.Ready,
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                        Icon(Icons.Rounded.ArrowBack, null, tint = Color.White)
                    }
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
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            details.title,
                            style = MaterialTheme.typography.titleSmall.copy(lineHeight = 21.sp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        // YouTube-style metadata line: "2.2M views · 2 months ago"
                        val metaLine = listOfNotNull(
                            details.viewCount.takeIf { it > 0 }?.let { "${formatViews(it)} views" },
                            details.uploadedAgo.takeIf { it.isNotBlank() }
                        ).joinToString("  ·  ")
                        if (metaLine.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(metaLine, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).then(
                                    if (onChannelClick != null && details.uploaderUrl.isNotEmpty())
                                        Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { onChannelClick(details.uploaderUrl) }
                                    else Modifier
                                )
                            ) {
                                com.streamflow.ui.components.ChannelAvatar(
                                    name      = details.uploaderName,
                                    avatarUrl = details.uploaderAvatarUrl,
                                    size      = 34.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(details.uploaderName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            color = if (onChannelClick != null && details.uploaderUrl.isNotEmpty())
                                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
                                        if (onChannelClick != null && details.uploaderUrl.isNotEmpty()) {
                                            Icon(Icons.Rounded.ChevronRight, null,
                                                tint = MaterialTheme.colorScheme.primary.copy(0.7f),
                                                modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                            if (details.uploaderUrl.isNotEmpty()) {
                                Button(
                                    onClick = { vm.toggleSubscribe() },
                                    colors = if (isSubscribed)
                                        ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    else ButtonDefaults.buttonColors(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp).padding(end = 4.dp)
                                ) {
                                    if (isSubscribed) {
                                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(3.dp))
                                    }
                                    Text(if (isSubscribed) "Subscribed" else "Subscribe",
                                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // YouTube-style labeled pill chips, on their own scrollable row so
                        // they can never squeeze the channel name (the old "big space" bug)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        ) {
                            // Springy bounce when the Like pill turns on
                            val likeScale = remember { Animatable(1f) }
                            LaunchedEffect(isFavorite) {
                                if (isFavorite) {
                                    likeScale.snapTo(0.7f)
                                    likeScale.animateTo(1f, spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium))
                                }
                            }
                            val chipHaptic = LocalHapticFeedback.current
                            ActionChip(
                                icon = if (isFavorite) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt,
                                label = if (details.likeCount > 0) formatViews(details.likeCount) else "Like",
                                active = isFavorite,
                                modifier = Modifier.scale(likeScale.value)
                            ) {
                                chipHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.toggleFavorite()
                            }
                            ActionChip(icon = Icons.Rounded.Share, label = "Share") {
                                // Share at the current position (YouTube-style ?t= link)
                                val posSec = playerPosition / 1000
                                val shareUrl = if (!details.isLive && posSec > 5)
                                    videoUrl + (if (videoUrl.contains("?")) "&t=${posSec}s" else "?t=${posSec}s")
                                else videoUrl
                                val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareUrl) }
                                context.startActivity(Intent.createChooser(i, "Share video"))
                            }
                            ActionChip(
                                icon = if (isInWatchLater) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                label = "Watch later",
                                active = isInWatchLater
                            ) { vm.toggleWatchLater() }
                            if (!details.isLive) {
                                // Save this exact moment for later (Library > Bookmarks)
                                ActionChip(icon = Icons.Rounded.BookmarkAdd, label = "Clip moment") {
                                    vm.addBookmark(playerPosition)
                                }
                                ActionChip(icon = Icons.Rounded.Download, label = "Download") { showDownloadDialog = true }
                            }
                            ActionChip(icon = Icons.Rounded.PlaylistAdd, label = "Save") { showPlaylistDialog = true }
                            if (AiEngine.isSupported() && !details.isLive) {
                                ActionChip(icon = Icons.Rounded.AutoAwesome, label = "Ask AI") { showAiSheet = true }
                            }
                        }
                        Spacer(Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        ) {
                            Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Speed", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box {
                                TextButton(onClick = { showSpeedMenu = true },
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)) {
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
                            // Quality selector
                            if (details.availableQualities.isNotEmpty()) {
                                Box {
                                    TextButton(onClick = { showQualityMenu = true },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp)) {
                                        Text(
                                            when {
                                                autoQuality && details.currentQuality > 0 -> "Auto (${details.currentQuality}p)"
                                                details.currentQuality > 0 -> "${details.currentQuality}p"
                                                else -> "Auto"
                                            },
                                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    DropdownMenu(expanded = showQualityMenu, onDismissRequest = { showQualityMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Auto", fontWeight = if (autoQuality) FontWeight.Bold else FontWeight.Normal) },
                                            trailingIcon = {
                                                if (autoQuality) Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                                            },
                                            onClick = {
                                                showQualityMenu = false
                                                if (!autoQuality) {
                                                    vm.savePosition(videoUrl, mediaController?.currentPosition ?: 0L)
                                                    vm.rememberQuality(null)
                                                    vm.changeQuality(videoUrl, null)
                                                }
                                            }
                                        )
                                        details.availableQualities.forEach { h ->
                                            DropdownMenuItem(
                                                text = { Text("${h}p", fontWeight = if (!autoQuality && h == details.currentQuality) FontWeight.Bold else FontWeight.Normal) },
                                                trailingIcon = {
                                                    if (!autoQuality && h == details.currentQuality)
                                                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                                                },
                                                onClick = {
                                                    showQualityMenu = false
                                                    if (autoQuality || h != details.currentQuality) {
                                                        vm.savePosition(videoUrl, mediaController?.currentPosition ?: 0L)
                                                        vm.rememberQuality(h)
                                                        vm.changeQuality(videoUrl, h)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            // Repeat button (36dp — default IconButtons are 48dp tall and
                            // made this whole row look like a big empty band)
                            IconButton(onClick = {
                                repeatMode = if (repeatMode == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                            }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    "Repeat",
                                    tint = if (repeatMode == Player.REPEAT_MODE_ONE) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // A–B loop: tap sets point A, tap again sets B, third tap clears
                            TextButton(
                                onClick = {
                                    when {
                                        loopA == null -> loopA = playerPosition
                                        loopB == null -> loopB = maxOf(playerPosition, loopA!! + 1000L)
                                        else -> { loopA = null; loopB = null }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    when {
                                        loopA == null -> "A·B"
                                        loopB == null -> "A·?"
                                        else -> "A·B ✓"
                                    },
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = if (loopA != null) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface.copy(0.6f)
                                )
                            }
                            // Audio-only toggle
                            IconButton(onClick = { vm.toggleAudioOnly() }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.MusicNote, "Audio only",
                                    tint = if (audioOnly) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurface.copy(0.6f),
                                    modifier = Modifier.size(20.dp))
                            }
                            // Queue button
                            Box {
                                IconButton(onClick = { showQueueSheet = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Rounded.QueueMusic, "Queue",
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
                                    IconButton(onClick = { showSubMenu = true }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Rounded.ClosedCaption, "Subtitles",
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
                            Spacer(Modifier.height(8.dp))
                            var expanded by remember { mutableStateOf(false) }
                            // YouTube-style rounded description card
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    // Timestamps seek the video; links open externally
                                    val accent = MaterialTheme.colorScheme.primary
                                    val descAnnotated = remember(details.description, accent) {
                                        annotateDescription(details.description, accent)
                                    }
                                    androidx.compose.foundation.text.ClickableText(
                                        text = descAnnotated,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.sp, lineHeight = 18.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                                        ),
                                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        onClick = { offset ->
                                            val ts = descAnnotated.getStringAnnotations("timestamp", offset, offset).firstOrNull()
                                            val link = descAnnotated.getStringAnnotations("url", offset, offset).firstOrNull()
                                            when {
                                                ts != null -> mediaController?.seekTo(ts.item.toLongOrNull() ?: 0L)
                                                link != null -> runCatching {
                                                    context.startActivity(Intent.createChooser(
                                                        Intent(Intent.ACTION_VIEW, link.item.toUri()), "Open link"))
                                                }
                                                else -> expanded = !expanded
                                            }
                                        }
                                    )
                                    Text(
                                        if (expanded) "Show less" else "...more", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 5.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = !expanded }
                                    )
                                }
                            }
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
                                    Icon(Icons.Rounded.List, null,
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text("Chapters  (${details.chapters.size})",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground)
                                }
                                Icon(if (chapExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
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
                                Icon(Icons.Rounded.Comment, null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text(
                                    if (comments.isEmpty()) "Comments" else "Comments  (${comments.size})",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (showComments && comments.isNotEmpty()) {
                                    // Honest, verifiable sort: NewPipe doesn't expose a
                                    // "newest" order through this API, so this only
                                    // offers what the data can actually back up.
                                    Surface(
                                        onClick = { commentsSortByLikes = !commentsSortByLikes },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f)
                                    ) {
                                        Text(
                                            if (commentsSortByLikes) "Most liked" else "Top",
                                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                                if (commentsLoading) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(if (showComments) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            if (showComments) {
                val sortedComments = if (commentsSortByLikes)
                    comments.sortedByDescending { it.likeCount } else comments
                items(sortedComments.take(30), key = { "c_${it.author}_${it.text.take(20)}" }) { comment ->
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
                                        Icon(Icons.Rounded.ThumbUp, null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp))
                                        Text(formatViews(comment.likeCount), fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                // Replies (expand/collapse, fetched on demand)
                                if (comment.replyCount > 0 && comment.repliesPage != null) {
                                    val key = vm.replyKey(comment)
                                    val expanded = repliesMap.containsKey(key)
                                    TextButton(
                                        onClick = { vm.toggleReplies(videoUrl, comment) },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        if (key in repliesLoading) {
                                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                        } else {
                                            Text(
                                                if (expanded) "Hide replies"
                                                else "View ${comment.replyCount} replies",
                                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    repliesMap[key]?.forEach { reply ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(top = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                Modifier.size(22.dp).clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (reply.avatarUrl.isNotEmpty()) {
                                                    coil.compose.AsyncImage(reply.avatarUrl, null,
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize())
                                                } else {
                                                    Text(reply.author.firstOrNull()?.uppercase() ?: "?",
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                                }
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text(reply.author, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onBackground)
                                                Text(reply.text, fontSize = 12.sp, lineHeight = 16.sp,
                                                    color = MaterialTheme.colorScheme.onBackground)
                                            }
                                        }
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
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        com.streamflow.ui.components.CompactVideoCard(
                            video = video, onClick = { onVideoClick(video.url) },
                            onChannelClick = onChannelClick?.let { cb -> { url: String -> cb(url) } })
                    }
                }
            }
        }
    }
    } // ambient-glow wrapper Box

    // ── Queue bottom sheet ────────────────────────────────────────────────────
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Download") },
            text = {
                Column {
                    Text("Pick a quality — saved to Downloads/StreamFlow, playable from Library > Downloads.",
                        fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "Best quality" to Int.MAX_VALUE,
                        "720p" to 720,
                        "480p (small size)" to 480
                    ).forEach { (label, cap) ->
                        TextButton(onClick = { vm.download(isAudio = false, maxHeight = cap); showDownloadDialog = false },
                            modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        }
                    }
                    TextButton(onClick = { vm.download(isAudio = true); showDownloadDialog = false },
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Audio only", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── On-device AI sheet: summary + ask about this video ───────────────────
    if (showAiSheet) {
        val aiModelReady = remember { AiEngine.isModelReady(context) }
        var aiQuestion by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { showAiSheet = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding() // keep the Ask field above the keyboard
                    .padding(start = 20.dp, end = 20.dp, bottom = 40.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("AI", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (aiModelReady) AiEngine.MODEL_LABEL else "On this device — no account, no cloud",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                if (!aiModelReady) {
                    Text(
                        "Download the free AI model once (${AiEngine.MODEL_SIZE_LABEL}) in Settings > AI. " +
                        "After that you can get video summaries and ask questions about any video — fully offline.",
                        fontSize = 14.sp, lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    FilledTonalButton(onClick = { showAiSheet = false }) { Text("Got it") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { vm.aiSummarize() }, enabled = !aiBusy) {
                            Icon(Icons.Rounded.Summarize, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Summarize video")
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (aiBusy && aiOutput.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Thinking… (first run loads the model, can take a minute)",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (aiOutput.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                aiOutput, fontSize = 14.sp, lineHeight = 20.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = aiQuestion,
                        onValueChange = { aiQuestion = it },
                        placeholder = { Text("Ask about this video…", fontSize = 13.sp) },
                        singleLine = true,
                        enabled = !aiBusy,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSend = {
                                if (!aiBusy && aiQuestion.isNotBlank()) { vm.aiAsk(aiQuestion); aiQuestion = "" }
                            }),
                        trailingIcon = {
                            IconButton(
                                onClick = { vm.aiAsk(aiQuestion); aiQuestion = "" },
                                enabled = !aiBusy && aiQuestion.isNotBlank()
                            ) { Icon(Icons.Rounded.Send, "Ask") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Answers come from the video's captions and run entirely on your phone. The small model can make mistakes.",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showPlaylistDialog) {
        val playlists by vm.playlists.collectAsState()
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("Save to playlist") },
            text = {
                Column {
                    playlists.forEach { pl ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { vm.addToPlaylist(pl.id); showPlaylistDialog = false }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.PlaylistPlay, null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(pl.name, modifier = Modifier.weight(1f), maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("${pl.count}", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (playlists.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outline.copy(0.3f))
                    }
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("New playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = { vm.createPlaylistAndAdd(newName); showPlaylistDialog = false }
                ) { Text("Create & save") }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistDialog = false }) { Text("Cancel") }
            }
        )
    }

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
                        Row {
                            IconButton(onClick = { PlaybackQueue.shuffle() }) {
                                Icon(Icons.Rounded.Shuffle, "Shuffle queue",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp))
                            }
                            TextButton(onClick = { PlaybackQueue.clear() }) { Text("Clear all") }
                        }
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
                            // Reorder
                            IconButton(onClick = { PlaybackQueue.move(idx, idx - 1) },
                                enabled = idx > 0, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "Move up",
                                    tint = if (idx > 0) MaterialTheme.colorScheme.onSurfaceVariant
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.25f),
                                    modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { PlaybackQueue.move(idx, idx + 1) },
                                enabled = idx < queue.size - 1, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "Move down",
                                    tint = if (idx < queue.size - 1) MaterialTheme.colorScheme.onSurfaceVariant
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.25f),
                                    modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { PlaybackQueue.remove(idx) },
                                modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Close, null,
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

// Makes timestamps (seek) and links (open) tappable in the video description
private fun annotateDescription(
    text: String,
    accent: Color
): androidx.compose.ui.text.AnnotatedString {
    val tsRegex = Regex("\\b(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})\\b")
    val urlRegex = Regex("https?://\\S+")
    data class Marker(val range: IntRange, val tag: String, val value: String)

    val markers = ArrayList<Marker>()
    urlRegex.findAll(text).forEach { m -> markers.add(Marker(m.range, "url", m.value)) }
    tsRegex.findAll(text).forEach { m ->
        val h = m.groupValues[1].toLongOrNull() ?: 0L
        val min = m.groupValues[2].toLongOrNull() ?: 0L
        val sec = m.groupValues[3].toLongOrNull() ?: 0L
        markers.add(Marker(m.range, "timestamp", ((h * 3600 + min * 60 + sec) * 1000L).toString()))
    }

    return androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        var lastEnd = -1
        markers.sortedBy { it.range.first }.forEach { mk ->
            if (mk.range.first <= lastEnd) return@forEach // skip overlaps (e.g. time inside a URL)
            lastEnd = mk.range.last
            addStyle(
                androidx.compose.ui.text.SpanStyle(color = accent, fontWeight = FontWeight.SemiBold),
                mk.range.first, mk.range.last + 1
            )
            addStringAnnotation(mk.tag, mk.value, mk.range.first, mk.range.last + 1)
        }
    }
}

// Chapter boundary tick marks drawn over a seek Slider (draw-only, never
// intercepts touches)
@Composable
private fun ChapterTicks(
    chapters: List<com.streamflow.data.model.VideoChapter>,
    durationMs: Long,
    modifier: Modifier
) {
    if (chapters.size < 2 || durationMs <= 0L) return
    androidx.compose.foundation.Canvas(modifier) {
        val tickW = 2.dp.toPx()
        val tickH = 5.dp.toPx()
        chapters.drop(1).forEach { ch ->
            val x = (ch.startMs.toFloat() / durationMs) * size.width
            drawRect(
                color = Color.Black.copy(0.6f),
                topLeft = Offset(x - tickW / 2, size.height / 2 - tickH / 2),
                size = androidx.compose.ui.geometry.Size(tickW, tickH)
            )
        }
    }
}

// Draws the storyboard frame for a playback position by cropping the right
// cell out of YouTube's sprite-sheet page (loaded and cached via Coil)
@Composable
private fun StoryboardPreview(
    sb: com.streamflow.data.model.Storyboard,
    positionMs: Long,
    modifier: Modifier
) {
    val perPage = (sb.framesPerPageX * sb.framesPerPageY).coerceAtLeast(1)
    val frameIdx = (positionMs / sb.durationPerFrameMs.coerceAtLeast(1))
        .toInt().coerceIn(0, (sb.totalCount - 1).coerceAtLeast(0))
    val pageIdx = (frameIdx / perPage).coerceIn(0, sb.urls.size - 1)
    val inPage = frameIdx % perPage
    val col = inPage % sb.framesPerPageX
    val rowIdx = inPage / sb.framesPerPageX

    val context = LocalContext.current
    var pageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(pageIdx) {
        // allowHardware(false): Canvas cropping needs a software bitmap
        val request = coil.request.ImageRequest.Builder(context)
            .data(sb.urls[pageIdx])
            .allowHardware(false)
            .build()
        val drawable = try { coil.Coil.imageLoader(context).execute(request).drawable } catch (_: Exception) { null }
        (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let {
            pageBitmap = it.asImageBitmap()
        }
    }

    val bmp = pageBitmap ?: return
    androidx.compose.foundation.Canvas(modifier.background(Color.Black)) {
        drawImage(
            image = bmp,
            srcOffset = androidx.compose.ui.unit.IntOffset(col * sb.frameWidth, rowIdx * sb.frameHeight),
            srcSize = androidx.compose.ui.unit.IntSize(sb.frameWidth, sb.frameHeight),
            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
        )
    }
}

// YouTube-style pill action chip (icon + label); `active` fills it with the accent
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            Modifier.height(34.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, null,
                tint = if (active) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
        }
    }
}

// Average color of the video thumbnail (tiny 24px decode) for the ambient glow
private suspend fun averageThumbColor(context: android.content.Context, url: String): Color? = try {
    val req = coil.request.ImageRequest.Builder(context)
        .data(url).size(24).allowHardware(false).build()
    val bmp = (coil.Coil.imageLoader(context).execute(req).drawable
        as? android.graphics.drawable.BitmapDrawable)?.bitmap
    if (bmp == null) null else {
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val c = bmp.getPixel(x, y)
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                x += 2
            }
            y += 2
        }
        if (n == 0) null
        else Color(red = (r / n) / 255f, green = (g / n) / 255f, blue = (b / n) / 255f)
    }
} catch (_: Exception) { null }
