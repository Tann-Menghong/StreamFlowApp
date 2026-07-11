package com.streamflow.ui.channel

import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    vm: ChannelViewModel = viewModel()
) {
    LaunchedEffect(channelUrl) { vm.loadChannel(channelUrl) }

    val data by vm.channel.collectAsState()
    val isSubscribed by vm.isSubscribed.collectAsState()
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3 && data.nextPage != null && !data.isLoadingMore
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
            data.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                        AsyncImage(
                            model = data.bannerUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        )
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
                                modifier = Modifier.size(64.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        } else {
                            Box(
                                Modifier.size(64.dp).clip(CircleShape)
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
                    // About: channel description (tap to expand)
                    if (data.description.isNotBlank()) {
                        var aboutExpanded by remember { mutableStateOf(false) }
                        Text(
                            data.description,
                            fontSize = 12.sp, lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (aboutExpanded) Int.MAX_VALUE else 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 10.dp)
                                .clickable { aboutExpanded = !aboutExpanded }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
                }

                // ── Content tabs (Videos / Shorts / Live) ─────────────────
                item {
                    if (data.availableTabs.size > 1) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            data.availableTabs.forEach { tab ->
                                val label = when (tab) {
                                    "shorts" -> "Shorts"
                                    "livestreams" -> "Live"
                                    else -> "Videos"
                                }
                                FilterChip(
                                    selected = data.selectedTab == tab,
                                    onClick  = { vm.selectTab(tab) },
                                    label    = { Text(label, fontSize = 12.sp) },
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor     = MaterialTheme.colorScheme.onPrimary,
                                        containerColor         = MaterialTheme.colorScheme.surface,
                                        labelColor             = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    } else {
                        Text("Videos",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp))
                    }
                }

                // ── Video list ────────────────────────────────────────────
                items(data.videos, key = { it.url }) { video ->
                    Box(Modifier.padding(horizontal = 14.dp)) {
                        VideoCard(
                            video = video,
                            onClick = { onVideoClick(video.url) },
                            onChannelClick = onChannelClick
                        )
                    }
                }

                // ── Loading more ──────────────────────────────────────────
                if (data.isLoadingMore) {
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
