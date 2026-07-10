package com.streamflow.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.StreamFlowApp
import com.streamflow.data.YouTubeRepository
import com.streamflow.data.friendlyError
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.model.VideoItem
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

    init { loadTrending() }

    fun loadTrending() {
        isSearchMode = false
        currentQuery = ""
        _selectedCategory.value = "All"
        _activeSearchQuery.value = ""
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            nextPage = null
            try {
                val country = prefs.country.first()
                val result  = repo.getTrending(country)
                nextPage    = result.nextPage
                _uiState.value = HomeUiState.Success(result.videos, hasMore = result.nextPage != null)
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
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                if (isSearchMode) {
                    val result = repo.search(currentQuery)
                    nextPage   = result.nextPage
                    _uiState.value = HomeUiState.Success(result.videos, hasMore = result.nextPage != null)
                } else {
                    val country = prefs.country.first()
                    val result  = repo.getTrending(country)
                    nextPage    = result.nextPage
                    _uiState.value = HomeUiState.Success(result.videos, hasMore = result.nextPage != null)
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(friendlyError(e))
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value as? HomeUiState.Success ?: return
        val page    = nextPage ?: return
        if (current.isLoadingMore) return
        viewModelScope.launch {
            _uiState.value = current.copy(isLoadingMore = true)
            val result = try {
                if (isSearchMode) repo.searchNextPage(currentQuery, page)
                else              repo.getTrendingNextPage(page)
            } catch (e: Exception) {
                _uiState.value = current.copy(isLoadingMore = false)
                return@launch
            }
            nextPage = result.nextPage
            _uiState.value = current.copy(
                videos        = current.videos + result.videos,
                isLoadingMore = false,
                hasMore       = result.nextPage != null
            )
        }
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
