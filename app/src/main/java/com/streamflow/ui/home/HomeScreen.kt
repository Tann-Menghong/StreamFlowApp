package com.streamflow.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamflow.ui.components.ContinueWatchingCard
import com.streamflow.ui.components.HeroVideoCard
import com.streamflow.ui.components.ShimmerList
import com.streamflow.ui.components.VideoCard
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onVideoClick: (String) -> Unit, vm: HomeViewModel = viewModel()) {
    val state            by vm.uiState.collectAsState()
    val homeLayout       by vm.homeLayout.collectAsState()
    val selectedCategory by vm.selectedCategory.collectAsState()
    val continueWatching by vm.continueWatching.collectAsState()
    val listState = rememberLazyListState()

    // Load more when near end of list
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
                    Text(
                        "StreamFlow",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 22.sp,
                        color      = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = { vm.toggleLayout() }) {
                        Icon(
                            if (homeLayout == "GRID") Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle layout",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { vm.loadTrending() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState  = state,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
                label        = "home_state"
            ) { s ->
                when (s) {
                    is HomeUiState.Loading -> ShimmerList()

                    is HomeUiState.Error -> Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Could not load", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(6.dp))
                        Text(s.message, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp))
                        Spacer(Modifier.height(20.dp))
                        FilledTonalButton(onClick = {
                            if (selectedCategory == "All") vm.loadTrending()
                            else vm.selectCategory(selectedCategory)
                        }) { Text("Retry") }
                    }

                    is HomeUiState.Success -> if (homeLayout == "GRID") {
                        LazyVerticalGrid(
                            columns             = GridCells.Fixed(2),
                            modifier            = Modifier.fillMaxSize(),
                            contentPadding      = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            gridItemsIndexed(s.videos, key = { _, v -> v.url }) { index, video ->
                                var visible by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) { delay((index * 35L).coerceAtMost(280L)); visible = true }
                                AnimatedVisibility(visible = visible,
                                    enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 6 }) {
                                    VideoCard(video = video, onClick = { onVideoClick(video.url) })
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state          = listState,
                            modifier       = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            // ── Category chips ─────────────────────────────
                            item {
                                CategoryChipsRow(
                                    categories       = vm.categories,
                                    selectedCategory = selectedCategory,
                                    onSelect         = { vm.selectCategory(it) }
                                )
                            }

                            // ── Continue Watching ──────────────────────────
                            if (continueWatching.isNotEmpty()) {
                                item {
                                    ContinueWatchingSection(
                                        items       = continueWatching,
                                        onVideoClick = onVideoClick
                                    )
                                }
                            }

                            // ── Section label ──────────────────────────────
                            item {
                                Text(
                                    if (selectedCategory == "All") "Trending" else selectedCategory,
                                    style    = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight   = FontWeight.Bold,
                                        letterSpacing = 1.2.sp,
                                        color        = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.padding(start = 16.dp, bottom = 10.dp, top = 8.dp)
                                )
                            }

                            // ── Hero first card ────────────────────────────
                            if (s.videos.isNotEmpty()) {
                                item(key = "hero_${s.videos.first().url}") {
                                    var visible by remember { mutableStateOf(false) }
                                    LaunchedEffect(Unit) { visible = true }
                                    AnimatedVisibility(visible = visible,
                                        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 8 }
                                    ) {
                                        Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                                            HeroVideoCard(
                                                video   = s.videos.first(),
                                                onClick = { onVideoClick(s.videos.first().url) }
                                            )
                                        }
                                    }
                                }
                            }

                            // ── Rest of videos ─────────────────────────────
                            itemsIndexed(
                                s.videos.drop(1),
                                key = { _, v -> v.url }
                            ) { index, video ->
                                var visible by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) { delay((index * 30L).coerceAtMost(250L)); visible = true }
                                AnimatedVisibility(visible = visible,
                                    enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 6 }
                                ) {
                                    Box(Modifier.padding(horizontal = 16.dp)) {
                                        VideoCard(video = video, onClick = { onVideoClick(video.url) })
                                    }
                                }
                            }

                            if (s.isLoadingMore) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            Modifier.size(24.dp),
                                            color       = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun CategoryChipsRow(
    categories: List<String>,
    selectedCategory: String,
    onSelect: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { cat ->
            val selected = cat == selectedCategory
            FilterChip(
                selected  = selected,
                onClick   = { onSelect(cat) },
                label     = { Text(cat, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                shape     = RoundedCornerShape(20.dp),
                colors    = FilterChipDefaults.filterChipColors(
                    selectedContainerColor    = MaterialTheme.colorScheme.primary,
                    selectedLabelColor        = MaterialTheme.colorScheme.onPrimary,
                    containerColor            = MaterialTheme.colorScheme.surface,
                    labelColor                = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border    = FilterChipDefaults.filterChipBorder(
                    enabled          = true,
                    selected         = selected,
                    borderColor      = MaterialTheme.colorScheme.outline.copy(0.4f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun ContinueWatchingSection(
    items: List<com.streamflow.data.local.entity.HistoryEntity>,
    onVideoClick: (String) -> Unit
) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            "Continue Watching",
            style    = MaterialTheme.typography.labelMedium.copy(
                fontWeight    = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color         = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
        )
        LazyRow(
            contentPadding      = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.url }) { entity ->
                ContinueWatchingCard(
                    entity  = entity,
                    onClick = { onVideoClick(entity.url) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color    = MaterialTheme.colorScheme.outline.copy(0.2f)
        )
    }
}
