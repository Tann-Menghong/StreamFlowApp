@file:OptIn(ExperimentalMaterial3Api::class)

package com.streamflow.app.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.ui.PlayerView
import com.streamflow.app.R
import com.streamflow.app.data.model.PlaybackSource
import com.streamflow.app.data.model.VideoDetails
import com.streamflow.app.data.model.VideoItem
import com.streamflow.app.di.ServiceLocator
import com.streamflow.app.ui.components.UiState
import com.streamflow.app.ui.components.VideoListItem
import com.streamflow.app.ui.components.formatViewCount
import kotlinx.coroutines.delay

@Composable
fun VideoDetailScreen(
    videoUrl: String,
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    isInPictureInPictureMode: Boolean = false
) {
    val viewModel: VideoDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                VideoDetailViewModel(
                    ServiceLocator.repository,
                    ServiceLocator.database,
                    ServiceLocator.playerController,
                    videoUrl
                )
            }
        }
    )
    val state by viewModel.state.collectAsState()
    val isBookmarked by viewModel.isBookmarked.collectAsState()
    val playerState by viewModel.playerState.collectAsState()

    if (isInPictureInPictureMode) {
        PlayerSurface(
            playbackSpeed = playerState.playbackSpeed,
            onSelectSpeed = viewModel::setPlaybackSpeed,
            onSeek = viewModel::seekBy,
            showSpeedControl = false,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is UiState.Error -> Text(current.message, modifier = Modifier.align(Alignment.Center))
                is UiState.Success -> VideoDetailBody(
                    details = current.data,
                    isBookmarked = isBookmarked,
                    playbackSpeed = playerState.playbackSpeed,
                    onToggleBookmark = { viewModel.toggleBookmark(current.data) },
                    onSelectSource = { source -> viewModel.selectPlaybackSource(source, current.data.title) },
                    onSelectSpeed = viewModel::setPlaybackSpeed,
                    onSeek = viewModel::seekBy,
                    onVideoClick = onVideoClick
                )
            }
        }
    }
}

@Composable
private fun VideoDetailBody(
    details: VideoDetails,
    isBookmarked: Boolean,
    playbackSpeed: Float,
    onToggleBookmark: () -> Unit,
    onSelectSource: (PlaybackSource) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSeek: (Long) -> Unit,
    onVideoClick: (VideoItem) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            PlayerSurface(
                playbackSpeed = playbackSpeed,
                onSelectSpeed = onSelectSpeed,
                onSeek = onSeek,
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
                    Column {
                        Text(details.uploaderName, style = MaterialTheme.typography.bodyMedium)
                        val meta = listOfNotNull(
                            formatViewCount(details.viewCount).takeIf { it.isNotBlank() },
                            details.textualUploadDate
                        ).joinToString(" • ")
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = stringResource(R.string.bookmarks)
                        )
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

                if (details.description.isNotBlank()) {
                    Text(
                        text = details.description,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }

        if (details.relatedVideos.isNotEmpty()) {
            item {
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
                    modifier = Modifier.padding(horizontal = 16.dp)
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
    modifier: Modifier = Modifier,
    onSeek: (Long) -> Unit = {},
    showSpeedControl: Boolean = true
) {
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<SeekDirection?>(null) }

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

    Box(
        modifier = modifier.pointerInput(Unit) {
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
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).also {
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
            Icon(
                imageVector = Icons.Default.FastRewind,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }

        AnimatedVisibility(
            visible = seekFeedback == SeekDirection.FORWARD,
            modifier = Modifier.align(Alignment.CenterEnd).padding(24.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Icon(
                imageVector = Icons.Default.FastForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
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
