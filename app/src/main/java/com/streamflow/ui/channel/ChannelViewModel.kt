package com.streamflow.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.data.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ChannelUiState {
    object Loading : ChannelUiState()
    data class Error(val message: String) : ChannelUiState()
    data class Ready(val channel: YouTubeRepository.ChannelResult) : ChannelUiState()
}

class ChannelViewModel : ViewModel() {
    private val repo = YouTubeRepository()

    private val _uiState = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
    val uiState: StateFlow<ChannelUiState> = _uiState

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
}
