package com.streamflow.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.app.data.db.AppDatabase
import com.streamflow.app.data.db.SearchHistoryEntity
import com.streamflow.app.data.model.SearchResultItem
import com.streamflow.app.data.repository.YoutubeRepository
import com.streamflow.app.ui.components.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: YoutubeRepository,
    private val database: AppDatabase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<UiState<List<SearchResultItem>>>(UiState.Success(emptyList()))
    val state: StateFlow<UiState<List<SearchResultItem>>> = _state.asStateFlow()

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = database.searchHistoryDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            _state.value = UiState.Success(emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            runSearch(newQuery)
        }
    }

    fun retry() {
        runSearch(_query.value)
    }

    fun deleteHistoryItem(query: String) {
        viewModelScope.launch { database.searchHistoryDao().delete(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { database.searchHistoryDao().clear() }
    }

    private fun runSearch(searchQuery: String) {
        if (searchQuery.isBlank()) return
        _state.value = UiState.Loading
        viewModelScope.launch {
            repository.search(searchQuery).fold(
                onSuccess = { results ->
                    _state.value = UiState.Success(results)
                    database.searchHistoryDao().insert(
                        SearchHistoryEntity(
                            query = searchQuery.trim(),
                            searchedAt = System.currentTimeMillis()
                        )
                    )
                },
                onFailure = { _state.value = UiState.Error(it.message ?: "Search failed") }
            )
        }
    }
}
