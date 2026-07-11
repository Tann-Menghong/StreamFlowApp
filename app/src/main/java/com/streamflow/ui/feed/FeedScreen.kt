package com.streamflow.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamflow.ui.components.ShimmerList
import com.streamflow.ui.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onChannelClick: ((String) -> Unit)? = null,
    vm: FeedViewModel = viewModel()
) {
    LaunchedEffect(Unit) { vm.load() }
    val state by vm.uiState.collectAsState()
    val groups by vm.groups.collectAsState()
    val groupFilter by vm.groupFilter.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New videos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { vm.load(force = true) }) {
                        Icon(Icons.Rounded.Refresh, "Refresh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            // Channel-group filter chips
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
                            onClick  = { vm.setGroupFilter(g) },
                            label    = { Text(g) }
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
            when (val s = state) {
                is FeedUiState.Loading -> ShimmerList()
                is FeedUiState.NoSubscriptions -> Column(
                    Modifier.align(Alignment.Center).padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.Subscriptions, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.25f),
                        modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No subscriptions yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(0.55f))
                    Spacer(Modifier.height(6.dp))
                    Text("Subscribe to channels to see their latest videos here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f),
                        textAlign = TextAlign.Center)
                }
                is FeedUiState.Error -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.load(force = true) }) { Text("Retry") }
                }
                is FeedUiState.Success -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(s.videos, key = { it.url }) { video ->
                        VideoCard(
                            video = video,
                            onClick = { onVideoClick(video.url) },
                            onChannelClick = onChannelClick
                        )
                    }
                }
            }
            }
        }
    }
}
