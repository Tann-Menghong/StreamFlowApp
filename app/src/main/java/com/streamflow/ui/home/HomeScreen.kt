package com.streamflow.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
fun HomeScreen(onVideoClick: (String) -> Unit, onChannelClick: ((String) -> Unit)? = null, vm: HomeViewModel = viewModel()) {
    val state            by vm.uiState.collectAsState()
    val homeLayout       by vm.homeLayout.collectAsState()
    val selectedCategory by vm.selectedCategory.collectAsState()
    val activeSearch     by vm.activeSearchQuery.collectAsState()
    val continueWatching by vm.continueWatching.collectAsState()
    val showCW           by vm.showContinueWatching.collectAsState()
    val showHero         by vm.showHeroCard.collectAsState()
    val gridCols         by vm.gridColumns.collectAsState()
    val recentSearches   by vm.recentSearches.collectAsState()
    val currentCountry   by vm.currentCountry.collectAsState()
    val listState        = rememberLazyListState()
    var showCountryPicker by remember { mutableStateOf(false) }

    // Search bar state
    var searchExpanded by remember { mutableStateOf(false) }
    var searchText     by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager   = LocalFocusManager.current

    val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    // Sync search text with active query (e.g. when category chip clears search)
    LaunchedEffect(activeSearch) {
        if (activeSearch.isEmpty() && searchText.isNotEmpty()) searchText = ""
    }

    // Load more when near end of list
    val shouldLoadMore by remember {
        derivedStateOf {
            val last  = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadMore() }

    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        floatingActionButton = {
            AnimatedVisibility(
                visible = showFab,
                enter   = fadeIn(tween(200)) + scaleIn(tween(200)),
                exit    = fadeOut(tween(150)) + scaleOut(tween(150))
            ) {
                FloatingActionButton(
                    onClick           = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                    containerColor    = MaterialTheme.colorScheme.primary,
                    contentColor      = MaterialTheme.colorScheme.onPrimary,
                    shape             = RoundedCornerShape(16.dp),
                    modifier          = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, "Scroll to top", modifier = Modifier.size(22.dp))
                }
            }
        },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        AnimatedContent(
                            targetState = searchExpanded,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(130)) },
                            label = "title_search"
                        ) { expanded ->
                            if (expanded) {
                                // Inline search field
                                Surface(
                                    modifier      = Modifier.fillMaxWidth().padding(end = 8.dp).height(40.dp),
                                    shape         = RoundedCornerShape(12.dp),
                                    color         = MaterialTheme.colorScheme.surfaceVariant,
                                    tonalElevation = 0.dp
                                ) {
                                    Row(
                                        Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Search, null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp))
                                        BasicTextField(
                                            value          = searchText,
                                            onValueChange  = { searchText = it },
                                            modifier       = Modifier.weight(1f)
                                                .focusRequester(focusRequester),
                                            singleLine     = true,
                                            textStyle      = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onBackground),
                                            cursorBrush    = SolidColor(MaterialTheme.colorScheme.primary),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(onSearch = {
                                                vm.search(searchText)
                                                focusManager.clearFocus()
                                            }),
                                            decorationBox  = { inner ->
                                                if (searchText.isEmpty()) Text(
                                                    "Search YouTube…",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                inner()
                                            }
                                        )
                                        if (searchText.isNotEmpty()) {
                                            IconButton(
                                                onClick  = { searchText = ""; vm.loadTrending() },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(Icons.Default.Close, null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                                LaunchedEffect(Unit) {
                                    delay(80); focusRequester.requestFocus()
                                }
                            } else {
                                Text(
                                    "StreamFlow",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize   = 22.sp,
                                    color      = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (searchExpanded) {
                                searchExpanded = false
                                searchText     = ""
                                focusManager.clearFocus()
                                vm.loadTrending()
                            } else {
                                searchExpanded = true
                            }
                        }) {
                            Icon(
                                if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (searchExpanded) "Close search" else "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!searchExpanded) {
                            // Country quick-picker
                            Box {
                                TextButton(onClick = { showCountryPicker = true }) {
                                    Text(currentCountry, fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold)
                                }
                                DropdownMenu(expanded = showCountryPicker,
                                    onDismissRequest = { showCountryPicker = false }) {
                                    countryList.forEach { (code, name) ->
                                        DropdownMenuItem(
                                            text = { Text("$name ($code)", fontSize = 13.sp,
                                                fontWeight = if (code == currentCountry) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = { vm.setCountry(code); showCountryPicker = false }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { vm.toggleLayout() }) {
                                Icon(
                                    if (homeLayout == "GRID") Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle layout",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { vm.refresh() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background)
                )
                // Recent searches — shown when search bar is open and user hasn't typed yet
                AnimatedVisibility(
                    visible = searchExpanded && searchText.isEmpty() && recentSearches.isNotEmpty(),
                    enter   = fadeIn(tween(150)) + expandVertically(tween(150)),
                    exit    = fadeOut(tween(100)) + shrinkVertically(tween(100))
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Recent", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { vm.clearRecentSearches() }) {
                                Text("Clear", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        recentSearches.forEach { q ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .then(
                                        Modifier.padding(vertical = 6.dp)
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.History, null,
                                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                                    modifier = Modifier.size(16.dp))
                                Text(
                                    q,
                                    style    = MaterialTheme.typography.bodyMedium,
                                    color    = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f).clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication        = null
                                    ) {
                                        searchText = q
                                        vm.search(q)
                                        focusManager.clearFocus()
                                    }
                                )
                            }
                        }
                    }
                }
                // Category chips — hidden when typing a search
                AnimatedVisibility(
                    visible = !searchExpanded || activeSearch.isEmpty(),
                    enter   = fadeIn(tween(180)) + expandVertically(tween(180)),
                    exit    = fadeOut(tween(130)) + shrinkVertically(tween(130))
                ) {
                    CategoryChipsRow(
                        categories       = vm.categories,
                        selectedCategory = selectedCategory,
                        onSelect         = {
                            searchExpanded = false
                            searchText     = ""
                            focusManager.clearFocus()
                            vm.selectCategory(it)
                        }
                    )
                }
            }
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
                            if (activeSearch.isNotEmpty()) vm.search(activeSearch)
                            else if (selectedCategory == "All") vm.loadTrending()
                            else vm.selectCategory(selectedCategory)
                        }) { Text("Retry") }
                    }

                    is HomeUiState.Success -> {
                        val cols = gridCols.toIntOrNull() ?: 2
                        if (homeLayout == "GRID") {
                            LazyVerticalGrid(
                                columns               = GridCells.Fixed(cols),
                                modifier              = Modifier.fillMaxSize(),
                                contentPadding        = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement   = Arrangement.spacedBy(8.dp)
                            ) {
                                gridItemsIndexed(s.videos, key = { _, v -> v.url }) { index, video ->
                                    var visible by remember { mutableStateOf(false) }
                                    LaunchedEffect(Unit) { delay((index * 35L).coerceAtMost(280L)); visible = true }
                                    AnimatedVisibility(visible,
                                        enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 6 }) {
                                        VideoCard(video = video, onClick = { onVideoClick(video.url) }, onChannelClick = onChannelClick)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state          = listState,
                                modifier       = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                // ── Continue Watching ──────────────────────────
                                if (showCW && continueWatching.isNotEmpty() && activeSearch.isEmpty() && selectedCategory == "All") {
                                    item {
                                        ContinueWatchingSection(
                                            items        = continueWatching,
                                            onVideoClick = onVideoClick
                                        )
                                    }
                                }

                                // ── Section label ──────────────────────────────
                                item {
                                    val label = when {
                                        activeSearch.isNotEmpty() -> "Results for \"$activeSearch\""
                                        selectedCategory != "All" -> selectedCategory
                                        else -> "Trending"
                                    }
                                    Text(
                                        label,
                                        style    = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight    = FontWeight.Bold,
                                            letterSpacing = 1.2.sp,
                                            color         = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.padding(start = 16.dp, bottom = 10.dp, top = 8.dp)
                                    )
                                }

                                // ── Hero first card (only on trending, not search) ──
                                if (showHero && s.videos.isNotEmpty() && activeSearch.isEmpty() && selectedCategory == "All") {
                                    item(key = "hero_${s.videos.first().url}") {
                                        var visible by remember { mutableStateOf(false) }
                                        LaunchedEffect(Unit) { visible = true }
                                        AnimatedVisibility(visible,
                                            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 8 }
                                        ) {
                                            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                                HeroVideoCard(
                                                    video   = s.videos.first(),
                                                    onClick = { onVideoClick(s.videos.first().url) }
                                                )
                                            }
                                        }
                                    }
                                }

                                // ── Video list ─────────────────────────────────
                                val startIndex = if (showHero && activeSearch.isEmpty() && selectedCategory == "All") 1 else 0
                                itemsIndexed(
                                    s.videos.drop(startIndex),
                                    key = { _, v -> v.url }
                                ) { index, video ->
                                    var visible by remember { mutableStateOf(false) }
                                    LaunchedEffect(Unit) { delay((index * 30L).coerceAtMost(250L)); visible = true }
                                    AnimatedVisibility(visible,
                                        enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 6 }
                                    ) {
                                        Box(Modifier.padding(horizontal = 16.dp)) {
                                            VideoCard(video = video, onClick = { onVideoClick(video.url) }, onChannelClick = onChannelClick)
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
}

private val countryList = listOf(
    "US" to "United States", "GB" to "United Kingdom", "CA" to "Canada", "AU" to "Australia",
    "JP" to "Japan", "KR" to "South Korea", "KH" to "Cambodia", "TH" to "Thailand",
    "VN" to "Vietnam", "ID" to "Indonesia", "MY" to "Malaysia", "SG" to "Singapore",
    "PH" to "Philippines", "IN" to "India", "FR" to "France", "DE" to "Germany",
    "BR" to "Brazil", "MX" to "Mexico", "RU" to "Russia", "TR" to "Turkey"
)

@Composable
private fun CategoryChipsRow(
    categories: List<String>,
    selectedCategory: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { cat ->
            val selected = cat == selectedCategory
            FilterChip(
                selected = selected,
                onClick  = { onSelect(cat) },
                label    = {
                    Text(cat,
                        fontSize   = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                },
                shape  = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor     = MaterialTheme.colorScheme.onPrimary,
                    containerColor         = MaterialTheme.colorScheme.surface,
                    labelColor             = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled             = true,
                    selected            = selected,
                    borderColor         = MaterialTheme.colorScheme.outline.copy(0.4f),
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
    Column(Modifier.padding(bottom = 4.dp)) {
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
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.url }) { entity ->
                ContinueWatchingCard(entity = entity, onClick = { onVideoClick(entity.url) })
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color    = MaterialTheme.colorScheme.outline.copy(0.2f)
        )
    }
}
