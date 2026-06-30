package com.streamflow.ui.channel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
    vm: ChannelViewModel = viewModel()
) {
    LaunchedEffect(channelUrl) { vm.loadChannel(channelUrl) }
    val state    by vm.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Load more when near end
    val shouldLoadMore by remember {
        derivedStateOf {
            val last  = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadMore() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (state as? ChannelUiState.Ready)?.channel?.name ?: "Channel"
                    Text(title, fontWeight = FontWeight.Bold, maxLines = 1,
                        style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Search, "Search channel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        when (val s = state) {
            is ChannelUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is ChannelUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)) {
                    Text(s.message, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.loadChannel(channelUrl) }) { Text("Retry") }
                }
            }

            is ChannelUiState.Ready -> {
                val ch          = s.channel
                val isSubscribed by vm.isSubscribed(channelUrl).collectAsState()

                LazyColumn(
                    state          = listState,
                    modifier       = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // ── Channel header ─────────────────────────────────────────────
                    item(key = "channel_header") {
                        ChannelHeader(
                            ch           = ch,
                            channelUrl   = channelUrl,
                            isSubscribed = isSubscribed,
                            onSubscribe  = { vm.subscribe(channelUrl, ch.name, ch.avatarUrl) },
                            onUnsubscribe = { vm.unsubscribe(channelUrl) }
                        )
                    }

                    // ── Videos label ────────────────────────────────────────────────
                    item(key = "videos_label") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (ch.videos.isEmpty() && !s.isLoadingMore) "No videos found" else "Videos",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (ch.videos.isNotEmpty()) {
                                Text(
                                    "${ch.videos.size}${if (s.hasMore) "+" else ""} videos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                    }

                    // ── Empty state ─────────────────────────────────────────────────
                    if (ch.videos.isEmpty() && !s.isLoadingMore) {
                        item(key = "empty") {
                            Box(
                                Modifier.fillMaxWidth().padding(top = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("No videos available",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(8.dp))
                                    Text("This channel's videos couldn't be loaded",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    // ── Video list ──────────────────────────────────────────────────
                    items(ch.videos, key = { it.url }) { video ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(220))
                        ) {
                            Box(Modifier.padding(horizontal = 16.dp)) {
                                VideoCard(
                                    video   = video,
                                    onClick = { onVideoClick(video.url) }
                                )
                            }
                        }
                    }

                    // ── Load more spinner ────────────────────────────────────────────
                    if (s.isLoadingMore) {
                        item(key = "loading_more") {
                            Box(
                                Modifier.fillMaxWidth().padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    Modifier.size(28.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(
    ch: com.streamflow.data.YouTubeRepository.ChannelResult,
    channelUrl: String,
    isSubscribed: Boolean,
    onSubscribe: () -> Unit,
    onUnsubscribe: () -> Unit
) {
    var descExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        // ── Banner ───────────────────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().height(110.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(0.4f),
                            MaterialTheme.colorScheme.tertiary.copy(0.3f)
                        )
                    )
                )
        ) {
            if (ch.bannerUrl.isNotEmpty()) {
                AsyncImage(
                    model              = ch.bannerUrl,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
                // Gradient overlay so avatar pops
                Box(
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(0.35f))
                            )
                        )
                )
            }
        }

        // ── Avatar row ───────────────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            // Avatar overlaps the banner by 24dp
            Box(
                Modifier
                    .size(72.dp)
                    .offset(y = (-24).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (ch.avatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model              = ch.avatarUrl,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Person, null,
                        modifier = Modifier.size(40.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Subscribe + bell buttons on the right
            Row(
                Modifier.align(Alignment.CenterEnd).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSubscribed) {
                    // Subscribed → secondary pill + bell
                    FilledTonalButton(
                        onClick = onUnsubscribe,
                        shape   = RoundedCornerShape(20.dp),
                        colors  = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Subscribed", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    IconButton(
                        onClick  = {},
                        modifier = Modifier.size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.NotificationsNone, "Notifications",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp))
                    }
                } else {
                    Button(
                        onClick = onSubscribe,
                        shape   = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("Subscribe", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Channel name & stats ─────────────────────────────────────────────
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                ch.name,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "@${ch.name.lowercase().replace(" ", "")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (ch.subscriberCount > 0) {
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
                        fontSize = 10.sp)
                    Text(
                        "${formatViews(ch.subscriberCount)} subscribers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Description ──────────────────────────────────────────────────
            if (ch.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    ch.description,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (descExpanded) Int.MAX_VALUE else 2,
                    lineHeight = 18.sp
                )
                TextButton(
                    onClick        = { descExpanded = !descExpanded },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        if (descExpanded) "Show less" else "...more",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
    }
}
