package com.streamflow.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.StreamFlowApp
import com.streamflow.data.YouTubeRepository
import com.streamflow.data.friendlyError
import com.streamflow.data.model.VideoItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class FeedUiState {
    object Loading : FeedUiState()
    object NoSubscriptions : FeedUiState()
    data class Success(val videos: List<VideoItem>) : FeedUiState()
    data class Error(val message: String) : FeedUiState()
}

class FeedViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = YouTubeRepository()
    private val db = (app as StreamFlowApp).database

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState

    private var loaded = false

    fun load(force: Boolean = false) {
        if (loaded && !force && _uiState.value is FeedUiState.Success) return
        viewModelScope.launch {
            _uiState.value = FeedUiState.Loading
            try {
                val subs = db.subscriptionDao().getAll().first()
                if (subs.isEmpty()) {
                    _uiState.value = FeedUiState.NoSubscriptions
                    return@launch
                }
                // Fetch each channel's latest uploads in parallel (cap channels + per-channel count)
                val videos = coroutineScope {
                    subs.take(12).map { sub ->
                        async {
                            try { repo.getChannelInfo(sub.channelUrl).videos.take(5) }
                            catch (_: Exception) { emptyList() }
                        }
                    }.awaitAll().flatten()
                }
                if (videos.isEmpty()) {
                    _uiState.value = FeedUiState.Error("Couldn't load any videos from your channels.")
                    return@launch
                }
                // Newest first; videos with unknown dates go last (keep channel order there)
                val sorted = videos.sortedByDescending { it.uploadedEpoch }
                _uiState.value = FeedUiState.Success(sorted)
                loaded = true
            } catch (e: Exception) {
                _uiState.value = FeedUiState.Error(friendlyError(e))
            }
        }
    }
}
