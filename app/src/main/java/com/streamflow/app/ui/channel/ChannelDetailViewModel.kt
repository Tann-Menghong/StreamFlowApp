package com.streamflow.app.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.app.data.db.AppDatabase
import com.streamflow.app.data.db.SubscriptionEntity
import com.streamflow.app.data.model.ChannelDetails
import com.streamflow.app.data.repository.YoutubeRepository
import com.streamflow.app.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChannelDetailViewModel(
    private val repository: YoutubeRepository,
    private val database: AppDatabase,
    private val channelUrl: String
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ChannelDetails>>(UiState.Loading)
    val state: StateFlow<UiState<ChannelDetails>> = _state.asStateFlow()

    val isSubscribed: StateFlow<Boolean> = database.subscriptionDao().observeIsSubscribed(channelUrl)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    fun toggleSubscription(channel: ChannelDetails) {
        viewModelScope.launch {
            if (isSubscribed.value) {
                database.subscriptionDao().deleteByUrl(channel.url)
            } else {
                database.subscriptionDao().insert(
                    SubscriptionEntity(
                        channelUrl = channel.url,
                        name = channel.name,
                        avatarUrl = channel.avatarUrl,
                        subscribedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
