package com.streamflow.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.streamflow.PlaybackService
import com.streamflow.ui.components.VideoCard
import com.streamflow.ui.components.formatViews
import kotlinx.coroutines.delay

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

    val isDirectStream = remember(videoUrl) {
        val lower = videoUrl.lowercase()
        lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".webm") ||
        lower.contains("/hls/") || lower.contains("/stream/")
    }
    var isFullscreen by remember { mutableStateOf(true) }

    // ── MediaController (connects to PlaybackService) ────────────────────────
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

    // ── Orientation + system bars cleanup on exit ────────────────────────────
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

    // ── Save playback position on exit ───────────────────────────────────────
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

    // ── Load video into service player ───────────────────────────────────────
    LaunchedEffect(videoUrl) { vm.loadVideo(videoUrl) }

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
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setExtras(extras).build()
            )
            .build()

        mc.setMediaItem(mediaItem)
        mc.prepare()
        mc.play()
        val savedPos = vm.getSavedPosition(videoUrl)
        if (savedPos > 0L) mc.seekTo(savedPos)
    }

    // ── Fullscreen orientation + display cutout ──────────────────────────────
    LaunchedEffect(isFullscreen) {
        activity?.let { act ->
            val win = act.window
            val ic  = WindowCompat.getInsetsController(win, win.decorView)
            if (isFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ic.hide(WindowInsetsCompat.Type.systemBars())
                ic.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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

    // ── Speed & seek feedback state ──────────────────────────────────────────
    var showSpeedMenu by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(1f) }
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    var seekFeedback by remember { mutableStateOf("") }
    var showSeekFeedback by remember { mutableStateOf(false) }
    if (showSeekFeedback) {
        LaunchedEffect(seekFeedback) {
            delay(700)
            showSeekFeedback = false
        }
    }

    // ── Seek feedback overlay (reused in both layouts) ───────────────────────
    @Composable
    fun SeekFeedback() {
        if (showSeekFeedback) {
            Text(
                seekFeedback,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }

    // Side-only double-tap zones that don't block the PlayerView controller.
    // They cover the full height EXCEPT the bottom 72 dp where controls live.
    @Composable
    fun DoubleTapZones() {
        val mc = mediaController ?: return
        Row(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(bottom = 72.dp)
        ) {
            Box(
                Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            mc.seekTo((mc.currentPosition - 10_000).coerceAtLeast(0))
                            seekFeedback = "- 10s"
                            showSeekFeedback = true
                        })
                    }
            )
            Box(Modifier.weight(0.4f).fillMaxHeight()) // transparent center – PlayerView handles it
            Box(
                Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            mc.seekTo(mc.currentPosition + 10_000)
                            seekFeedback = "+ 10s"
                            showSeekFeedback = true
                        })
                    }
            )
        }
    }

    // ── Donghua / direct stream ─ WebView player ─────────────────────────────
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
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(wv, true)
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
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
        return
    }

    // ── Fullscreen YouTube player ─────────────────────────────────────────────
    if (isFullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // PlayerView — handles ALL touch events (play/pause, seek bar, volume)
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = mediaController
                        useController = true
                        keepScreenOn = true
                    }
                },
                update = { pv -> pv.player = mediaController },
                modifier = Modifier.fillMaxSize()
            )

            // Double-tap zones on the sides only (centre is free for PlayerView controls)
            DoubleTapZones()

            // Seek feedback centred
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SeekFeedback()
            }

            // Exit fullscreen
            IconButton(
                onClick = { isFullscreen = false },
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
            ) {
                Icon(Icons.Default.FullscreenExit, null, tint = Color.White)
            }

            // PiP
            IconButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val params = PictureInPictureParams.Builder()
                            .setAspectRatio(Rational(16, 9)).build()
                        activity?.enterPictureInPictureMode(params)
                    }
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
            ) {
                Icon(Icons.Default.PictureInPicture, "PiP", tint = Color.White)
            }
        }
        return
    }

    // ── Portrait layout ───────────────────────────────────────────────────────
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                when (val s = state) {
                    is PlayerUiState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(40.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    is PlayerUiState.Error -> Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            s.message,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { vm.loadVideo(videoUrl) }) { Text("Retry") }
                    }
                    is PlayerUiState.Ready -> {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = mediaController
                                    useController = true
                                    keepScreenOn = true
                                }
                            },
                            update = { pv -> pv.player = mediaController },
                            modifier = Modifier.fillMaxSize()
                        )
                        DoubleTapZones()
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            SeekFeedback()
                        }
                    }
                }
                // Back
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                // Fullscreen
                IconButton(
                    onClick = { isFullscreen = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                ) {
                    Icon(Icons.Default.Fullscreen, null, tint = Color.White)
                }
            }
        }

        if (state is PlayerUiState.Ready) {
            val details = (state as PlayerUiState.Ready).details

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
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
                                Text(
                                    details.uploaderName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                if (details.viewCount > 0) Text(
                                    "${formatViews(details.viewCount)} views",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (details.likeCount > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.ThumbUp, null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                formatViews(details.likeCount),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                // Share
                                IconButton(onClick = {
                                    val i = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, videoUrl)
                                    }
                                    context.startActivity(Intent.createChooser(i, "Share video"))
                                }) {
                                    Icon(
                                        Icons.Default.Share, "Share",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                // Watch Later
                                IconButton(onClick = { vm.toggleWatchLater() }) {
                                    Icon(
                                        if (isInWatchLater) Icons.Default.Bookmark
                                        else Icons.Default.BookmarkBorder,
                                        "Watch Later",
                                        tint = if (isInWatchLater) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                // Favourite
                                IconButton(onClick = { vm.toggleFavorite() }) {
                                    Icon(
                                        if (isFavorite) Icons.Default.Favorite
                                        else Icons.Default.FavoriteBorder,
                                        "Favourite",
                                        tint = heartColor,
                                        modifier = Modifier.size(24.dp).scale(heartScale)
                                    )
                                }
                            }
                        }

                        // Speed
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Speed, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Speed", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box {
                                TextButton(onClick = { showSpeedMenu = true }) {
                                    Text("${currentSpeed}×", fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false }
                                ) {
                                    speeds.forEach { speed ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "${speed}×",
                                                    fontWeight = if (speed == currentSpeed)
                                                        FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                currentSpeed = speed
                                                mediaController?.setPlaybackSpeed(speed)
                                                showSpeedMenu = false
                                            }
                                        )
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
                                details.description,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                maxLines = if (expanded) Int.MAX_VALUE else 3,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { expanded = !expanded }
                            )
                            Text(
                                if (expanded) "Show less" else "Show more",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { expanded = !expanded }
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
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
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
