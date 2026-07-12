package com.streamflow.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.data.YouTubeRepository
import com.streamflow.data.friendlyError
import com.streamflow.data.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(
        val query: String,
        val videos: List<VideoItem>,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = false
    ) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

class SearchViewModel : ViewModel() {

    private val repo = YouTubeRepository()
    private var nextPage: Page? = null
    // Bumped per search so a slow older query can't clobber a newer one (stale-response race)
    private var searchGeneration = 0

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    fun search(query: String) {
        if (query.isBlank()) return
        val gen = ++searchGeneration
        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            nextPage = null
            try {
                val result = repo.search(query)
                if (gen != searchGeneration) return@launch // superseded by a newer query
                nextPage = result.nextPage
                _uiState.value = SearchUiState.Success(query, result.videos, hasMore = result.nextPage != null)
            } catch (e: Exception) {
                if (gen != searchGeneration) return@launch
                _uiState.value = SearchUiState.Error(friendlyError(e))
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        val page = nextPage ?: return
        if (current.isLoadingMore) return
        val gen = searchGeneration
        viewModelScope.launch {
            _uiState.value = current.copy(isLoadingMore = true)
            try {
                val result = repo.searchNextPage(current.query, page)
                if (gen != searchGeneration) return@launch // a new search replaced this one
                nextPage = result.nextPage
                // Dedupe at append time — NewPipe pagination can repeat a video
                // across pages, and the LazyColumn's key = { it.url } crashes on
                // a duplicate key if that happens unguarded.
                val existing = current.videos.mapTo(HashSet()) { it.url }
                _uiState.value = current.copy(
                    videos        = current.videos + result.videos.filter { it.url !in existing },
                    isLoadingMore = false,
                    hasMore       = result.nextPage != null
                )
            } catch (e: Exception) {
                _uiState.value = current.copy(isLoadingMore = false)
            }
        }
    }
}
