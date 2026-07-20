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

// Client-side result filters (YouTube's search API doesn't expose these via
// NewPipe, so they're applied to the fetched results)
enum class DurationFilter(val label: String) {
    ANY("Any length"), SHORT("Under 4 min"), MEDIUM("4–20 min"), LONG("Over 20 min");
    fun matches(seconds: Long): Boolean = when (this) {
        ANY -> true
        SHORT -> seconds in 1 until 240
        MEDIUM -> seconds in 240..1200
        LONG -> seconds > 1200
    }
}

enum class DateFilter(val label: String) {
    ANY("Any time"), DAY("Today"), WEEK("This week"), MONTH("This month");
    fun matches(uploadedEpoch: Long): Boolean {
        if (this == ANY) return true
        if (uploadedEpoch <= 0L) return false // unknown date can't satisfy a date filter
        val age = System.currentTimeMillis() - uploadedEpoch
        return when (this) {
            DAY -> age <= 24L * 3600_000
            WEEK -> age <= 7L * 24 * 3600_000
            else -> age <= 31L * 24 * 3600_000
        }
    }
}

class SearchViewModel : ViewModel() {

    private val repo = YouTubeRepository()
    private var nextPage: Page? = null
    // Bumped per search so a slow older query can't clobber a newer one (stale-response race)
    private var searchGeneration = 0

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    // Filters live outside the Success state so pagination keeps the RAW list
    // and filtering stays a pure view concern (changing a filter never refetches)
    val durationFilter = MutableStateFlow(DurationFilter.ANY)
    val dateFilter = MutableStateFlow(DateFilter.ANY)

    // Last submitted query, so Retry works even after the text field was cleared
    private var lastQuery = ""

    fun retry() { if (lastQuery.isNotBlank()) search(lastQuery) }

    fun search(query: String) {
        if (query.isBlank()) return
        lastQuery = query
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

    // Synchronous re-entrancy guard: the scroll trigger can fire again before the
    // coroutine flips isLoadingMore, which let two identical page fetches run at
    // once (double network for the same page).
    private var loadingMorePage = false

    fun loadMore() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        val page = nextPage ?: return
        if (current.isLoadingMore || loadingMorePage) return
        loadingMorePage = true
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
                // Guard the failure path too: writing the captured `current` here
                // after a new search started would resurrect the old query's results
                if (gen != searchGeneration) return@launch
                _uiState.value = current.copy(isLoadingMore = false)
            } finally {
                // Always release — including the stale-generation early return —
                // or a new search mid-load would pin it and block pagination.
                loadingMorePage = false
            }
        }
    }
}
