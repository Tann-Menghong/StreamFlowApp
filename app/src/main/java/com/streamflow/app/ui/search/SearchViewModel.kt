package com.streamflow.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.app.data.model.VideoItem
import com.streamflow.app.data.repository.YoutubeRepository
import com.streamflow.app.ui.components.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val repository: YoutubeRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<UiState<List<VideoItem>>>(UiState.Success(emptyList()))
    val state: StateFlow<UiState<List<VideoItem>>> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            _state.value = UiState.Success(emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(400) // debounce
            runSearch(newQuery)
        }
    }

    fun retry() {
        runSearch(_query.value)
    }

    private fun runSearch(searchQuery: String) {
        if (searchQuery.isBlank()) return
        _state.value = UiState.Loading
        viewModelScope.launch {
            repository.search(searchQuery).fold(
                onSuccess = { _state.value = UiState.Success(it) },
                onFailure = { _state.value = UiState.Error(it.message ?: "Search failed") }
            )
        }
    }
}
