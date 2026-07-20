package com.streamflow.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.StreamFlowApp
import com.streamflow.data.YouTubeRepository
import com.streamflow.data.friendlyError
import com.streamflow.data.local.entity.BlockedItemEntity
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.model.VideoItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val videos: List<VideoItem>,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = false
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo  = YouTubeRepository()
    private val prefs = (app as StreamFlowApp).prefs
    private val db    = (app as StreamFlowApp).database
    private var nextPage: Page? = null

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)

    // Sort applied to search results only (client-side)
    private val _sortMode = MutableStateFlow("RELEVANCE") // RELEVANCE | VIEWS | NEWEST
    val sortMode: StateFlow<String> = _sortMode
    fun setSortMode(v: String) { _sortMode.value = v }

    private data class FeedFilters(
        val blocked: List<BlockedItemEntity>,
        val hideWatched: Boolean,
        val hideShorts: Boolean,
        val history: List<HistoryEntity>,
        val sort: String
    )

    private val feedFilters = combine(
        db.blockedDao().getAll(),
        prefs.hideWatched,
        prefs.hideShorts,
        db.historyDao().getAll(),
        _sortMode
    ) { blocked, hideWatched, hideShorts, history, sort ->
        FeedFilters(blocked, hideWatched, hideShorts, history, sort)
    }

    // Displayed feed = raw feed minus "not interested" videos/channels, optionally
    // watched videos and Shorts; search results get the picked sort applied.
    val uiState: StateFlow<HomeUiState> = combine(_uiState, feedFilters) { state, f ->
        if (state !is HomeUiState.Success) return@combine state
        val blockedVideos   = f.blocked.asSequence().filter { it.type == "VIDEO" }.mapTo(HashSet()) { it.itemKey }
        val blockedChannels = f.blocked.asSequence().filter { it.type == "CHANNEL" }.mapTo(HashSet()) { it.itemKey }
        val watched = if (f.hideWatched) f.history.mapTo(HashSet()) { it.url } else emptySet<String>()
        val filtered = state.videos.filter { v ->
            v.url !in blockedVideos &&
            (v.uploaderUrl.isEmpty() || v.uploaderUrl !in blockedChannels) &&
            v.url !in watched &&
            // 1..75 matches ShortsViewModel's definition of a short — 60 missed
            // the 61-75s shorts that the Shorts feed itself includes
            !(f.hideShorts && v.duration in 1..75)
        }
        val sorted = when {
            _activeSearchQuery.value.isEmpty() -> filtered
            f.sort == "VIEWS"  -> filtered.sortedByDescending { it.viewCount }
            f.sort == "NEWEST" -> filtered.sortedByDescending { it.uploadedEpoch }
            else -> filtered
        }
        state.copy(videos = sorted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState.Loading)

    // ── Live search suggestions ──────────────────────────────
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions
    private var suggestionsJob: kotlinx.coroutines.Job? = null

    fun fetchSuggestions(query: String) {
        suggestionsJob?.cancel()
        if (query.isBlank()) { _suggestions.value = emptyList(); return }
        suggestionsJob = viewModelScope.launch {
            kotlinx.coroutines.delay(250) // debounce typing
            _suggestions.value = try { repo.getSearchSuggestions(query) } catch (_: Exception) { emptyList() }
        }
    }

    fun clearSuggestions() {
        suggestionsJob?.cancel()
        _suggestions.value = emptyList()
    }

    // Watched-progress per video url, for the red progress bar on thumbnails
    val historyProgress: StateFlow<Map<String, Float>> = db.historyDao().getAll()
        .map { list ->
            list.asSequence()
                .filter { it.duration > 0L && it.position > 0L }
                .associate { it.url to (it.position / 1000f / it.duration).coerceIn(0f, 1f) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val incognito = prefs.incognito.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hideWatched = prefs.hideWatched.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setHideWatched(v: Boolean) = viewModelScope.launch { prefs.setHideWatched(v) }

    val hideShorts = prefs.hideShorts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setHideShorts(v: Boolean) = viewModelScope.launch { prefs.setHideShorts(v) }

    val showCategoryBar = prefs.showCategoryBar.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setShowCategoryBar(v: Boolean) = viewModelScope.launch { prefs.setShowCategoryBar(v) }

    fun blockVideo(v: VideoItem) = viewModelScope.launch {
        db.blockedDao().insert(BlockedItemEntity(itemKey = v.url, type = "VIDEO", name = v.title))
    }

    fun blockChannel(v: VideoItem) = viewModelScope.launch {
        if (v.uploaderUrl.isNotEmpty()) {
            db.blockedDao().insert(BlockedItemEntity(itemKey = v.uploaderUrl, type = "CHANNEL", name = v.uploaderName))
        }
    }

    fun unblock(key: String) = viewModelScope.launch {
        if (key.isNotEmpty()) db.blockedDao().delete(key)
    }

    // Persisted layout/display prefs
    val homeLayout           = prefs.homeLayout.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "LIST")
    val showContinueWatching = prefs.showContinueWatching.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showHeroCard         = prefs.showHeroCard.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val gridColumns          = prefs.gridColumns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "2")
    val cardStyle            = prefs.homeCardStyle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "COMFORT")

    // Full pool of pickable category chips; user selects which ones show on Home
    private val baseCategoryPool = listOf(
        "Music", "Gaming", "Sports", "News", "Tech", "Comedy", "Film",
        "Podcasts", "Cooking", "Travel", "Fitness", "Education", "Beauty",
        "Cars", "Animals", "Anime", "Science", "Fashion", "Khmer News", "K-Pop"
    )
    val customCategories = prefs.customCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categoryPool: StateFlow<List<String>> = prefs.customCategories
        .map { baseCategoryPool + it.filter { c -> c !in baseCategoryPool } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), baseCategoryPool)

    fun addCustomCategory(name: String) {
        // Categories are stored comma-joined, so a comma in the name would corrupt the list
        val cat = name.replace(",", " ").replace(Regex("\\s+"), " ").trim().take(24)
        if (cat.isBlank()) return
        viewModelScope.launch {
            val custom = prefs.customCategories.first()
            if (cat !in custom && cat !in baseCategoryPool) {
                prefs.setCustomCategories(custom + cat)
            }
            // Select it right away so it appears on Home
            val picked = prefs.homeCategories.first()
            if (cat !in picked) prefs.setHomeCategories(picked + cat)
        }
    }

    fun removeCustomCategory(name: String) {
        viewModelScope.launch {
            prefs.setCustomCategories(prefs.customCategories.first() - name)
            prefs.setHomeCategories(prefs.homeCategories.first() - name)
            if (name == _selectedCategory.value) loadTrending()
        }
    }

    fun removeRecentSearch(query: String) = viewModelScope.launch {
        prefs.removeRecentSearch(query)
    }

    private val defaultCategories = listOf("Music", "Gaming", "Sports", "News", "Tech", "Comedy", "Film")

    val selectedCategories = prefs.homeCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultCategories)

    val categories: StateFlow<List<String>> = prefs.homeCategories
        .map { listOf("All") + it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All") + defaultCategories)

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory

    // Active text search query (empty = not in text-search mode)
    private val _activeSearchQuery = MutableStateFlow("")
    val activeSearchQuery: StateFlow<String> = _activeSearchQuery

    // ── Search result type: VIDEOS | CHANNELS | PLAYLISTS ────────
    private val _searchType = MutableStateFlow("VIDEOS")
    val searchType: StateFlow<String> = _searchType

    private val _channelResults = MutableStateFlow<List<YouTubeRepository.ChannelItem>>(emptyList())
    val channelResults: StateFlow<List<YouTubeRepository.ChannelItem>> = _channelResults

    private val _playlistResults = MutableStateFlow<List<YouTubeRepository.PlaylistItem>>(emptyList())
    val playlistResults: StateFlow<List<YouTubeRepository.PlaylistItem>> = _playlistResults

    private val _typeLoading = MutableStateFlow(false)
    val typeLoading: StateFlow<Boolean> = _typeLoading

    fun setSearchType(type: String) {
        if (_searchType.value == type) return
        _searchType.value = type
        val q = _activeSearchQuery.value
        if (q.isEmpty() || type == "VIDEOS") return
        viewModelScope.launch {
            _typeLoading.value = true
            try {
                when (type) {
                    "CHANNELS"  -> {
                        val res = repo.searchChannels(q)
                        // A newer search may have replaced the query mid-flight —
                        // don't show the old query's channels under the new one
                        if (q == _activeSearchQuery.value) _channelResults.value = res
                    }
                    "PLAYLISTS" -> {
                        val res = repo.searchPlaylists(q)
                        if (q == _activeSearchQuery.value) _playlistResults.value = res
                    }
                }
            } catch (_: Exception) {
                if (q == _activeSearchQuery.value) {
                    if (type == "CHANNELS") _channelResults.value = emptyList()
                    else _playlistResults.value = emptyList()
                }
            } finally {
                _typeLoading.value = false
            }
        }
    }

    val continueWatching: StateFlow<List<HistoryEntity>> = db.historyDao()
        .getRecentWithProgress(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSearches: StateFlow<List<String>> = prefs.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentCountry: StateFlow<String> = prefs.country
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "US")

    fun setCountry(code: String) {
        viewModelScope.launch {
            prefs.setCountry(code)
            loadTrending()
        }
    }

    private var isSearchMode = false
    private var currentQuery = ""

    // Endless "For You" feed: seed queries built from watch history, recent
    // searches, and the user's picked categories. Trending alone is a single
    // fixed page with no pagination, so seeds are what make the feed infinite
    // and make every refresh surface new videos.
    private var feedSeeds: List<String> = emptyList()
    private var seedIndex = 0
    private val seenUrls = HashSet<String>()
    // Consecutive load-more rounds that produced no new videos. Once a full pass
    // over every seed yields nothing new twice in a row, the feed is genuinely
    // exhausted — stop advertising hasMore so scrolling to the bottom doesn't
    // keep firing a dozen wasted searches on every attempt. One blank round is
    // tolerated so a brief offline blip doesn't end the feed. Reset on reload.
    private var emptyFeedRounds = 0

    // Bumped whenever the feed is replaced (reload/search/category) so an
    // in-flight loadMore can't append stale results into the new feed
    private var feedGeneration = 0

    private suspend fun buildSeeds(): List<String> {
        val history = try { db.historyDao().getAll().first() } catch (_: Exception) { emptyList() }
        val channelSeeds = history.asSequence()
            .map { it.uploaderName }
            .filter { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
            .distinct().take(10).toList().shuffled().take(5)
        val searchSeeds = try { prefs.recentSearches.first().take(4) } catch (_: Exception) { emptyList() }
        val categorySeeds = try { prefs.homeCategories.first() } catch (_: Exception) { emptyList() }
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val evergreen = listOf(
            "popular this week", "new music video", "viral videos",
            "highlights today", "best of $year", "documentary"
        )
        return ((channelSeeds + searchSeeds).shuffled() + (categorySeeds + evergreen).shuffled()).distinct()
    }

    // Blocks of 3 trending + 2 fresh picks so a refresh visibly brings new videos
    private fun interleave(a: List<VideoItem>, b: List<VideoItem>): List<VideoItem> {
        val out = ArrayList<VideoItem>(a.size + b.size)
        var i = 0; var j = 0
        while (i < a.size || j < b.size) {
            repeat(3) { if (i < a.size) out.add(a[i++]) }
            repeat(2) { if (j < b.size) out.add(b[j++]) }
        }
        return out
    }

    // True while the feed on screen is last session's cached snapshot; keeps
    // loadTrending from flashing a spinner over content the user can already use
    private var showingCachedFeed = false

    init {
        viewModelScope.launch {
            // Instant startup: show the last session's feed right away, then
            // let the fresh load replace it in the background
            try {
                val cached = prefs.cachedFeed.first()
                if (cached.isNotEmpty() && _uiState.value is HomeUiState.Loading) {
                    showingCachedFeed = true
                    _uiState.value = HomeUiState.Success(cached, hasMore = false)
                }
            } catch (_: Exception) {}
            loadTrending()
        }
    }

    fun loadTrending() {
        isSearchMode = false
        currentQuery = ""
        _selectedCategory.value = "All"
        _activeSearchQuery.value = ""
        val gen = ++feedGeneration
        viewModelScope.launch {
            // Keep showing the cached feed instead of a spinner while refreshing
            if (!showingCachedFeed) _uiState.value = HomeUiState.Loading
            nextPage = null
            try {
                val country = prefs.country.first()
                feedSeeds = buildSeeds()
                seedIndex = 0
                seenUrls.clear()
                emptyFeedRounds = 0

                // Trending + one personalized chunk in parallel; shuffle trending
                // so every refresh has a different order
                var trendingErr: Exception? = null
                val (trendingRes, seedChunk) = coroutineScope {
                    val t = async {
                        try { repo.getTrending(country) } catch (e: Exception) { trendingErr = e; null }
                    }
                    val s = async {
                        val seed = feedSeeds.firstOrNull() ?: return@async emptyList<VideoItem>()
                        seedIndex = 1
                        try { repo.search(seed).videos } catch (_: Exception) { emptyList() }
                    }
                    t.await() to s.await()
                }
                if (gen != feedGeneration) return@launch // superseded by a newer feed/search/category
                val trendingVideos = trendingRes?.videos?.shuffled() ?: emptyList()
                if (trendingVideos.isEmpty() && seedChunk.isEmpty()) {
                    throw (trendingErr ?: Exception("No videos found"))
                }
                nextPage = trendingRes?.nextPage
                val mixed = interleave(trendingVideos, seedChunk).filter { seenUrls.add(it.url) }
                showingCachedFeed = false
                _uiState.value = HomeUiState.Success(mixed, hasMore = true)
                // Snapshot for instant startup next launch
                try { prefs.saveCachedFeed(mixed) } catch (_: Exception) {}
            } catch (e: Exception) {
                // A stale failure must not replace a newer feed with an error page
                if (gen != feedGeneration) return@launch
                // Offline / failed refresh: the cached feed is better than an error page
                if (!showingCachedFeed) _uiState.value = HomeUiState.Error(friendlyError(e))
            }
        }
    }

    // Called by the search bar when user submits a text query
    fun search(rawQuery: String) {
        // Trim: " cats" and "cats" used to be two different recent-search entries
        val query = rawQuery.trim()
        if (query.isBlank()) { loadTrending(); return }
        showingCachedFeed = false
        isSearchMode = true
        currentQuery = query
        _selectedCategory.value = "All"
        _activeSearchQuery.value = query
        _sortMode.value = "RELEVANCE"
        _searchType.value = "VIDEOS"
        _channelResults.value = emptyList()
        _playlistResults.value = emptyList()
        clearSuggestions()
        val gen = ++feedGeneration
        viewModelScope.launch {
            // Incognito: leave no trace in recent searches
            if (!prefs.incognito.first()) prefs.addRecentSearch(query)
            _uiState.value = HomeUiState.Loading
            nextPage = null
            try {
                val result = repo.search(query)
                if (gen != feedGeneration) return@launch // superseded by a newer query
                nextPage   = result.nextPage
                _uiState.value = HomeUiState.Success(result.videos, hasMore = result.nextPage != null)
            } catch (e: Exception) {
                if (gen != feedGeneration) return@launch // stale failure — don't clobber the newer feed
                _uiState.value = HomeUiState.Error(friendlyError(e))
            }
        }
    }

    fun clearRecentSearches() { viewModelScope.launch { prefs.clearRecentSearches() } }

    // Called by category chips
    fun selectCategory(cat: String) {
        _selectedCategory.value = cat
        _activeSearchQuery.value = ""
        if (cat == "All") {
            loadTrending()
        } else {
            showingCachedFeed = false
            isSearchMode = true
            currentQuery = cat
            val gen = ++feedGeneration
            viewModelScope.launch {
                _uiState.value = HomeUiState.Loading
                nextPage = null
                try {
                    val result = repo.search(cat)
                    if (gen != feedGeneration) return@launch // superseded by a newer category/search
                    nextPage   = result.nextPage
                    _uiState.value = HomeUiState.Success(result.videos, hasMore = result.nextPage != null)
                } catch (e: Exception) {
                    if (gen != feedGeneration) return@launch // stale failure — don't clobber the newer feed
                    _uiState.value = HomeUiState.Error(friendlyError(e))
                }
            }
        }
    }

    fun refresh() {
        if (!isSearchMode) {
            // Full reload: new shuffle, new seeds, fresh recommendations
            loadTrending()
            return
        }
        val gen = ++feedGeneration
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val result = repo.search(currentQuery)
                if (gen != feedGeneration) return@launch // superseded by a newer refresh/search
                nextPage   = result.nextPage
                _uiState.value = HomeUiState.Success(result.videos, hasMore = result.nextPage != null)
            } catch (e: Exception) {
                if (gen != feedGeneration) return@launch // stale failure — don't clobber the newer feed
                _uiState.value = HomeUiState.Error(friendlyError(e))
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value as? HomeUiState.Success ?: return
        if (current.isLoadingMore || !current.hasMore) return
        val gen = feedGeneration
        viewModelScope.launch {
            _uiState.value = current.copy(isLoadingMore = true)
            val fresh = try { fetchMoreVideos() } catch (_: Exception) { emptyList() }
            if (gen != feedGeneration) return@launch // feed was replaced mid-load
            val base = _uiState.value as? HomeUiState.Success ?: return@launch
            // Final dedupe at append time — duplicate LazyColumn keys crash the app
            val existing = base.videos.mapTo(HashSet()) { it.url }
            val added = fresh.filter { it.url !in existing }
            // Track exhaustion so an all-seeds-seen feed stops re-searching forever
            emptyFeedRounds = if (added.isEmpty()) emptyFeedRounds + 1 else 0
            _uiState.value = base.copy(
                videos        = base.videos + added,
                isLoadingMore = false,
                // Feed mode: seeds are endless, so one empty/failed round (e.g. a
                // brief offline moment) must not end the feed — keep hasMore true
                // and retry once more; only after two blank rounds is it truly
                // exhausted and we stop, so scrolling the bottom quits hammering
                // the network with searches that only return already-seen videos.
                hasMore       = if (isSearchMode) nextPage != null
                                else feedSeeds.isNotEmpty() && emptyFeedRounds < 2
            )
        }
    }

    private suspend fun fetchMoreVideos(): List<VideoItem> {
        // Search / category mode: normal pagination
        if (isSearchMode) {
            val page = nextPage ?: return emptyList()
            val r = repo.searchNextPage(currentQuery, page)
            nextPage = r.nextPage
            return r.videos
        }
        // Home feed: trending pagination first (usually absent) …
        nextPage?.let { p ->
            val r = try { repo.getTrendingNextPage(p) } catch (_: Exception) { null }
            // Only advance the page on success — nulling it on a transient
            // failure permanently ended trending pagination
            if (r != null) nextPage = r.nextPage
            val fresh = r?.videos?.filter { seenUrls.add(it.url) } ?: emptyList()
            if (fresh.isNotEmpty()) return fresh
        }
        // … then endless seed chunks: each scroll pulls the next topic,
        // skipping videos already in the feed
        repeat(feedSeeds.size) {
            if (feedSeeds.isEmpty()) return emptyList()
            val seed = feedSeeds[seedIndex % feedSeeds.size]
            seedIndex++
            val fresh = try {
                repo.search(seed).videos.filter { seenUrls.add(it.url) }
            } catch (_: Exception) { emptyList() }
            if (fresh.isNotEmpty()) return fresh
        }
        return emptyList()
    }

    fun toggleLayout() {
        viewModelScope.launch {
            val current = prefs.homeLayout.first()
            prefs.setHomeLayout(if (current == "GRID") "LIST" else "GRID")
        }
    }

    // ── Customize Home sheet setters ─────────────────────────
    fun setLayout(v: String)             = viewModelScope.launch { prefs.setHomeLayout(v) }
    fun setGridColumns(v: String)        = viewModelScope.launch { prefs.setGridColumns(v) }
    fun setCardStyle(v: String)          = viewModelScope.launch { prefs.setHomeCardStyle(v) }
    fun setShowCW(v: Boolean)            = viewModelScope.launch { prefs.setShowContinueWatching(v) }
    fun setShowFeatured(v: Boolean)      = viewModelScope.launch { prefs.setShowHeroCard(v) }

    fun toggleCategory(cat: String) {
        viewModelScope.launch {
            val current = prefs.homeCategories.first()
            val updated = if (cat in current) current - cat else current + cat
            prefs.setHomeCategories(updated)
            // If the currently selected chip was removed, fall back to All
            if (cat == _selectedCategory.value && cat !in updated) loadTrending()
        }
    }

    fun resetCategories() {
        viewModelScope.launch {
            prefs.setHomeCategories(listOf("Music", "Gaming", "Sports", "News", "Tech", "Comedy", "Film"))
        }
    }
}
