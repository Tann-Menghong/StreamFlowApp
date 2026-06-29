package com.streamflow.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import android.view.WindowManager
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.streamflow.ui.components.VideoCard
import com.streamflow.ui.components.formatViews
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

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
    var isFullscreen by remember { mutableStateOf(false) }

    // Speed control
    var showSpeedMenu by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(1f) }
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    // Seek feedback
    var seekFeedback by remember { mutableStateOf("") }
    var showSeekFeedback by remember { mutableStateOf(false) }

    // PlayerView reference for controller toggle
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    val heartScale by animateFloatAsState(
        targetValue   = if (isFavorite) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "heart"
    )
    val heartColor by animateColorAsState(
        targetValue   = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        animationSpec = tween(200),
        label         = "heart_color"
    )

    LaunchedEffect(videoUrl) { vm.loadVideo(videoUrl) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    // Save position when screen is disposed
    DisposableEffect(videoUrl) {
        onDispose {
            vm.savePosition(videoUrl, player.currentPosition)
        }
    }

    LaunchedEffect(state) {
        val ready = state as? PlayerUiState.Ready ?: return@LaunchedEffect
        val d = ready.details
        val dsf = OkHttpDataSource.Factory(OkHttpClient())
        if (d.audioUrl != null) {
            val vs = ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(d.streamUrl))
            val `as` = ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(d.audioUrl))
            player.setMediaSource(MergingMediaSource(vs, `as`))
        } else {
            player.setMediaItem(MediaItem.fromUri(d.streamUrl))
        }
        player.prepare()
        player.play()
        // Seek to saved position
        val savedPos = vm.getSavedPosition(videoUrl)
        if (savedPos > 0L) player.seekTo(savedPos)
    }

    LaunchedEffect(isFullscreen) {
        activity?.let { act ->
            val win = act.window
            val ic  = WindowCompat.getInsetsController(win, win.decorView)
            if (isFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ic.hide(WindowInsetsCompat.Type.systemBars())
                ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                ic.show(WindowInsetsCompat.Type.systemBars())
                win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Seek feedback auto-dismiss
    if (showSeekFeedback) {
        LaunchedEffect(seekFeedback) {
            delay(600)
            showSeekFeedback = false
        }
    }

    // Gesture modifier for double-tap seek + single-tap controller toggle
    val gestureModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                playerViewRef?.let { pv ->
                    if (pv.isControllerFullyVisible) pv.hideController() else pv.showController()
                }
            },
            onDoubleTap = { offset ->
                val width = size.width
                if (offset.x < width / 2) {
                    player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                    seekFeedback = "- 10s"
                } else {
                    player.seekTo(player.currentPosition + 10_000)
                    seekFeedback = "+ 10s"
                }
                showSeekFeedback = true
            }
        )
    }

    // ── Fullscreen layout ────────────────────────────────────────────
    if (isFullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        playerViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            // Gesture + seek feedback overlay
            Box(
                modifier = Modifier.fillMaxSize().then(gestureModifier),
                contentAlignment = Alignment.Center
            ) {
                if (showSeekFeedback) {
                    Text(
                        seekFeedback,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            // Exit fullscreen
            IconButton(
                onClick = { isFullscreen = false },
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
            ) {
                Icon(Icons.Default.FullscreenExit, contentDescription = null, tint = Color.White)
            }
            // PiP button
            IconButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val params = PictureInPictureParams.Builder()
                            .setAspectRatio(Rational(16, 9))
                            .build()
                        activity?.enterPictureInPictureMode(params)
                    }
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
            ) {
                Icon(Icons.Default.PictureInPicture, contentDescription = "Picture in Picture", tint = Color.White)
            }
        }
        return
    }

    // ── Normal layout ────────────────────────────────────────────────
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Player box
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
                    is PlayerUiState.Error   -> Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(s.message, color = Color.White, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { vm.loadVideo(videoUrl) }) { Text("Retry") }
                    }
                    is PlayerUiState.Ready   -> AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = true
                                playerViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Gesture + seek feedback overlay
                Box(
                    modifier = Modifier.fillMaxSize().then(gestureModifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (showSeekFeedback) {
                        Text(
                            seekFeedback,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                // Back button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                // Fullscreen button
                IconButton(
                    onClick = { isFullscreen = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                ) {
                    Icon(Icons.Default.Fullscreen, null, tint = Color.White)
                }
            }
        }

        // Info card
        if (state is PlayerUiState.Ready) {
            val details = (state as PlayerUiState.Ready).details

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = details.title,
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
                                            Icon(Icons.Default.ThumbUp, null,
                                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(formatViews(details.likeCount),
                                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                // Share button
                                IconButton(onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, videoUrl)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                // Watch Later button
                                IconButton(onClick = { vm.toggleWatchLater() }) {
                                    Icon(
                                        imageVector = if (isInWatchLater) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "Watch Later",
                                        tint = if (isInWatchLater) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                // Favorite button
                                IconButton(onClick = { vm.toggleFavorite() }) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = heartColor,
                                        modifier = Modifier.size(24.dp).scale(heartScale)
                                    )
                                }
                            }
                        }

                        // Playback speed row
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Speed", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box {
                                TextButton(onClick = { showSpeedMenu = true }) {
                                    Text("${currentSpeed}×", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                                    speeds.forEach { speed ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "${speed}×",
                                                    fontWeight = if (speed == currentSpeed) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                currentSpeed = speed
                                                player.setPlaybackSpeed(speed)
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

            // Related videos
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
