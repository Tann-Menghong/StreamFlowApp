package com.streamflow.ui.shorts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.streamflow.data.OkHttpDownloader
import com.streamflow.data.model.VideoItem
import com.streamflow.ui.components.formatViews

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(
    onBack: () -> Unit,
    onOpenInPlayer: (String) -> Unit,
    onChannelClick: ((String) -> Unit)? = null,
    vm: ShortsViewModel = viewModel()
) {
    val videos by vm.videos.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val details by vm.details.collectAsState()
    val context = LocalContext.current

    // One shared player swapped between pages. Audio focus is requested so
    // Shorts ducks/pauses other apps' audio instead of playing over it.
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var isPaused by remember { mutableStateOf(false) }

    // Pause when the app goes to background — Shorts has no playback service,
    // so without this the audio would keep playing after leaving the app.
    // Coming back auto-resumes (only if WE paused it, not the user).
    var pausedByLifecycle by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when {
                event == Lifecycle.Event.ON_PAUSE && player.isPlaying -> {
                    player.pause()
                    isPaused = true
                    pausedByLifecycle = true
                }
                event == Lifecycle.Event.ON_RESUME && pausedByLifecycle -> {
                    player.play()
                    isPaused = false
                    pausedByLifecycle = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            loading -> CircularProgressIndicator(
                Modifier.align(Alignment.Center), color = Color.White
            )
            error != null -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Could not load Shorts", color = Color.White,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(error ?: "", color = Color.White.copy(0.7f), fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { vm.loadFeed() }) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retry")
                }
            }
            videos.isNotEmpty() -> {
                val pagerState = rememberPagerState { videos.size }
                val currentVideo = videos.getOrNull(pagerState.currentPage)

                // Resolve streams for the current page and prefetch the next one
                LaunchedEffect(pagerState.currentPage, videos.size) {
                    videos.getOrNull(pagerState.currentPage)?.let { vm.loadDetails(it.url) }
                    videos.getOrNull(pagerState.currentPage + 1)?.let { vm.loadDetails(it.url) }
                    if (pagerState.currentPage >= videos.size - 3) vm.loadMore()
                }

                // Feed the shared player whenever the visible page's stream is ready
                val currentDetails = currentVideo?.let { details[it.url] }
                LaunchedEffect(pagerState.currentPage, currentDetails != null) {
                    player.stop()
                    // Clear a pause left over from the previous page — otherwise the
                    // big Play icon showed over the next short's loading spinner
                    isPaused = false
                    val d = currentDetails ?: return@LaunchedEffect
                    val dsf = OkHttpDataSource.Factory(OkHttpDownloader.instance.client)
                    val videoSource = ProgressiveMediaSource.Factory(dsf)
                        .createMediaSource(MediaItem.fromUri(d.streamUrl))
                    val source = if (d.audioUrl != null) {
                        val audioSource = ProgressiveMediaSource.Factory(dsf)
                            .createMediaSource(MediaItem.fromUri(d.audioUrl))
                        MergingMediaSource(true, videoSource, audioSource)
                    } else videoSource
                    player.setMediaSource(source)
                    player.prepare()
                    player.play()
                    isPaused = false
                    currentVideo.let { vm.recordWatch(it) }
                }

                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondBoundsPageCount = 1
                ) { page ->
                    val video = videos[page]
                    val isActive = page == pagerState.currentPage
                    val ready = isActive && details.containsKey(video.url)

                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (player.isPlaying) { player.pause(); isPaused = true }
                                else { player.play(); isPaused = false }
                            }
                    ) {
                        if (ready) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                                    }
                                },
                                update = { it.player = player },
                                onRelease = { it.player = null },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AsyncImage(
                                model = video.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isActive) {
                                CircularProgressIndicator(
                                    Modifier.align(Alignment.Center).size(36.dp),
                                    color = Color.White, strokeWidth = 3.dp
                                )
                            }
                        }

                        if (isActive && isPaused) {
                            Icon(
                                Icons.Rounded.PlayArrow, "Paused",
                                tint = Color.White.copy(0.85f),
                                modifier = Modifier.align(Alignment.Center).size(72.dp)
                            )
                        }

                        ShortsOverlay(
                            video = video,
                            onOpenInPlayer = {
                                player.pause()
                                onOpenInPlayer(video.url)
                            },
                            onChannelClick = onChannelClick?.let { navigate ->
                                { url: String -> player.pause(); navigate(url) }
                            },
                            modifier = Modifier.align(Alignment.BottomStart)
                        )
                    }
                }
            }
            else -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No Shorts found", color = Color.White,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { vm.loadFeed() }) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retry")
                }
            }
        }

        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(0.55f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White)
            }
            Text("Shorts", color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = 17.sp)
        }
    }
}

@Composable
private fun ShortsOverlay(
    video: VideoItem,
    onOpenInPlayer: () -> Unit,
    onChannelClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Row(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(0.65f))
                )
            )
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 6.dp, bottom = 18.dp, top = 40.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable(enabled = video.uploaderUrl.isNotEmpty()) {
                    onChannelClick?.invoke(video.uploaderUrl)
                }
            ) {
                if (video.uploaderAvatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = video.uploaderAvatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(30.dp).clip(CircleShape)
                    )
                } else {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(Color.White.copy(0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Person, null, tint = Color.White,
                            modifier = Modifier.size(18.dp))
                    }
                }
                Text(video.uploaderName, color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Text(video.title, color = Color.White, fontSize = 13.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
            if (video.viewCount > 0) {
                Spacer(Modifier.height(3.dp))
                Text("${formatViews(video.viewCount)} views",
                    color = Color.White.copy(0.7f), fontSize = 11.sp)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onOpenInPlayer) {
                Icon(Icons.Rounded.OpenInFull, "Open in player",
                    tint = Color.White, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, video.url)
                }
                context.startActivity(
                    android.content.Intent.createChooser(send, "Share video"))
            }) {
                Icon(Icons.Rounded.Share, "Share",
                    tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}
