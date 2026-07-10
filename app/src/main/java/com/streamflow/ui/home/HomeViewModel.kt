package com.streamflow.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.StreamFlowApp
import com.streamflow.data.YouTubeRepository
import com.streamflow.data.friendlyError
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
    val uiState: StateFlow<HomeUiState> = _uiState

    // Persisted layout/display prefs
    val homeLayout           = prefs.homeLayout.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "LIST")
    val showContinueWatching = prefs.showContinueWatching.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showHeroCard         = prefs.showHeroCard.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val gridColumns          = prefs.gridColumns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "2")
    val cardStyle            = prefs.homeCardStyle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "COMFORT")

    // Full pool of pickable category chips; user selects which ones show on Home
    val categoryPool = listOf(
        "Music", "Gaming", "Sports", "News", "Tech", "Comedy", "Film",
        "Podcasts", "Cooking", "Travel", "Fitness", "Education", "Beauty",
        "Cars", "Animals", "Anime", "Science", "Fashion", "Khmer News", "K-Pop"
    )

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
        val evergreen = listOf(
            "popular this week", "new music video", "viral videos",
            "highlights today", "best of 2026", "documentary"
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

    init { loadTrending() }

    fun loadTrending() {
        isSearchMode = false
        currentQuery = ""
        _selectedCategory.value = "All"
        _activeSearchQuery.value = ""
        feedGeneration++
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            nextPage = null
            try {
                val country = prefs.country.first()
                feedSeeds = buildSeeds()
                seedIndex = 0
                seenUrls.clear()

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
                val trendingVideos = trendingRes?.videos?.shuffled() ?: emptyList()
                if (trendingVideos.isEmpty() && seedChunk.isEmpty()) {
                    throw (trendingErr ?: Exception("No videos found"))
                }
                nextPage = trendingRes?.nextPage
                val mixed = interleave(trendingVideos, seedChunk).filter { seenUrls.add(it.url) }
                _uiState.value = HomeUiState.Success(mixed, hasMore = true)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(friendlyError(e))
            }
        }
    }

    // Called by the search bar when user submits a text query
    fun search(query: String) {
        if (query.isBlank()) { loadTrending(); return }
        isSearchMode = true
        currentQuery = query
        _selectedCategory.value = "All"
        _activeSearchQuery.value = query
        feedGeneration++
        viewModelScope.launch {
            prefs.addRecentSearch(query)
            _uiState.value = HomeUiState.Loading
            nextPage = null
            try {
                val result = repo.search(query)
                nextPage   = result.nextPage
                _uiState.value = HomeUiState.Success(result.videos, hasMore = result.nextPage != null)
            } catch (e: Exception) {
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
            isSearchMode = true
            currentQuery = cat
            feedGeneration++
            viewModelScope.launch {
                _uiState.value = HomeUiState.Loading
                nextPage = null
                try {
                    val result = repo.search(cat)
                    nextPage   = result.nextPage
                    _uiState.value = HomeUiState.Success(result.videos, hasMore = result.nextPage != null)
                } catch (e: Exception) {
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
        feedGeneration++
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val result = repo.search(currentQuery)
                nextPage   = result.nextPage
                _uiState.value = HomeUiState.Success(result.videos, hasMore = result.nextPage != null)
            } catch (e: Exception) {
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
            _uiState.value = base.copy(
                videos        = base.videos + fresh.filter { it.url !in existing },
                isLoadingMore = false,
                hasMore       = if (isSearchMode) nextPage != null else fresh.isNotEmpty()
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
            nextPage = r?.nextPage
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
