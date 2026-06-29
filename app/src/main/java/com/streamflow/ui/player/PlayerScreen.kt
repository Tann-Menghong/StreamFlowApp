package com.streamflow.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val context = LocalContext.current
    val activity = context as? Activity
    var isFullscreen by remember { mutableStateOf(false) }

    // Hoisted here so they run in a @Composable scope (not inside LazyListScope)
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
        player.prepare(); player.play()
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

    // ── Fullscreen layout ────────────────────────────────────────────
    if (isFullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = { isFullscreen = false },
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
            ) {
                Icon(Icons.Default.FullscreenExit, contentDescription = null, tint = Color.White)
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
        // Player
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
                        factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
                        modifier = Modifier.fillMaxSize()
                    )
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
                            Column {
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

            // Related
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
