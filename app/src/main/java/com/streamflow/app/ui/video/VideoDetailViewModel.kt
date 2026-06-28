package com.streamflow.app.ui.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.app.data.db.AppDatabase
import com.streamflow.app.data.db.BookmarkEntity
import com.streamflow.app.data.db.HistoryEntity
import com.streamflow.app.data.model.PlaybackSource
import com.streamflow.app.data.model.VideoDetails
import com.streamflow.app.data.repository.YoutubeRepository
import com.streamflow.app.player.PlayerController
import com.streamflow.app.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VideoDetailViewModel(
    private val repository: YoutubeRepository,
    private val database: AppDatabase,
    private val playerController: PlayerController,
    private val videoUrl: String
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<VideoDetails>>(UiState.Loading)
    val state: StateFlow<UiState<VideoDetails>> = _state.asStateFlow()

    val isBookmarked: StateFlow<Boolean> = database.bookmarkDao().observeIsBookmarked(videoUrl)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val playerState = playerController.state

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            repository.getVideoDetails(videoUrl).fold(
                onSuccess = { details ->
                    _state.value = UiState.Success(details)
                    playDefault(details)
                    recordHistory(details)
                },
                onFailure = { _state.value = UiState.Error(it.message ?: "Failed to load video") }
            )
        }
    }

    fun selectPlaybackSource(source: PlaybackSource, title: String) {
        when (source) {
            is PlaybackSource.Muxed -> playerController.play(source.url, title, source.label)
            is PlaybackSource.AudioOnly -> playerController.play(source.url, title, source.label)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        playerController.setPlaybackSpeed(speed)
    }

    fun toggleBookmark(details: VideoDetails) {
        viewModelScope.launch {
            if (isBookmarked.value) {
                database.bookmarkDao().deleteByUrl(details.url)
            } else {
                database.bookmarkDao().insert(
                    BookmarkEntity(
                        url = details.url,
                        title = details.title,
                        thumbnailUrl = details.thumbnailUrl,
                        uploaderName = details.uploaderName,
                        durationSeconds = details.durationSeconds,
                        savedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun playDefault(details: VideoDetails) {
        val default = details.playbackOptions.firstOrNull() ?: return
        selectPlaybackSource(default, details.title)
    }

    private fun recordHistory(details: VideoDetails) {
        viewModelScope.launch {
            database.historyDao().upsert(
                HistoryEntity(
                    url = details.url,
                    title = details.title,
                    thumbnailUrl = details.thumbnailUrl,
                    uploaderName = details.uploaderName,
                    durationSeconds = details.durationSeconds,
                    watchedAt = System.currentTimeMillis()
                )
            )
        }
    }
}
