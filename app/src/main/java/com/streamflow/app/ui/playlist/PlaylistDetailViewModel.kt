package com.streamflow.app.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.app.data.model.PlaylistDetails
import com.streamflow.app.data.repository.YoutubeRepository
import com.streamflow.app.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(
    private val repository: YoutubeRepository,
    private val playlistUrl: String
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<PlaylistDetails>>(UiState.Loading)
    val state: StateFlow<UiState<PlaylistDetails>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            repository.getPlaylistDetails(playlistUrl).fold(
                onSuccess = { _state.value = UiState.Success(it) },
                onFailure = { _state.value = UiState.Error(it.message ?: "Failed to load playlist") }
            )
        }
    }
}
