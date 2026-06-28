package com.streamflow.app.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.app.data.model.ChannelDetails
import com.streamflow.app.data.repository.YoutubeRepository
import com.streamflow.app.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChannelDetailViewModel(
    private val repository: YoutubeRepository,
    private val channelUrl: String
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ChannelDetails>>(UiState.Loading)
    val state: StateFlow<UiState<ChannelDetails>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            repository.getChannelDetails(channelUrl).fold(
                onSuccess = { _state.value = UiState.Success(it) },
                onFailure = { _state.value = UiState.Error(it.message ?: "Failed to load channel") }
            )
        }
    }
}
