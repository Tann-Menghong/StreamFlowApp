package com.streamflow.ui.library

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.streamflow.data.local.dao.PlaylistWithCount
import com.streamflow.data.local.entity.DownloadEntity
import com.streamflow.data.local.entity.FavoriteEntity
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.local.entity.SubscriptionEntity
import com.streamflow.data.local.entity.WatchLaterEntity
import com.streamflow.data.model.VideoItem
import com.streamflow.ui.components.VideoCard
import com.streamflow.ui.components.formatDuration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onVideoClick: (String) -> Unit,
    onChannelClick: ((String) -> Unit)? = null,
    onFeedClick: (() -> Unit)? = null,
    onPlaylistClick: ((Long) -> Unit)? = null,
    vm: LibraryViewModel = viewModel()
) {
    val favorites     by vm.favorites.collectAsState()
    val history       by vm.history.collectAsState()
    val watchLater    by vm.watchLater.collectAsState()
    val subscriptions by vm.subscriptions.collectAsState()
    val playlists     by vm.playlists.collectAsState()
    val downloads     by vm.downloads.collectAsState()
    // Saveable: the chosen tab survives opening a video and coming back —
    // plain remember reset it and the default-tab effect snapped it away
    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var tabInitialized by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val libContext = androidx.compose.ui.platform.LocalContext.current
    val libPrefs = remember { com.streamflow.data.local.AppPreferences.get(libContext) }
    val uiLang by libPrefs.language.collectAsState(initial = "EN")
    // Open on the user's preferred tab (Settings > Home > Default Library tab) —
    // but only on the FIRST open, never over a tab the user has navigated to
    LaunchedEffect(Unit) {
        if (!tabInitialized) {
            selectedTab = libPrefs.libraryTab.first().toIntOrNull()?.coerceIn(0, 6) ?: 1
            tabInitialized = true
        }
    }
    val tabs = listOf("Favorites", "History", "Watch Later", "Channels", "Playlists", "Downloads", "Bookmarks")
        .map { com.streamflow.ui.theme.KmStrings.t(it, uiLang) }
    val bookmarksList by vm.bookmarks.collectAsState()

    // Swipe-to-delete with Undo
    val snackbarHostState = remember { SnackbarHostState() }
    val undoScope = rememberCoroutineScope()
    fun deleteWithUndo(video: VideoItem, remove: (String) -> Unit, restore: (VideoItem) -> Unit) {
        remove(video.url)
        undoScope.launch {
            val r = snackbarHostState.showSnackbar(
                message = "Removed \"${video.title.take(28)}${if (video.title.length > 28) "…" else ""}\"",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (r == SnackbarResult.ActionPerformed) restore(video)
        }
    }

    // Telegram-style big title that collapses into the bar as you scroll
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text("Library", fontWeight = FontWeight.ExtraBold) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background),
                actions = {
                    AnimatedVisibility(selectedTab == 0 && favorites.isNotEmpty()) {
                        IconButton(onClick = { vm.clearFavorites() }) {
                            Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    AnimatedVisibility(selectedTab == 1 && history.isNotEmpty()) {
                        IconButton(onClick = { vm.clearHistory() }) {
                            Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    AnimatedVisibility(selectedTab == 2 && watchLater.isNotEmpty()) {
                        IconButton(onClick = { vm.clearWatchLater() }) {
                            Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LibraryStatsHeader(favoritesCount = favorites.size, history = history)
            val tabCounts = listOf(favorites.size, history.size, watchLater.size, subscriptions.size, playlists.size, downloads.size, bookmarksList.size)
            // Pill-style segmented tabs (replaces the flat underline TabRow)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { i, title ->
                    val selected = selectedTab == i
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { selectedTab = i }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                title,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize   = 13.sp,
                                color      = if (selected) MaterialTheme.colorScheme.onPrimary
                                             else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (tabCounts[i] > 0) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(0.22f)
                                            else MaterialTheme.colorScheme.primary.copy(0.15f)
                                ) {
                                    Text(
                                        tabCounts[i].toString(),
                                        fontSize   = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = if (selected) MaterialTheme.colorScheme.onPrimary
                                                     else MaterialTheme.colorScheme.primary,
                                        modifier   = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "library_tab"
            ) { tab ->
                when (tab) {
                    0 -> VideoListWithSearch(
                            items = favorites.map { it.toVideoItem() },
                            onVideoClick = onVideoClick,
                            onRemove = { url ->
                                favorites.find { it.url == url }?.let {
                                    deleteWithUndo(it.toVideoItem(), vm::removeFavorite, vm::restoreFavorite)
                                }
                            },
                            emptyTitle = "No favorites yet",
                            emptySubtitle = "Tap heart on any video to save it here.",
                            emptyIcon = Icons.Rounded.FavoriteBorder
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
                            onRemove = { url ->
                                history.find { it.url == url }?.let {
                                    deleteWithUndo(it.toVideoItem(), vm::removeHistory, vm::restoreHistory)
                                }
                            },
                            emptyTitle = "No history yet",
                            emptySubtitle = "Videos you watch will appear here.",
                            emptyIcon = Icons.Rounded.History,
                            progressFractions = progressMap,
                            remainingSeconds = remainingMap,
                            header = { HistoryStatsRow(history) }
                        )
                    }
                    2 -> VideoListWithSearch(
                            items = watchLater.map { it.toVideoItem() },
                            onVideoClick = onVideoClick,
                            onRemove = { url ->
                                watchLater.find { it.url == url }?.let {
                                    deleteWithUndo(it.toVideoItem(), vm::removeWatchLater, vm::restoreWatchLater)
                                }
                            },
                            emptyTitle = "No videos saved",
                            emptySubtitle = "Tap bookmark while watching to add videos here.",
                            emptyIcon = Icons.Rounded.BookmarkBorder
                        )
                    3 -> SubscriptionList(
                            subscriptions = subscriptions,
                            onChannelClick = onChannelClick,
                            onUnsubscribe = vm::unsubscribe,
                            onFeedClick = onFeedClick,
                            onSetGroup = vm::setGroup,
                            onSetNotify = vm::setNotify
                        )
                    4 -> PlaylistList(
                            playlists = playlists,
                            onPlaylistClick = onPlaylistClick,
                            onDelete = vm::deletePlaylist,
                            onCreate = vm::createPlaylist
                        )
                    6 -> BookmarkList(
                            bookmarks = bookmarksList,
                            onOpen = { b -> vm.primeBookmarkPosition(b) { onVideoClick(b.videoUrl) } },
                            onDelete = vm::deleteBookmark
                        )
                    else -> DownloadList(
                            downloads = downloads,
                            onPlay = { d ->
                                if (d.status == "DONE" && d.filePath.isNotEmpty()) onVideoClick(d.filePath)
                                else onVideoClick(d.url)
                            },
                            onRemove = vm::removeDownload
                        )
                }
            }
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<PlaylistWithCount>,
    onPlaylistClick: ((Long) -> Unit)?,
    onDelete: (Long) -> Unit,
    onCreate: (String) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        TextButton(
            onClick = { showCreate = true },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("New playlist", fontSize = 13.sp)
        }
        if (playlists.isEmpty()) {
            EmptyState(Icons.Rounded.PlaylistPlay, "No playlists yet",
                "Create one here, or save videos from the player.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(playlists, key = { it.id }) { pl ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onPlaylistClick?.invoke(pl.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.width(100.dp).height(56.dp).clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!pl.firstThumb.isNullOrEmpty()) {
                                AsyncImage(pl.firstThumb, null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(Icons.Rounded.PlaylistPlay, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(pl.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground)
                            Text("${pl.count} videos", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDelete(pl.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, "Delete playlist",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    placeholder = { Text("Playlist name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = newName.isNotBlank(),
                    onClick = { onCreate(newName); newName = ""; showCreate = false }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DownloadList(
    downloads: List<DownloadEntity>,
    onPlay: (DownloadEntity) -> Unit,
    onRemove: (String) -> Unit
) {
    if (downloads.isEmpty()) {
        EmptyState(Icons.Rounded.Download, "No downloads",
            "Use the download button in the player to save videos for offline watching.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(downloads, key = { it.url }) { d ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onPlay(d) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.width(100.dp).height(56.dp).clip(RoundedCornerShape(8.dp))) {
                    AsyncImage(d.thumbnailUrl, null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (d.isAudio) {
                        Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(0.7f), RoundedCornerShape(4.dp))
                            .padding(3.dp)) {
                            Icon(Icons.Rounded.MusicNote, null,
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(12.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(d.title, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground, lineHeight = 17.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when (d.status) {
                            "DONE" -> "Saved offline${if (d.isAudio) " · audio" else ""}"
                            "FAILED" -> "Download failed"
                            else -> "Downloading…"
                        },
                        fontSize = 11.sp,
                        color = when (d.status) {
                            "DONE" -> MaterialTheme.colorScheme.primary
                            "FAILED" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(onClick = { onRemove(d.url) }) {
                    Icon(Icons.Rounded.DeleteOutline, "Remove download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                        modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// Watch stats for the History tab: videos today / this week, total watch time
@Composable
private fun HistoryStatsRow(history: List<HistoryEntity>) {
    if (history.isEmpty()) return
    val dayStart = remember(history.size) {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val weekStart = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    val today = history.count { it.watchedAt >= dayStart }
    val week  = history.count { it.watchedAt >= weekStart }
    val watchMs = history.sumOf { it.position }
    val watchLabel = run {
        val totalMin = watchMs / 60000L
        if (totalMin >= 60) "${totalMin / 60}h ${totalMin % 60}m" else "${totalMin}m"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCell("$today", "Today")
            StatCell("$week", "This week")
            StatCell(watchLabel, "Watch time")
        }
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    remainingSeconds: Map<String, Long> = emptyMap(),
    header: (@Composable () -> Unit)? = null
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
        header?.invoke()
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
                    Icon(Icons.Rounded.Search, null,
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
                            Icon(Icons.Rounded.Close, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { showSort = true }) {
                    Icon(Icons.Rounded.Sort, "Sort",
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
                // Swipe the row sideways to delete (with Undo snackbar)
                var dragX by remember(video.url) { mutableFloatStateOf(0f) }
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = dragX
                            alpha = 1f - (kotlin.math.abs(dragX) / 900f).coerceIn(0f, 0.6f)
                        }
                        .pointerInput(video.url) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, dx -> dragX += dx },
                                onDragEnd = {
                                    if (kotlin.math.abs(dragX) > 240f) onRemove(video.url)
                                    dragX = 0f
                                },
                                onDragCancel = { dragX = 0f }
                            )
                        }
                ) {
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
                        Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
            Icon(icon, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.25f),
                modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(0.55f))
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f),
                textAlign = TextAlign.Center)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SubscriptionList(
    subscriptions: List<SubscriptionEntity>,
    onChannelClick: ((String) -> Unit)?,
    onUnsubscribe: (String) -> Unit,
    onFeedClick: (() -> Unit)? = null,
    onSetGroup: ((String, String) -> Unit)? = null,
    onSetNotify: ((String, Boolean) -> Unit)? = null
) {
    var groupFilter by remember { mutableStateOf("All") }
    var groupTarget by remember { mutableStateOf<SubscriptionEntity?>(null) }
    val groups = remember(subscriptions) {
        subscriptions.mapNotNull { it.groupName.ifBlank { null } }.distinct().sorted()
    }
    val shown = if (groupFilter == "All") subscriptions
                else subscriptions.filter { it.groupName == groupFilter }

    if (subscriptions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                Icon(Icons.Rounded.Subscriptions, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.25f),
                    modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("No subscriptions yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.55f))
                Spacer(Modifier.height(6.dp))
                Text("Open a channel and tap Subscribe to save it here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f),
                    textAlign = TextAlign.Center)
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        // Group filter chips (shown once at least one group exists)
        if (groups.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (listOf("All") + groups).forEach { g ->
                    FilterChip(
                        selected = groupFilter == g,
                        onClick  = { groupFilter = g },
                        label    = { Text(g, fontSize = 12.sp) },
                        shape    = RoundedCornerShape(16.dp)
                    )
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            if (onFeedClick != null) {
                item {
                    val badgeCtx = androidx.compose.ui.platform.LocalContext.current
                    val unseen by remember { com.streamflow.data.local.AppPreferences.get(badgeCtx).unseenFeed }
                        .collectAsState(initial = 0)
                    Button(
                        onClick = onFeedClick,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Subscriptions, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("New videos from your channels", fontWeight = FontWeight.SemiBold)
                        if (unseen > 0) {
                            Spacer(Modifier.width(8.dp))
                            // NEW badge: uploads found since the feed was last opened
                            Surface(shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.onPrimary) {
                                Text(if (unseen > 99) "99+" else "$unseen",
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                            }
                        }
                    }
                }
            }
            items(shown, key = { it.channelUrl }) { sub ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = onChannelClick != null) { onChannelClick?.invoke(sub.channelUrl) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (sub.avatarUrl.isNotEmpty()) {
                        coil.compose.AsyncImage(
                            model = sub.avatarUrl, contentDescription = null,
                            modifier = Modifier.size(46.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    } else {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(sub.name.firstOrNull()?.uppercase() ?: "?",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(sub.name,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground)
                        if (sub.groupName.isNotBlank()) {
                            Text(sub.groupName, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (onSetNotify != null) {
                        IconButton(onClick = { onSetNotify(sub.channelUrl, !sub.notify) }) {
                            Icon(
                                if (sub.notify) Icons.Rounded.NotificationsActive
                                else Icons.Rounded.NotificationsOff,
                                "Toggle notifications",
                                tint = if (sub.notify) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f),
                                modifier = Modifier.size(18.dp))
                        }
                    }
                    if (onSetGroup != null) {
                        IconButton(onClick = { groupTarget = sub }) {
                            Icon(Icons.Rounded.Folder, "Set group",
                                tint = if (sub.groupName.isNotBlank()) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f),
                                modifier = Modifier.size(18.dp))
                        }
                    }
                    TextButton(onClick = { onUnsubscribe(sub.channelUrl) }) {
                        Text("Unsubscribe", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))
            }
        }
    }

    // "Set group" dialog: pick an existing group, type a new one, or clear it
    groupTarget?.let { sub ->
        var groupName by remember(sub.channelUrl) { mutableStateOf(sub.groupName) }
        AlertDialog(
            onDismissRequest = { groupTarget = null },
            title = { Text("Group for ${sub.name}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = groupName, onValueChange = { groupName = it },
                        placeholder = { Text("Group name (e.g. Music)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (groups.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            groups.forEach { g ->
                                SuggestionChip(onClick = { groupName = g },
                                    label = { Text(g, fontSize = 12.sp) })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetGroup?.invoke(sub.channelUrl, groupName)
                    groupTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (sub.groupName.isNotBlank()) {
                        TextButton(onClick = {
                            onSetGroup?.invoke(sub.channelUrl, "")
                            groupTarget = null
                        }) { Text("Remove") }
                    }
                    TextButton(onClick = { groupTarget = null }) { Text("Cancel") }
                }
            }
        )
    }
}

private fun FavoriteEntity.toVideoItem() = VideoItem(url = url, title = title, thumbnailUrl = thumbnailUrl, uploaderName = uploaderName, viewCount = viewCount, duration = duration)
private fun HistoryEntity.toVideoItem()  = VideoItem(url = url, title = title, thumbnailUrl = thumbnailUrl, uploaderName = uploaderName, viewCount = viewCount, duration = duration)
private fun WatchLaterEntity.toVideoItem() = VideoItem(url = url, title = title, thumbnailUrl = thumbnailUrl, uploaderName = uploaderName, viewCount = viewCount, duration = duration)

// Dashboard header: colorful stat tiles summarizing the user's week
@Composable
private fun LibraryStatsHeader(favoritesCount: Int, history: List<HistoryEntity>) {
    val weekCutoff = remember { System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000 }
    val weekly = history.filter { it.watchedAt >= weekCutoff }
    val minutes = weekly.sumOf { it.position } / 1000 / 60
    val timeLabel = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatTile("Watch time", timeLabel, "this week", Modifier.weight(1f))
        StatTile("Watched", "${weekly.size}", "this week", Modifier.weight(1f))
        StatTile("Favorites", "$favoritesCount", "saved", Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(title: String, value: String, sub: String, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(0.10f),
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(0.85f), maxLines = 1)
            Text(sub, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

// Saved video moments: tap to play from that exact timestamp
@Composable
private fun BookmarkList(
    bookmarks: List<com.streamflow.data.local.entity.BookmarkEntity>,
    onOpen: (com.streamflow.data.local.entity.BookmarkEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    if (bookmarks.isEmpty()) {
        EmptyState(Icons.Rounded.BookmarkAdd, "No saved moments",
            "Tap \"Clip moment\" while watching to save the exact time here.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        items(bookmarks, key = { it.id }) { b ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onOpen(b) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box {
                    AsyncImage(
                        model = b.thumbnailUrl, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 120.dp, height = 68.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                    )
                    // Timestamp chip over the thumbnail
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.align(Alignment.BottomStart).padding(5.dp)
                    ) {
                        Text(
                            formatDuration(b.positionMs / 1000),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(b.title, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(2.dp))
                    Text(b.uploaderName, fontSize = 11.sp,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onDelete(b.id) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Close, "Delete bookmark",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                        modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
