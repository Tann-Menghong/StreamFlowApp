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

sealed class ChannelUiState {
    object Loading : ChannelUiState()
    data class Error(val message: String) : ChannelUiState()
    data class Ready(val channel: YouTubeRepository.ChannelResult) : ChannelUiState()
}

class ChannelViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = YouTubeRepository()
    private val dao  = AppDatabase.get(app).subscriptionDao()

    private val _uiState = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
    val uiState: StateFlow<ChannelUiState> = _uiState

    fun isSubscribed(url: String) = dao.isSubscribed(url)
        .map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun loadChannel(url: String) {
        viewModelScope.launch {
            _uiState.value = ChannelUiState.Loading
            try {
                _uiState.value = ChannelUiState.Ready(repo.getChannelVideos(url))
            } catch (e: Exception) {
                _uiState.value = ChannelUiState.Error(e.localizedMessage ?: "Failed to load channel")
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
