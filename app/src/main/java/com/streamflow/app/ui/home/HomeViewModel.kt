package com.streamflow.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.app.data.model.SearchResultItem
import com.streamflow.app.data.model.VideoItem
import com.streamflow.app.data.repository.YoutubeRepository
import com.streamflow.app.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: YoutubeRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<VideoItem>>>(UiState.Loading)
    val state: StateFlow<UiState<List<VideoItem>>> = _state.asStateFlow()

    private val _selectedCategory = MutableStateFlow(CATEGORIES[0])
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    init {
        load()
    }

    fun selectCategory(category: String) {
        if (_selectedCategory.value == category) return
        _selectedCategory.value = category
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        val category = _selectedCategory.value
        viewModelScope.launch {
            if (category == CATEGORIES[0]) {
                repository.getTrending().fold(
                    onSuccess = { _state.value = UiState.Success(it) },
                    onFailure = { _state.value = UiState.Error(it.message ?: "Failed to load trending videos") }
                )
            } else {
                val query = CATEGORY_QUERIES[category] ?: category.lowercase()
                repository.search(query).fold(
                    onSuccess = { results ->
                        val videos = results.filterIsInstance<SearchResultItem.Video>().map { it.video }
                        _state.value = UiState.Success(videos)
                    },
                    onFailure = { _state.value = UiState.Error(it.message ?: "Failed to load $category") }
                )
            }
        }
    }

    companion object {
        val CATEGORIES = listOf("Trending", "Music", "Gaming", "News", "Sports", "Tech")
        private val CATEGORY_QUERIES = mapOf(
            "Music" to "music",
            "Gaming" to "gaming",
            "News" to "news",
            "Sports" to "sports",
            "Tech" to "technology"
        )
    }
}
