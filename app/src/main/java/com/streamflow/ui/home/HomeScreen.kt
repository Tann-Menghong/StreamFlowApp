package com.streamflow.ui.home

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.streamflow.data.model.VideoItem
import com.streamflow.ui.components.CompactVideoCard
import com.streamflow.ui.components.ContinueWatchingCard
import com.streamflow.ui.components.HeroVideoCard
import com.streamflow.ui.components.ShimmerList
import com.streamflow.ui.components.VideoCard
import com.streamflow.ui.components.formatDuration
import com.streamflow.ui.components.formatViews
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit,
    onChannelClick: ((String) -> Unit)? = null,
    vm: HomeViewModel = viewModel()
) {
    val state            by vm.uiState.collectAsState()
    val homeLayout       by vm.homeLayout.collectAsState()
    val selectedCategory by vm.selectedCategory.collectAsState()
    val activeSearch     by vm.activeSearchQuery.collectAsState()
    val continueWatching by vm.continueWatching.collectAsState()
    val showCW           by vm.showContinueWatching.collectAsState()
    val showHero         by vm.showHeroCard.collectAsState()
    val gridCols         by vm.gridColumns.collectAsState()
    val cardStyle        by vm.cardStyle.collectAsState()
    val categoriesList   by vm.categories.collectAsState()
    val selectedCats     by vm.selectedCategories.collectAsState()
    val recentSearches   by vm.recentSearches.collectAsState()
    val currentCountry   by vm.currentCountry.collectAsState()
    val hideWatched      by vm.hideWatched.collectAsState()
    val hideShorts       by vm.hideShorts.collectAsState()
    val suggestions      by vm.suggestions.collectAsState()
    val sortMode         by vm.sortMode.collectAsState()
    val listState        = rememberLazyListState()
    val context          = LocalContext.current
    var showCountryPicker by remember { mutableStateOf(false) }
    var showCustomizeSheet by remember { mutableStateOf(false) }

    val hideVideo: (VideoItem) -> Unit = { v ->
        vm.blockVideo(v)
        Toast.makeText(context, "Hidden — you won't see this video again", Toast.LENGTH_SHORT).show()
    }
    val hideChannel: (VideoItem) -> Unit = { v ->
        vm.blockChannel(v)
        Toast.makeText(context, "Channel hidden from your feed", Toast.LENGTH_SHORT).show()
    }

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

    // Load more when near the end — snapshotFlow re-arms after each append
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 5
        }.collect { if (it) vm.loadMore() }
    }
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }.collect { if (it) vm.loadMore() }
    }

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
                                            onValueChange  = { searchText = it; vm.fetchSuggestions(it) },
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
                            IconButton(onClick = { showCustomizeSheet = true }) {
                                Icon(Icons.Default.Tune, contentDescription = "Customize home",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                // Live search suggestions while typing
                AnimatedVisibility(
                    visible = searchExpanded && searchText.isNotEmpty() && suggestions.isNotEmpty(),
                    enter   = fadeIn(tween(150)) + expandVertically(tween(150)),
                    exit    = fadeOut(tween(100)) + shrinkVertically(tween(100))
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        suggestions.forEach { s ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication        = null
                                    ) {
                                        searchText = s
                                        vm.search(s)
                                        focusManager.clearFocus()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Search, null,
                                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                                    modifier = Modifier.size(15.dp))
                                Text(s, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                // Sort chips for search results
                AnimatedVisibility(
                    visible = activeSearch.isNotEmpty(),
                    enter   = fadeIn(tween(150)) + expandVertically(tween(150)),
                    exit    = fadeOut(tween(100)) + shrinkVertically(tween(100))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("RELEVANCE" to "Relevance", "VIEWS" to "Most viewed", "NEWEST" to "Newest").forEach { (key, label) ->
                            FilterChip(
                                selected = sortMode == key,
                                onClick  = { vm.setSortMode(key) },
                                label    = { Text(label, fontSize = 12.sp) },
                                shape    = RoundedCornerShape(16.dp),
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor     = MaterialTheme.colorScheme.onPrimary,
                                    containerColor         = MaterialTheme.colorScheme.surface,
                                    labelColor             = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
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
                        categories       = categoriesList,
                        selectedCategory = selectedCategory,
                        onSelect         = {
                            searchExpanded = false
                            searchText     = ""
                            focusManager.clearFocus()
                            vm.selectCategory(it)
                        },
                        onEdit           = { showCustomizeSheet = true }
                    )
                }
            }
        }
    ) { padding ->
        // Pull down to refresh the feed
        val ptrState = rememberPullToRefreshState()
        if (ptrState.isRefreshing) {
            LaunchedEffect(true) { vm.refresh() }
        }
        LaunchedEffect(state) {
            if (ptrState.isRefreshing && state !is HomeUiState.Loading) ptrState.endRefresh()
        }

        Box(Modifier.fillMaxSize().padding(padding).nestedScroll(ptrState.nestedScrollConnection)) {
            AnimatedContent(
                targetState  = state,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
                // Only animate on Loading/Success/Error changes — load-more appends
                // update in place instead of crossfading (flashing) the whole feed
                contentKey   = { it::class },
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
                                state                 = gridState,
                                modifier              = Modifier.fillMaxSize(),
                                contentPadding        = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement   = Arrangement.spacedBy(8.dp)
                            ) {
                                gridItemsIndexed(s.videos, key = { _, v -> v.url }) { index, video ->
                                    var visible by remember { mutableStateOf(false) }
                                    LaunchedEffect(Unit) { delay((index * 35L).coerceAtMost(280L)); visible = true }
                                    AnimatedVisibility(visible,
                                        modifier = Modifier.animateItemPlacement(),
                                        enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 6 }) {
                                        VideoCard(video = video, onClick = { onVideoClick(video.url) },
                                            onChannelClick = onChannelClick,
                                            onNotInterested = { hideVideo(video) },
                                            onBlockChannel = if (video.uploaderUrl.isNotEmpty())
                                                ({ hideChannel(video) }) else null)
                                    }
                                }
                                if (s.isLoadingMore) {
                                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
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
                                        activeSearch.isNotEmpty() -> "Results for \"${activeSearch}\""
                                        selectedCategory != "All" -> selectedCategory
                                        else -> "For You"
                                    }
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight    = FontWeight.Bold,
                                            letterSpacing = 1.2.sp,
                                            color         = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp, top = 8.dp)
                                    )
                                }

                                // ── Featured horizontal row (top 5 trending) ────
                                if (showHero && s.videos.size >= 3 && activeSearch.isEmpty() && selectedCategory == "All") {
                                    item(key = "featured_row") {
                                        FeaturedVideoRow(
                                            videos = s.videos.take(5),
                                            onVideoClick = onVideoClick
                                        )
                                    }
                                }

                                // ── Full video list ─────────────────────────────
                                itemsIndexed(
                                    s.videos,
                                    key = { _, v -> "list_${v.url}" }
                                ) { index, video ->
                                    var visible by remember { mutableStateOf(false) }
                                    LaunchedEffect(Unit) { delay((index * 25L).coerceAtMost(200L)); visible = true }
                                    AnimatedVisibility(visible,
                                        modifier = Modifier.animateItemPlacement(),
                                        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 6 }
                                    ) {
                                        Box(Modifier.padding(horizontal = 16.dp)) {
                                            if (cardStyle == "COMPACT") {
                                                CompactVideoCard(video = video, onClick = { onVideoClick(video.url) },
                                                    onChannelClick = onChannelClick,
                                                    onNotInterested = { hideVideo(video) },
                                                    onBlockChannel = if (video.uploaderUrl.isNotEmpty())
                                                        ({ hideChannel(video) }) else null)
                                            } else {
                                                VideoCard(video = video, onClick = { onVideoClick(video.url) },
                                                    onChannelClick = onChannelClick,
                                                    onNotInterested = { hideVideo(video) },
                                                    onBlockChannel = if (video.uploaderUrl.isNotEmpty())
                                                        ({ hideChannel(video) }) else null)
                                            }
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

            PullToRefreshContainer(
                state    = ptrState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor   = MaterialTheme.colorScheme.primary
            )
        }

        if (showCustomizeSheet) {
            CustomizeHomeSheet(
                homeLayout         = homeLayout,
                gridCols           = gridCols,
                cardStyle          = cardStyle,
                showCW             = showCW,
                showHero           = showHero,
                hideWatched        = hideWatched,
                hideShorts         = hideShorts,
                selectedCategories = selectedCats,
                categoryPool       = vm.categoryPool,
                onLayout           = vm::setLayout,
                onGridCols         = vm::setGridColumns,
                onCardStyle        = vm::setCardStyle,
                onShowCW           = vm::setShowCW,
                onShowHero         = vm::setShowFeatured,
                onHideWatched      = vm::setHideWatched,
                onHideShorts       = vm::setHideShorts,
                onToggleCategory   = vm::toggleCategory,
                onResetCategories  = vm::resetCategories,
                onDismiss          = { showCustomizeSheet = false }
            )
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
    onSelect: (String) -> Unit,
    onEdit: (() -> Unit)? = null
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
        if (onEdit != null) {
            AssistChip(
                onClick = onEdit,
                label   = { Text("Edit", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                shape  = RoundedCornerShape(20.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor     = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled     = true,
                    borderColor = MaterialTheme.colorScheme.outline.copy(0.4f)
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CustomizeHomeSheet(
    homeLayout: String,
    gridCols: String,
    cardStyle: String,
    showCW: Boolean,
    showHero: Boolean,
    hideWatched: Boolean,
    hideShorts: Boolean,
    selectedCategories: List<String>,
    categoryPool: List<String>,
    onLayout: (String) -> Unit,
    onGridCols: (String) -> Unit,
    onCardStyle: (String) -> Unit,
    onShowCW: (Boolean) -> Unit,
    onShowHero: (Boolean) -> Unit,
    onHideWatched: (Boolean) -> Unit,
    onHideShorts: (Boolean) -> Unit,
    onToggleCategory: (String) -> Unit,
    onResetCategories: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            Text("Customize Home", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))

            // ── Layout ─────────────────────────────────────────
            SheetSectionLabel("LAYOUT")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetChoiceChip("List", homeLayout == "LIST",
                    icon = Icons.Default.ViewList) { onLayout("LIST") }
                SheetChoiceChip("Grid", homeLayout == "GRID",
                    icon = Icons.Default.GridView) { onLayout("GRID") }
            }
            Spacer(Modifier.height(10.dp))
            AnimatedContent(targetState = homeLayout, label = "layout_options") { layout ->
                if (layout == "GRID") {
                    Column {
                        Text("Grid columns", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SheetChoiceChip("2 columns", gridCols == "2") { onGridCols("2") }
                            SheetChoiceChip("3 columns", gridCols == "3") { onGridCols("3") }
                        }
                    }
                } else {
                    Column {
                        Text("Card style", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SheetChoiceChip("Comfortable", cardStyle == "COMFORT") { onCardStyle("COMFORT") }
                            SheetChoiceChip("Compact", cardStyle == "COMPACT") { onCardStyle("COMPACT") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Sections ───────────────────────────────────────
            SheetSectionLabel("SECTIONS")
            SheetSwitchRow("Continue Watching", "Resume videos you started", showCW, onShowCW)
            SheetSwitchRow("Featured row", "Horizontal top-trending strip", showHero, onShowHero)
            SheetSwitchRow("Hide watched videos", "Skip videos already in your history", hideWatched, onHideWatched)
            SheetSwitchRow("Hide Shorts", "Skip videos under 60 seconds", hideShorts, onHideShorts)

            Spacer(Modifier.height(20.dp))

            // ── Categories ─────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SheetSectionLabel("CATEGORY CHIPS")
                TextButton(onClick = onResetCategories) {
                    Text("Reset", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text("Pick the topics shown on your home feed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(4.dp)
            ) {
                categoryPool.forEach { cat ->
                    val selected = cat in selectedCategories
                    FilterChip(
                        selected = selected,
                        onClick  = { onToggleCategory(cat) },
                        label    = { Text(cat, fontSize = 12.sp) },
                        shape    = RoundedCornerShape(16.dp),
                        colors   = FilterChipDefaults.filterChipColors(
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
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.primary))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SheetChoiceChip(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = { Text(label, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        leadingIcon = if (icon != null) ({
            Icon(icon, null, modifier = Modifier.size(16.dp))
        }) else null,
        shape  = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor     = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
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

@Composable
private fun SheetSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
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

@Composable
private fun FeaturedVideoRow(
    videos: List<VideoItem>,
    onVideoClick: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Featured", style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary))
            Text("See all ›", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            items(videos, key = { "feat_${it.url}" }) { video ->
                FeaturedCard(video = video, onClick = { onVideoClick(video.url) })
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outline.copy(0.15f)
        )
    }
}

@Composable
private fun FeaturedCard(video: VideoItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (video.duration > 0) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(5.dp)
                        .background(Color.Black.copy(0.78f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(formatDuration(video.duration), color = Color.White,
                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(video.title, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground, lineHeight = 16.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            buildString {
                append(video.uploaderName)
                if (video.viewCount > 0) append("  ·  ${formatViews(video.viewCount)} views")
            },
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}
