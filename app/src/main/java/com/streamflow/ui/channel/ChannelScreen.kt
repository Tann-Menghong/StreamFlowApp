package com.streamflow.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.streamflow.ui.components.VideoCard
import com.streamflow.ui.components.formatViews

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelUrl: String,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onChannelClick: ((String) -> Unit)? = null,
    onPlaylistClick: ((String) -> Unit)? = null,
    vm: ChannelViewModel = viewModel()
) {
    LaunchedEffect(channelUrl) { vm.loadChannel(channelUrl) }

    val data by vm.channel.collectAsState()
    val isSubscribed by vm.isSubscribed.collectAsState()
    val listState = rememberLazyListState()
    // About is a local tab — it shows data we already have, no network call
    var showAbout by remember(channelUrl) { mutableStateOf(false) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3 && data.nextPage != null && !data.isLoadingMore && !showAbout
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadMore() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(data.name.ifEmpty { "Channel" }, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        when {
            // Full-screen spinner only on the very first load; when switching
            // tabs the header/tab bar stay put and only the list area reloads
            data.isLoading && data.name.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            data.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("Failed to load channel", fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(4.dp))
                    Text(data.error ?: "", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.loadChannel(channelUrl) }) { Text("Retry") }
                }
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // ── Banner ────────────────────────────────────────────────
                if (data.bannerUrl.isNotEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(140.dp)) {
                            AsyncImage(
                                model = data.bannerUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                            )
                            // Fade the banner into the page so the header text pops
                            Box(
                                Modifier.fillMaxWidth().height(56.dp).align(Alignment.BottomCenter)
                                    .background(Brush.verticalGradient(listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(0.9f))))
                            )
                        }
                    }
                }

                // ── Channel header ────────────────────────────────────────
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (data.avatarUrl.isNotEmpty()) {
                            AsyncImage(
                                model = data.avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(0.5f), CircleShape)
                            )
                        } else {
                            Box(
                                Modifier.size(72.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(data.name, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground)
                            if (data.subscriberCount > 0) {
                                Text("${formatViews(data.subscriberCount)} subscribers",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Button(
                            onClick = { vm.toggleSubscribe() },
                            colors = if (isSubscribed)
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            else ButtonDefaults.buttonColors(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(if (isSubscribed) "Subscribed" else "Subscribe",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // ── YouTube-style tabs: Videos / Shorts / Live / Playlists / About ──
                item {
                    val order = listOf("videos", "shorts", "livestreams", "playlists")
                    val contentTabs = order.filter { it in data.availableTabs }.ifEmpty { listOf("videos") }
                    val tabs = contentTabs + "about"
                    val selectedIndex =
                        if (showAbout) tabs.lastIndex
                        else contentTabs.indexOf(data.selectedTab).coerceAtLeast(0)
                    ScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        edgePadding = 4.dp,
                        containerColor = MaterialTheme.colorScheme.background
                    ) {
                        tabs.forEachIndexed { i, tab ->
                            Tab(
                                selected = i == selectedIndex,
                                onClick = {
                                    if (tab == "about") showAbout = true
                                    else { showAbout = false; vm.selectTab(tab) }
                                },
                                text = {
                                    Text(
                                        when (tab) {
                                            "shorts" -> "Shorts"
                                            "livestreams" -> "Live"
                                            "playlists" -> "Playlists"
                                            "about" -> "About"
                                            else -> "Videos"
                                        },
                                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                            )
                        }
                    }
                }

                when {
                    // ── About tab ─────────────────────────────────────────
                    showAbout -> item {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            if (data.subscriberCount > 0) {
                                Text("${formatViews(data.subscriberCount)} subscribers",
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground)
                                Spacer(Modifier.height(10.dp))
                            }
                            Text(
                                data.description.ifBlank { "This channel has no description." },
                                fontSize = 13.sp, lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // ── Tab switch in progress ────────────────────────────
                    data.isLoading -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                        }
                    }

                    // ── Playlists tab ─────────────────────────────────────
                    data.selectedTab == "playlists" -> {
                        if (data.playlists.isEmpty()) {
                            item {
                                Text("No playlists", fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp))
                            }
                        }
                        items(data.playlists, key = { it.url }) { pl ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { onPlaylistClick?.invoke(pl.url) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box {
                                    AsyncImage(
                                        model = pl.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(120.dp, 68.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                    if (pl.streamCount >= 0) {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.75f),
                                            shape = RoundedCornerShape(topStart = 6.dp),
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        ) {
                                            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.PlaylistPlay, null,
                                                    tint = Color.White, modifier = Modifier.size(12.dp))
                                                Spacer(Modifier.width(2.dp))
                                                Text("${pl.streamCount}", color = Color.White, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(pl.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onBackground)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        if (pl.streamCount >= 0) "${pl.streamCount} videos" else "Playlist",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // ── Videos / Shorts / Live list ───────────────────────
                    else -> items(data.videos, key = { it.url }) { video ->
                        Box(Modifier.padding(horizontal = 14.dp)) {
                            VideoCard(
                                video = video,
                                onClick = { onVideoClick(video.url) },
                                onChannelClick = onChannelClick
                            )
                        }
                    }
                }

                // ── Loading more ──────────────────────────────────────────
                if (data.isLoadingMore && !showAbout) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}
