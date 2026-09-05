package com.streamflow.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflow.StreamFlowApp
import com.streamflow.data.ExtractionError
import com.streamflow.data.PlaybackQueue
import com.streamflow.data.SeriesEpisodes
import com.streamflow.data.classifyExtractionError
import com.streamflow.data.YouTubeRepository
import com.streamflow.data.friendlyError
import com.streamflow.data.model.VideoItem
import com.streamflow.ui.components.formatDuration
import kotlinx.coroutines.launch
import com.streamflow.ui.theme.appShape
import org.schabi.newpipe.extractor.Page

// ── Local playlist detail ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    val context = LocalContext.current
    val db = remember { (context.applicationContext as StreamFlowApp).database }
    val items by db.playlistDao().getItems(playlistId).collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("Playlist") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(playlistId) {
        name = db.playlistDao().getName(playlistId) ?: "Playlist"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (items.isNotEmpty()) {
                fun playAll(shuffled: Boolean) {
                    val order = if (shuffled) items.shuffled() else items
                    PlaybackQueue.setAll(order.drop(1).map {
                        VideoItem(
                            url = it.url, title = it.title, thumbnailUrl = it.thumbnailUrl,
                            uploaderName = it.uploaderName, viewCount = 0L, duration = it.duration
                        )
                    })
                    onVideoClick(order.first().url)
                }
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { playAll(false) }) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play all (${items.size})")
                    }
                    OutlinedButton(onClick = { playAll(true) }) {
                        Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                        Icon(Icons.Rounded.PlaylistPlay, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.25f),
                            modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("This playlist is empty",
                            color = MaterialTheme.colorScheme.onBackground.copy(0.55f))
                        Text("Save videos here from the player.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 4.dp, horizontal = 16.dp)) {
                    itemsIndexed(items, key = { _, it -> it.url }) { index, item ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onVideoClick(item.url) }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.width(110.dp).height(62.dp).clip(appShape(8.dp))) {
                                AsyncImage(item.thumbnailUrl, null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                if (item.duration > 0) {
                                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                        .background(Color.Black.copy(0.8f), appShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)) {
                                        Text(formatDuration(item.duration), color = Color.White, fontSize = 9.sp)
                                    }
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onBackground)
                                Text(item.uploaderName, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            // Reorder by swapping addedAt with the neighbour
                            Column {
                                IconButton(
                                    onClick = {
                                        val above = items.getOrNull(index - 1) ?: return@IconButton
                                        scope.launch {
                                            if (above.addedAt == item.addedAt) {
                                                // Batch-imported items share a timestamp — swapping
                                                // equal values is a no-op, so step past instead
                                                db.playlistDao().setAddedAt(playlistId, item.url, above.addedAt - 1)
                                            } else {
                                                db.playlistDao().setAddedAt(playlistId, item.url, above.addedAt)
                                                db.playlistDao().setAddedAt(playlistId, above.url, item.addedAt)
                                            }
                                        }
                                    },
                                    enabled = index > 0, modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Rounded.KeyboardArrowUp, "Move up",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(if (index > 0) 0.7f else 0.25f),
                                        modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val below = items.getOrNull(index + 1) ?: return@IconButton
                                        scope.launch {
                                            if (below.addedAt == item.addedAt) {
                                                db.playlistDao().setAddedAt(playlistId, item.url, below.addedAt + 1)
                                            } else {
                                                db.playlistDao().setAddedAt(playlistId, item.url, below.addedAt)
                                                db.playlistDao().setAddedAt(playlistId, below.url, item.addedAt)
                                            }
                                        }
                                    },
                                    enabled = index < items.size - 1, modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Rounded.KeyboardArrowDown, "Move down",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(if (index < items.size - 1) 0.7f else 0.25f),
                                        modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(onClick = {
                                scope.launch { db.playlistDao().removeItem(playlistId, item.url) }
                            }) {
                                Icon(Icons.Rounded.Close, "Remove",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Remote (YouTube) playlist ────────────────────────────────────────────────

/**
 * Play [url] and queue whatever follows it in [episodes].
 *
 * Every entry point into a playlist goes through here so none of them can
 * forget the queue. Tapping a row used to call onVideoClick alone, which played
 * that one video against whatever queue happened to be left over from before —
 * so a series stopped at the end of the episode you picked, and "Play all" was
 * the only way to get continuous playback at all.
 */
private fun playFrom(
    episodes: List<VideoItem>,
    url: String,
    onVideoClick: (String) -> Unit,
) {
    PlaybackQueue.setAll(SeriesEpisodes.upNextFrom(episodes, url))
    onVideoClick(url)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemotePlaylistScreen(
    playlistUrl: String,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    val repo = remember { YouTubeRepository() }
    var playlist by remember { mutableStateOf<YouTubeRepository.RemotePlaylist?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Kept alongside the message so the shared ErrorState can offer an action
    // that can actually work, instead of Retry for every failure.
    var errorKind by remember { mutableStateOf(ExtractionError.UNKNOWN) }

    // A playlist is a series and its items are episodes, so how far through
    // each one the user is decides what the Continue button does.
    val playlistCtx = LocalContext.current
    val playlistDb = remember { (playlistCtx.applicationContext as StreamFlowApp).database }
    val watched by playlistDb.historyDao().getAll().collectAsState(initial = emptyList())
    val progress = remember(watched) {
        watched.associate { it.url to SeriesEpisodes.fractionOf(it.duration, it.position) }
    }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var nextPage by remember { mutableStateOf<Page?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(playlistUrl, retryKey) {
        error = null
        try {
            val p = repo.getRemotePlaylist(playlistUrl)
            playlist = p
            videos = p.videos.distinctBy { it.url }
            nextPage = p.nextPage
        } catch (e: Exception) {
            error = friendlyError(e)
            errorKind = classifyExtractionError(e)
        }
    }

    // Infinite scroll for long playlists. The flow emits (lastVisible, total)
    // instead of a Boolean: a small appended page could leave "near end" stuck
    // at true, and snapshotFlow's dedupe (true -> true) never re-fired, so
    // pagination stalled until the user scrolled again.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (last, total) ->
            val nearEnd = total > 0 && last >= total - 4
            val page = nextPage
            if (nearEnd && page != null && !loadingMore) {
                loadingMore = true
                val r = try { repo.getRemotePlaylistNextPage(playlistUrl, page) }
                        catch (_: Exception) { null }
                // Keep the page on failure so the next scroll retries — nulling
                // it on a hiccup permanently ended the playlist at that point
                if (r != null) {
                    nextPage = r.nextPage
                    val existing = videos.mapTo(HashSet()) { it.url }
                    videos = videos + r.videos.filter { it.url !in existing }
                }
                loadingMore = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(playlist?.name ?: "Playlist", fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                // The shared states, so a playlist that fails to load looks and
                // behaves like every other failure in the app rather than being
                // this screen's own private answer to the same question.
                error != null -> com.streamflow.ui.components.ErrorState(
                    error = errorKind,
                    onRetry = { retryKey++ }
                )
                playlist == null -> com.streamflow.ui.components.ShimmerList()
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Text(playlist!!.uploaderName, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (playlist!!.videoCount > 0) {
                                Text("${playlist!!.videoCount} videos", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                            }
                            Spacer(Modifier.height(8.dp))
                            if (videos.isNotEmpty()) {
                                // Continue takes precedence over Play all when
                                // the series has been started: on a 280-episode
                                // donghua, "Play all" from episode 1 is almost
                                // never what the user came back for.
                                val resume = remember(videos, progress) {
                                    SeriesEpisodes.resumePoint(videos, progress)
                                }
                                if (resume != null) {
                                    val ep = resume.episode
                                    val number = SeriesEpisodes.episodeNumber(ep.title, resume.index)
                                    val left = watched.firstOrNull { it.url == ep.url }
                                        ?.let { SeriesEpisodes.remainingLabel(it.duration, it.position) }
                                    Button(
                                        onClick = { playFrom(videos, ep.url, onVideoClick) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            buildString {
                                                append(if (resume.isNextUp) "Play episode " else "Continue episode ")
                                                append(number)
                                                if (!resume.isNextUp && left != null) append(" · $left")
                                            },
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        PlaybackQueue.setAll(videos.drop(1))
                                        onVideoClick(videos.first().url)
                                    }) {
                                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Play all")
                                    }
                                    OutlinedButton(onClick = {
                                        val order = videos.shuffled()
                                        PlaybackQueue.setAll(order.drop(1))
                                        onVideoClick(order.first().url)
                                    }) {
                                        Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Shuffle")
                                    }
                                }
                            }
                        }
                    }
                    itemsIndexed(videos, key = { _, v -> v.url }) { index, video ->
                        val watchedFraction = progress[video.url] ?: 0f
                        Row(
                            Modifier.fillMaxWidth()
                                // The fix that makes this a series rather than a
                                // list: tapping an episode used to play only
                                // that episode and leave the queue alone, so
                                // playback stopped dead at the end of it and
                                // there was no next episode to go to.
                                .clickable { playFrom(videos, video.url, onVideoClick) }
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // The uploader's own numbering where the title
                            // states it: a playlist can open with a trailer, or
                            // start partway into an ongoing series, and then the
                            // position is not the episode number.
                            Text("${SeriesEpisodes.episodeNumber(video.title, index)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(24.dp))
                            Box(Modifier.width(110.dp).height(62.dp).clip(appShape(8.dp))) {
                                AsyncImage(video.thumbnailUrl, null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                if (video.duration > 0) {
                                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                        .background(Color.Black.copy(0.8f), appShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)) {
                                        Text(formatDuration(video.duration), color = Color.White, fontSize = 9.sp)
                                    }
                                }
                                // How far in you already are, on the thumbnail
                                // itself — the same cue the rest of the app uses.
                                if (watchedFraction > SeriesEpisodes.STARTED) {
                                    Box(
                                        Modifier.align(Alignment.BottomStart)
                                            .fillMaxWidth().height(3.dp)
                                            .background(Color.Black.copy(0.45f))
                                    ) {
                                        Box(
                                            Modifier.fillMaxWidth(watchedFraction).fillMaxHeight()
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(video.title, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onBackground)
                                Text(video.uploaderName, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                    if (loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
