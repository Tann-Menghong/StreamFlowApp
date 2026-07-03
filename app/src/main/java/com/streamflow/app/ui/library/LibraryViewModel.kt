package com.streamflow.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.app.data.db.AppDatabase
import com.streamflow.app.data.db.BookmarkEntity
import com.streamflow.app.data.db.HistoryEntity
import com.streamflow.app.data.db.SubscriptionEntity
import com.streamflow.app.data.model.VideoItem
import com.streamflow.app.data.repository.YoutubeRepository
import com.streamflow.app.ui.components.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val database: AppDatabase,
    private val repository: YoutubeRepository
) : ViewModel() {

    val history: StateFlow<List<VideoItem>> = database.historyDao().observeAll()
        .map { entities -> entities.map { it.toVideoItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<VideoItem>> = database.bookmarkDao().observeAll()
        .map { entities -> entities.map { it.toVideoItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriptions: StateFlow<List<SubscriptionEntity>> = database.subscriptionDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _feedState = MutableStateFlow<UiState<List<VideoItem>>>(UiState.Loading)
    val feedState: StateFlow<UiState<List<VideoItem>>> = _feedState.asStateFlow()

    fun clearHistory() {
        viewModelScope.launch { database.historyDao().clear() }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch { database.bookmarkDao().deleteByUrl(url) }
    }

    fun unsubscribe(channelUrl: String) {
        viewModelScope.launch { database.subscriptionDao().deleteByUrl(channelUrl) }
    }

    fun loadFeed() {
        _feedState.value = UiState.Loading
        viewModelScope.launch {
            val subs = database.subscriptionDao().observeAll().first()
            if (subs.isEmpty()) {
                _feedState.value = UiState.Success(emptyList())
                return@launch
            }

            val perChannelVideos = subs.map { sub ->
                async { repository.getChannelDetails(sub.channelUrl).getOrNull()?.videos.orEmpty() }
            }.awaitAll()

            _feedState.value = UiState.Success(interleaveNewestFirst(perChannelVideos))
        }
    }

    /** Channels each return their own videos newest-first; round-robin across channels approximates a chronological feed without relying on cross-channel timestamp comparison. */
    private fun interleaveNewestFirst(perChannelVideos: List<List<VideoItem>>): List<VideoItem> {
        val maxSize = perChannelVideos.maxOfOrNull { it.size } ?: 0
        val merged = mutableListOf<VideoItem>()
        for (index in 0 until maxSize) {
            for (videos in perChannelVideos) {
                videos.getOrNull(index)?.let { merged += it }
            }
        }
        return merged.distinctBy { it.url }
    }

    private fun HistoryEntity.toVideoItem() = VideoItem(
        url = url,
        title = title,
        thumbnailUrl = thumbnailUrl,
        uploaderName = uploaderName,
        uploaderUrl = null,
        uploaderAvatarUrl = null,
        durationSeconds = durationSeconds,
        viewCount = -1,
        textualUploadDate = null,
        isShort = false
    )

    private fun BookmarkEntity.toVideoItem() = VideoItem(
        url = url,
        title = title,
        thumbnailUrl = thumbnailUrl,
        uploaderName = uploaderName,
        uploaderUrl = null,
        uploaderAvatarUrl = null,
        durationSeconds = durationSeconds,
        viewCount = -1,
        textualUploadDate = null,
        isShort = false
    )
}
