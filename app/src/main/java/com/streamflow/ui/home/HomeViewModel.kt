package com.streamflow.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.StreamFlowApp
import com.streamflow.data.YouTubeRepository
import com.streamflow.data.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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

    private val repo = YouTubeRepository()
    private val prefs = (app as StreamFlowApp).prefs
    private var nextPage: Page? = null

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    val homeLayout = prefs.homeLayout.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "LIST")

    fun toggleLayout() {
        viewModelScope.launch {
            val current = prefs.homeLayout.first()
            prefs.setHomeLayout(if (current == "GRID") "LIST" else "GRID")
        }
    }

    init { loadTrending() }

    fun loadTrending() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            nextPage = null
            try {
                val country = prefs.country.first()
                val result = repo.getTrending(country)
                nextPage = result.nextPage
                _uiState.value = HomeUiState.Success(result.videos, hasMore = result.nextPage != null)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error("${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value as? HomeUiState.Success ?: return
        val page = nextPage ?: return
        if (current.isLoadingMore) return
        viewModelScope.launch {
            _uiState.value = current.copy(isLoadingMore = true)
            val result = repo.getTrendingNextPage(page)
            nextPage = result.nextPage
            _uiState.value = current.copy(
                videos = current.videos + result.videos,
                isLoadingMore = false,
                hasMore = result.nextPage != null
            )
        }
    }
}
