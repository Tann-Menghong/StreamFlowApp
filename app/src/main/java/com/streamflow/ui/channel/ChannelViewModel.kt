package com.streamflow.ui.channel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.data.YouTubeRepository
import com.streamflow.data.local.AppDatabase
import com.streamflow.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

sealed class ChannelUiState {
    object Loading : ChannelUiState()
    data class Error(val message: String) : ChannelUiState()
    data class Ready(
        val channel: YouTubeRepository.ChannelResult,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = false
    ) : ChannelUiState()
}

class ChannelViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = YouTubeRepository()
    private val dao  = AppDatabase.get(app).subscriptionDao()

    private val _uiState = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
    val uiState: StateFlow<ChannelUiState> = _uiState

    private var currentChannelUrl = ""
    private var nextPage: Page? = null

    fun isSubscribed(url: String) = dao.isSubscribed(url)
        .map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun loadChannel(url: String) {
        currentChannelUrl = url
        nextPage = null
        viewModelScope.launch {
            _uiState.value = ChannelUiState.Loading
            try {
                val result = repo.getChannelVideos(url)
                nextPage = result.nextPage
                _uiState.value = ChannelUiState.Ready(
                    channel = result,
                    isLoadingMore = false,
                    hasMore = result.nextPage != null
                )
            } catch (e: Exception) {
                _uiState.value = ChannelUiState.Error(e.localizedMessage ?: "Failed to load channel")
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value as? ChannelUiState.Ready ?: return
        val page    = nextPage ?: return
        if (current.isLoadingMore) return
        viewModelScope.launch {
            _uiState.value = current.copy(isLoadingMore = true)
            try {
                val result = repo.getChannelNextPage(currentChannelUrl, page)
                nextPage = result.nextPage
                _uiState.value = current.copy(
                    channel = current.channel.copy(
                        videos = current.channel.videos + result.videos
                    ),
                    isLoadingMore = false,
                    hasMore = result.nextPage != null
                )
            } catch (_: Exception) {
                _uiState.value = current.copy(isLoadingMore = false)
            }
        }
    }

    fun subscribe(url: String, name: String, avatarUrl: String) {
        viewModelScope.launch {
            dao.insert(SubscriptionEntity(channelUrl = url, channelName = name, avatarUrl = avatarUrl))
        }
    }

    fun unsubscribe(url: String) {
        viewModelScope.launch { dao.delete(url) }
    }
}
