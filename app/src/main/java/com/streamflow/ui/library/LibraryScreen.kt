package com.streamflow.ui.library

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamflow.data.local.entity.FavoriteEntity
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.local.entity.WatchLaterEntity
import com.streamflow.data.model.VideoItem
import com.streamflow.ui.components.VideoCard
import com.streamflow.ui.components.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onVideoClick: (String) -> Unit, vm: LibraryViewModel = viewModel()) {
    val favorites  by vm.favorites.collectAsState()
    val history    by vm.history.collectAsState()
    val watchLater by vm.watchLater.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Favorites", "History", "Watch Later")

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
                    AnimatedVisibility(selectedTab == 2 && watchLater.isNotEmpty()) {
                        IconButton(onClick = { vm.clearWatchLater() }) {
                            Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val tabCounts = listOf(favorites.size, history.size, watchLater.size)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = MaterialTheme.colorScheme.background,
                contentColor     = MaterialTheme.colorScheme.primary,
                divider          = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.4f)) }
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i }
                    ) {
                        Row(
                            Modifier.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                title,
                                fontWeight = if (selectedTab == i) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize   = 13.sp,
                                color      = if (selectedTab == i) MaterialTheme.colorScheme.primary
                                             else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (tabCounts[i] > 0) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selectedTab == i) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        tabCounts[i].toString(),
                                        fontSize   = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = if (selectedTab == i) MaterialTheme.colorScheme.onPrimary
                                                     else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier   = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "library_tab"
            ) { tab ->
                when (tab) {
                    0 -> VideoListWithSearch(
                            items = favorites.map { it.toVideoItem() },
                            onVideoClick = onVideoClick,
                            onRemove = vm::removeFavorite,
                            emptyTitle = "No favorites yet",
                            emptySubtitle = "Tap heart on any video to save it here.",
                            emptyIcon = Icons.Default.FavoriteBorder
                        )
                    1 -> {
                        val progressMap = history.associate { h ->
                            h.url to if (h.duration > 0L)
                                (h.position / 1000f / h.duration).coerceIn(0f, 1f) else 0f
                        }
                        val remainingMap = history.associate { h ->
                            h.url to ((h.duration - h.position / 1000L).coerceAtLeast(0L))
                        }
                        VideoListWithSearch(
                            items = history.map { it.toVideoItem() },
                            onVideoClick = onVideoClick,
                            onRemove = vm::removeHistory,
                            emptyTitle = "No history yet",
                            emptySubtitle = "Videos you watch will appear here.",
                            emptyIcon = Icons.Default.History,
                            progressFractions = progressMap,
                            remainingSeconds = remainingMap
                        )
                    }
                    else -> VideoListWithSearch(
                            items = watchLater.map { it.toVideoItem() },
                            onVideoClick = onVideoClick,
                            onRemove = vm::removeWatchLater,
                            emptyTitle = "No videos saved",
                            emptySubtitle = "Tap bookmark while watching to add videos here.",
                            emptyIcon = Icons.Default.BookmarkBorder
                        )
                }
            }
        }
    }
}

@Composable
private fun VideoListWithSearch(
    items: List<VideoItem>,
    onVideoClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    emptyTitle: String,
    emptySubtitle: String,
    emptyIcon: ImageVector,
    progressFractions: Map<String, Float> = emptyMap(),
    remainingSeconds: Map<String, Long> = emptyMap()
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Date") }
    var showSort by remember { mutableStateOf(false) }

    val filteredItems = items
        .filter { it.title.contains(searchQuery, ignoreCase = true) }
        .let { list ->
            when (sortBy) {
                "Title" -> list.sortedBy { it.title }
                "Duration" -> list.sortedByDescending { it.duration }
                else -> list
            }
        }

    Column(Modifier.fillMaxSize()) {
        // Search + sort row
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Search, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onBackground),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) Text("Search...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(18.dp)) {
                            Icon(Icons.Default.Close, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { showSort = true }) {
                    Icon(Icons.Default.Sort, "Sort",
                        tint = if (sortBy != "Date") MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                    listOf("Date", "Title", "Duration").forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt, fontWeight = if (sortBy == opt) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { sortBy = opt; showSort = false }
                        )
                    }
                }
            }
        }

        if (filteredItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                    Icon(emptyIcon, null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.25f),
                        modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(if (searchQuery.isNotEmpty()) "No results for \"$searchQuery\"" else emptyTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(0.55f))
                    Spacer(Modifier.height(6.dp))
                    Text(emptySubtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f),
                        textAlign = TextAlign.Center)
                }
            }
            return
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(filteredItems, key = { it.url }) { video ->
                val remaining = remainingSeconds[video.url]
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f)) {
                        VideoCard(
                            video            = video,
                            onClick          = { onVideoClick(video.url) },
                            progressFraction = progressFractions[video.url] ?: 0f,
                            remainingLabel   = remaining?.let {
                                if (it > 0) "${formatDuration(it)} left" else "Watched"
                            }
                        )
                    }
                    IconButton(onClick = { onRemove(video.url) }, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private fun FavoriteEntity.toVideoItem() = VideoItem(url, title, thumbnailUrl, uploaderName, viewCount, duration)
private fun HistoryEntity.toVideoItem()  = VideoItem(url, title, thumbnailUrl, uploaderName, viewCount, duration)
private fun WatchLaterEntity.toVideoItem() = VideoItem(url, title, thumbnailUrl, uploaderName, viewCount, duration)
