package com.streamflow.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.data.YouTubeRepository
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

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            nextPage = null
            val result = repo.search(query)
            nextPage = result.nextPage
            _uiState.value = if (result.videos.isEmpty())
                SearchUiState.Error("No results for \"$query\".")
            else
                SearchUiState.Success(query, result.videos, hasMore = result.nextPage != null)
        }
    }

    fun loadMore() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        val page = nextPage ?: return
        if (current.isLoadingMore) return
        viewModelScope.launch {
            _uiState.value = current.copy(isLoadingMore = true)
            val result = repo.searchNextPage(current.query, page)
            nextPage = result.nextPage
            _uiState.value = current.copy(
                videos = current.videos + result.videos,
                isLoadingMore = false,
                hasMore = result.nextPage != null
            )
        }
    }
}
