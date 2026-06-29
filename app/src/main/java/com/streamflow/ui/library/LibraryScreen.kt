package com.streamflow.ui.library

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamflow.data.local.entity.FavoriteEntity
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.model.VideoItem
import com.streamflow.ui.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onVideoClick: (String) -> Unit, vm: LibraryViewModel = viewModel()) {
    val favorites by vm.favorites.collectAsState()
    val history   by vm.history.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Favorites", "History")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    AnimatedVisibility(selectedTab == 0 && favorites.isNotEmpty()) {
                        IconButton(onClick = { vm.clearFavorites() }) {
                            Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    AnimatedVisibility(selectedTab == 1 && history.isNotEmpty()) {
                        IconButton(onClick = { vm.clearHistory() }) {
                            Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = MaterialTheme.colorScheme.background,
                contentColor     = MaterialTheme.colorScheme.primary,
                divider          = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.4f)) }
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text     = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == i) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize   = 13.sp
                            )
                        }
                    )
                }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "library_tab"
            ) { tab ->
                when (tab) {
                    0 -> VideoList(favorites.map { it.toVideoItem() }, onVideoClick, vm::removeFavorite, "No favorites yet.\nTap ♥ on any video to save it.")
                    else -> VideoList(history.map { it.toVideoItem() }, onVideoClick, vm::removeHistory, "No watch history yet.")
                }
            }
        }
    }
}

@Composable
private fun VideoList(items: List<VideoItem>, onVideoClick: (String) -> Unit, onRemove: (String) -> Unit, emptyMessage: String) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        items(items, key = { it.url }) { video ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) {
                    VideoCard(video = video, onClick = { onVideoClick(video.url) })
                }
                IconButton(onClick = { onRemove(video.url) }, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun FavoriteEntity.toVideoItem() = VideoItem(url, title, thumbnailUrl, uploaderName, viewCount, duration)
private fun HistoryEntity.toVideoItem()  = VideoItem(url, title, thumbnailUrl, uploaderName, viewCount, duration)
