@file:OptIn(ExperimentalMaterial3Api::class)

package com.streamflow.app.ui.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.streamflow.app.R
import com.streamflow.app.data.model.ChapterItem
import com.streamflow.app.data.model.CommentItem
import com.streamflow.app.data.model.DislikeInfo
import com.streamflow.app.data.model.PlaybackSource
import com.streamflow.app.data.model.VideoDetails
import com.streamflow.app.data.model.VideoItem
import com.streamflow.app.di.ServiceLocator
import com.streamflow.app.ui.components.UiState
import com.streamflow.app.ui.components.VideoListItem
import com.streamflow.app.ui.components.formatCount
import com.streamflow.app.ui.components.formatViewCount
import kotlinx.coroutines.delay

@Composable
fun VideoDetailScreen(
    videoUrl: String,
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (String) -> Unit = {},
    isInPictureInPictureMode: Boolean = false
) {
    val context = LocalContext.current
    val viewModel: VideoDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                VideoDetailViewModel(
                    ServiceLocator.repository,
                    ServiceLocator.database,
                    ServiceLocator.playerController,
                    ServiceLocator.prefs,
                    videoUrl
                )
            }
        }
    )
    val state by viewModel.state.collectAsState()
    val isBookmarked by viewModel.isBookmarked.collectAsState()
    val isInWatchLater by viewModel.isInWatchLater.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val commentsState by viewModel.commentsState.collectAsState()
    val showComments by viewModel.showComments.collectAsState()
    val dislikeInfo by viewModel.dislikeInfo.collectAsState()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsState()

    var isFullscreen by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val activity = remember(context) { context as? ComponentActivity }

    LaunchedEffect(isFullscreen) {
        val act = activity ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (isFullscreen) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val act = activity ?: return@onDispose
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    if (isInPictureInPictureMode) {
        PlayerSurface(
            playbackSpeed = playerState.playbackSpeed,
            onSelectSpeed = viewModel::setPlaybackSpeed,
            onSeek = viewModel::seekBy,
            isFullscreen = false,
            onToggleFullscreen = {},
            showSpeedControl = false,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            currentRemaining = sleepTimerRemaining,
            onSelect = { minutes ->
                viewModel.setSleepTimer(minutes)
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        if (state is UiState.Success) {
                            IconButton(onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, videoUrl)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, null))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                            }
                        }
                        IconButton(onClick = { showSleepTimerDialog = true }) {
                            if (sleepTimerRemaining > 0) {
                                Text(
                                    text = "${sleepTimerRemaining}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(Icons.Default.Timer, contentDescription = stringResource(R.string.sleep_timer))
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(if (isFullscreen) PaddingValues(0.dp) else padding)
                .fillMaxSize()
        ) {
            when (val current = state) {
                is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is UiState.Error -> Text(current.message, modifier = Modifier.align(Alignment.Center))
                is UiState.Success -> VideoDetailBody(
                    details = current.data,
                    isBookmarked = isBookmarked,
                    isInWatchLater = isInWatchLater,
                    playbackSpeed = playerState.playbackSpeed,
                    commentsState = commentsState,
                    showComments = showComments,
                    dislikeInfo = dislikeInfo,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onToggleBookmark = { viewModel.toggleBookmark(current.data) },
                    onToggleWatchLater = { viewModel.toggleWatchLater(current.data) },
                    onAddToQueue = { viewModel.addToQueue(current.data) },
                    onSelectSource = { source -> viewModel.selectPlaybackSource(source, current.data.title) },
                    onSelectSpeed = viewModel::setPlaybackSpeed,
                    onSeek = viewModel::seekBy,
                    onSeekTo = viewModel::seekTo,
                    onLoadComments = viewModel::loadComments,
                    onVideoClick = onVideoClick,
                    onChannelClick = onChannelClick
                )
            }
        }
    }
}

@Composable
private fun SleepTimerDialog(
    currentRemaining: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer)) },
        text = {
            Column {
                listOf(5, 10, 15, 30, 60).forEach { minutes ->
                    TextButton(
                        onClick = { onSelect(minutes) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.sleep_timer_minutes, minutes))
                    }
                }
                TextButton(
                    onClick = { onSelect(0) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.sleep_timer_off))
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun VideoDetailBody(
    details: VideoDetails,
    isBookmarked: Boolean,
    isInWatchLater: Boolean,
    playbackSpeed: Float,
    commentsState: UiState<List<CommentItem>>,
    showComments: Boolean,
    dislikeInfo: DislikeInfo?,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleWatchLater: () -> Unit,
    onAddToQueue: () -> Unit,
    onSelectSource: (PlaybackSource) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onLoadComments: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (String) -> Unit
) {
    if (isFullscreen) {
        PlayerSurface(
            playbackSpeed = playbackSpeed,
            onSelectSpeed = onSelectSpeed,
            onSeek = onSeek,
            isFullscreen = true,
            onToggleFullscreen = onToggleFullscreen,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            PlayerSurface(
                playbackSpeed = playbackSpeed,
                onSelectSpeed = onSelectSpeed,
                onSeek = onSeek,
                isFullscreen = false,
                onToggleFullscreen = onToggleFullscreen,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
        }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(details.title, style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = details.uploaderUrl?.let { url ->
                            Modifier.weight(1f).clickable { onChannelClick(url) }
                        } ?: Modifier.weight(1f)
                    ) {
                        Text(details.uploaderName, style = MaterialTheme.typography.bodyMedium)
                        val viewMeta = buildList {
                            formatViewCount(details.viewCount).takeIf { it.isNotBlank() }?.let { add(it) }
                            dislikeInfo?.let { info ->
                                val dc = formatCount(info.dislikes)
                                if (dc.isNotBlank()) add("$dc ${stringResource(R.string.dislikes)}")
                            }
                            details.textualUploadDate?.let { add(it) }
                        }.joinToString(" • ")
                        if (viewMeta.isNotBlank()) {
                            Text(
                                text = viewMeta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = onAddToQueue) {
                            Icon(Icons.Default.AddToQueue, contentDescription = stringResource(R.string.add_to_queue))
                        }
                        IconButton(onClick = onToggleWatchLater) {
                            Icon(
                                imageVector = if (isInWatchLater) Icons.Filled.WatchLater else Icons.Outlined.WatchLater,
                                contentDescription = stringResource(
                                    if (isInWatchLater) R.string.remove_from_watch_later else R.string.add_to_watch_later
                                )
                            )
                        }
                        IconButton(onClick = onToggleBookmark) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = stringResource(R.string.bookmarks)
                            )
                        }
                    }
                }

                if (details.playbackOptions.size > 1) {
                    Text(
                        text = stringResource(R.string.quality),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        details.playbackOptions.forEach { source ->
                            val label = when (source) {
                                is PlaybackSource.Muxed -> source.label
                                is PlaybackSource.AudioOnly -> stringResource(R.string.audio_only)
                            }
                            FilterChip(
                                selected = false,
                                onClick = { onSelectSource(source) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                if (details.chapters.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.chapters),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }
            }
        }

        if (details.chapters.isNotEmpty()) {
            item {
                ChapterRow(chapters = details.chapters, onSeekTo = onSeekTo)
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (details.description.isNotBlank()) {
                    Text(
                        text = details.description,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            if (!showComments) {
                OutlinedButton(
                    onClick = onLoadComments,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Comment,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp).size(18.dp)
                    )
                    Text(stringResource(R.string.show_comments))
                }
            } else {
                Text(
                    text = stringResource(R.string.comments),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        if (showComments) {
            when (commentsState) {
                is UiState.Loading -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                is UiState.Error -> item {
                    Text(
                        text = commentsState.message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                is UiState.Success -> {
                    if (commentsState.data.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.no_comments),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        items(commentsState.data, key = { it.commentId.ifBlank { it.text } }) { comment ->
                            CommentListItem(comment = comment)
                        }
                    }
                }
            }
        }

        if (details.relatedVideos.isNotEmpty()) {
            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Text(
                    text = stringResource(R.string.related_videos),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(details.relatedVideos, key = { it.url }) { video ->
                VideoListItem(
                    video = video,
                    onClick = { onVideoClick(video) },
                    onUploaderClick = onChannelClick,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun ChapterRow(chapters: List<ChapterItem>, onSeekTo: (Long) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chapters) { chapter ->
            FilterChip(
                selected = false,
                onClick = { onSeekTo(chapter.startTimeSeconds * 1000L) },
                label = { Text(formatChapterLabel(chapter)) }
            )
        }
    }
}

private fun formatChapterLabel(chapter: ChapterItem): String {
    val m = chapter.startTimeSeconds / 60
    val s = chapter.startTimeSeconds % 60
    return "${chapter.title} ($m:${s.toString().padStart(2, '0')})"
}

@Composable
private fun CommentListItem(comment: CommentItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = comment.authorAvatarUrl,
                contentDescription = comment.authorName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.labelMedium
                )
                if (comment.isPinned) {
                    Text(
                        text = stringResource(R.string.pinned),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (comment.publishedDate != null) {
                    Text(
                        text = comment.publishedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            if (comment.text.isNotBlank()) {
                Text(
                    text = comment.text,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val likeText = when {
                comment.textualLikeCount.isNotBlank() -> "${comment.textualLikeCount} ${stringResource(R.string.likes)}"
                comment.likeCount > 0 -> "${comment.likeCount} ${stringResource(R.string.likes)}"
                else -> null
            }
            if (likeText != null) {
                Text(
                    text = likeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun formatSpeedLabel(speed: Float): String {
    val trimmed = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
    return "${trimmed}x"
}

private enum class SeekDirection { BACKWARD, FORWARD }

private const val SEEK_STEP_MS = 10_000L

@Composable
private fun PlayerSurface(
    playbackSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    onSeek: (Long) -> Unit = {},
    showSpeedControl: Boolean = true
) {
    val context = LocalContext.current
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<SeekDirection?>(null) }
    var volumeOverlay by remember { mutableStateOf<Int?>(null) }
    var brightnessOverlay by remember { mutableStateOf<Float?>(null) }
    var dragStartX by remember { mutableStateOf(0f) }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    DisposableEffect(Unit) {
        onDispose {
            playerView?.let { ServiceLocator.playerController.detachFrom(it) }
        }
    }

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(500)
            seekFeedback = null
        }
    }

    LaunchedEffect(volumeOverlay) {
        if (volumeOverlay != null) {
            delay(1000)
            volumeOverlay = null
        }
    }

    LaunchedEffect(brightnessOverlay) {
        if (brightnessOverlay != null) {
            delay(1000)
            brightnessOverlay = null
        }
    }

    Box(
        modifier = modifier
            .pointerInput("tap") {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2f) {
                            onSeek(-SEEK_STEP_MS)
                            seekFeedback = SeekDirection.BACKWARD
                        } else {
                            onSeek(SEEK_STEP_MS)
                            seekFeedback = SeekDirection.FORWARD
                        }
                    }
                )
            }
            .pointerInput("drag") {
                detectDragGestures(
                    onDragStart = { offset -> dragStartX = offset.x },
                    onDrag = { _, dragAmount ->
                        val normalizedDelta = -dragAmount.y / size.height.toFloat()
                        if (dragStartX < size.width / 2f) {
                            // Left side: brightness
                            val activity = context as? Activity ?: return@detectDragGestures
                            val attrs = activity.window.attributes
                            val current = if (attrs.screenBrightness < 0) 0.5f else attrs.screenBrightness
                            val newBrightness = (current + normalizedDelta * 0.5f).coerceIn(0.01f, 1f)
                            attrs.screenBrightness = newBrightness
                            activity.window.attributes = attrs
                            brightnessOverlay = newBrightness
                        } else {
                            // Right side: volume
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val newVol = (currentVol + (normalizedDelta * maxVol * 0.5f).toInt()).coerceIn(0, maxVol)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            volumeOverlay = newVol * 100 / maxVol
                        }
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).also {
                    playerView = it
                    ServiceLocator.playerController.attachTo(it)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = seekFeedback == SeekDirection.BACKWARD,
            modifier = Modifier.align(Alignment.CenterStart).padding(24.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Icon(Icons.Default.FastRewind, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        }

        AnimatedVisibility(
            visible = seekFeedback == SeekDirection.FORWARD,
            modifier = Modifier.align(Alignment.CenterEnd).padding(24.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        }

        brightnessOverlay?.let { brightness ->
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text("${(brightness * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }

        volumeOverlay?.let { vol ->
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text("$vol%", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }

        // Fullscreen toggle button
        IconButton(
            onClick = onToggleFullscreen,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
        ) {
            Icon(
                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = stringResource(if (isFullscreen) R.string.exit_fullscreen else R.string.fullscreen),
                tint = Color.White
            )
        }

        if (showSpeedControl) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                FilledTonalButton(onClick = { speedMenuExpanded = true }) {
                    Text(formatSpeedLabel(playbackSpeed))
                }
                DropdownMenu(expanded = speedMenuExpanded, onDismissRequest = { speedMenuExpanded = false }) {
                    PLAYBACK_SPEEDS.forEach { speed ->
                        DropdownMenuItem(
                            text = { Text(formatSpeedLabel(speed)) },
                            onClick = {
                                onSelectSpeed(speed)
                                speedMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
