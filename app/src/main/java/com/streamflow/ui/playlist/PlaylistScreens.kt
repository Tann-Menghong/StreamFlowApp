package com.streamflow.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Shuffle
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
import com.streamflow.data.PlaybackQueue
import com.streamflow.data.YouTubeRepository
import com.streamflow.data.friendlyError
import com.streamflow.data.model.VideoItem
import com.streamflow.ui.components.formatDuration
import kotlinx.coroutines.launch
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
                    PlaybackQueue.clear()
                    order.drop(1).forEach {
                        PlaybackQueue.add(VideoItem(
                            url = it.url, title = it.title, thumbnailUrl = it.thumbnailUrl,
                            uploaderName = it.uploaderName, viewCount = 0L, duration = it.duration
                        ))
                    }
                    onVideoClick(order.first().url)
                }
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { playAll(false) }) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play all (${items.size})")
                    }
                    OutlinedButton(onClick = { playAll(true) }) {
                        Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                        Icon(Icons.Default.PlaylistPlay, null,
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
                            Box(Modifier.width(110.dp).height(62.dp).clip(RoundedCornerShape(8.dp))) {
                                AsyncImage(item.thumbnailUrl, null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                if (item.duration > 0) {
                                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                        .background(Color.Black.copy(0.8f), RoundedCornerShape(4.dp))
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
                                            db.playlistDao().setAddedAt(playlistId, item.url, above.addedAt)
                                            db.playlistDao().setAddedAt(playlistId, above.url, item.addedAt)
                                        }
                                    },
                                    enabled = index > 0, modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, "Move up",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(if (index > 0) 0.7f else 0.25f),
                                        modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val below = items.getOrNull(index + 1) ?: return@IconButton
                                        scope.launch {
                                            db.playlistDao().setAddedAt(playlistId, item.url, below.addedAt)
                                            db.playlistDao().setAddedAt(playlistId, below.url, item.addedAt)
                                        }
                                    },
                                    enabled = index < items.size - 1, modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, "Move down",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(if (index < items.size - 1) 0.7f else 0.25f),
                                        modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(onClick = {
                                scope.launch { db.playlistDao().removeItem(playlistId, item.url) }
                            }) {
                                Icon(Icons.Default.Close, "Remove",
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
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var nextPage by remember { mutableStateOf<Page?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(playlistUrl) {
        try {
            val p = repo.getRemotePlaylist(playlistUrl)
            playlist = p
            videos = p.videos.distinctBy { it.url }
            nextPage = p.nextPage
        } catch (e: Exception) {
            error = friendlyError(e)
        }
    }

    // Infinite scroll for long playlists
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }.collect { nearEnd ->
            val page = nextPage
            if (nearEnd && page != null && !loadingMore) {
                loadingMore = true
                val r = try { repo.getRemotePlaylistNextPage(playlistUrl, page) }
                        catch (_: Exception) { null }
                nextPage = r?.nextPage
                val existing = videos.mapTo(HashSet()) { it.url }
                videos = videos + (r?.videos?.filter { it.url !in existing } ?: emptyList())
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
                error != null -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Could not load playlist", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(6.dp))
                    Text(error ?: "", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                playlist == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
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
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        PlaybackQueue.clear()
                                        videos.drop(1).forEach { PlaybackQueue.add(it) }
                                        onVideoClick(videos.first().url)
                                    }) {
                                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Play all")
                                    }
                                    OutlinedButton(onClick = {
                                        val order = videos.shuffled()
                                        PlaybackQueue.clear()
                                        order.drop(1).forEach { PlaybackQueue.add(it) }
                                        onVideoClick(order.first().url)
                                    }) {
                                        Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Shuffle")
                                    }
                                }
                            }
                        }
                    }
                    itemsIndexed(videos, key = { _, v -> v.url }) { index, video ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onVideoClick(video.url) }
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("${index + 1}", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(20.dp))
                            Box(Modifier.width(110.dp).height(62.dp).clip(RoundedCornerShape(8.dp))) {
                                AsyncImage(video.thumbnailUrl, null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                if (video.duration > 0) {
                                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                        .background(Color.Black.copy(0.8f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)) {
                                        Text(formatDuration(video.duration), color = Color.White, fontSize = 9.sp)
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
