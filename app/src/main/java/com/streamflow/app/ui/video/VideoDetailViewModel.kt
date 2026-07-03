package com.streamflow.app.ui.video

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.app.data.db.AppDatabase
import com.streamflow.app.data.db.BookmarkEntity
import com.streamflow.app.data.db.HistoryEntity
import com.streamflow.app.data.db.WatchLaterEntity
import com.streamflow.app.data.model.CommentItem
import com.streamflow.app.data.model.DislikeInfo
import com.streamflow.app.data.model.PlaybackSource
import com.streamflow.app.data.model.SponsorSegment
import com.streamflow.app.data.model.VideoDetails
import com.streamflow.app.data.repository.YoutubeRepository
import com.streamflow.app.player.PlayerController
import com.streamflow.app.ui.components.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoDetailViewModel(
    private val repository: YoutubeRepository,
    private val database: AppDatabase,
    private val playerController: PlayerController,
    private val prefs: SharedPreferences,
    private val videoUrl: String
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<VideoDetails>>(UiState.Loading)
    val state: StateFlow<UiState<VideoDetails>> = _state.asStateFlow()

    val isBookmarked: StateFlow<Boolean> = database.bookmarkDao().observeIsBookmarked(videoUrl)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isInWatchLater: StateFlow<Boolean> = database.watchLaterDao().observeIsInWatchLater(videoUrl)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val playerState = playerController.state

    private val _commentsState = MutableStateFlow<UiState<List<CommentItem>>>(UiState.Success(emptyList()))
    val commentsState: StateFlow<UiState<List<CommentItem>>> = _commentsState.asStateFlow()

    private val _showComments = MutableStateFlow(false)
    val showComments: StateFlow<Boolean> = _showComments.asStateFlow()

    private val _dislikeInfo = MutableStateFlow<DislikeInfo?>(null)
    val dislikeInfo: StateFlow<DislikeInfo?> = _dislikeInfo.asStateFlow()

    private val _sleepTimerRemaining = MutableStateFlow(0)
    val sleepTimerRemaining: StateFlow<Int> = _sleepTimerRemaining.asStateFlow()
    private var sleepTimerJob: Job? = null

    private val _sponsorSegments = MutableStateFlow<List<SponsorSegment>>(emptyList())
    private val skippedSegmentEnds = mutableSetOf<Long>()

    init {
        load()
        viewModelScope.launch {
            playerState.collect { state ->
                autoSkipSponsorSegment(state.positionMs)
            }
        }
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            val savedPosition = database.historyDao().getSavedPosition(videoUrl) ?: 0L
            repository.getVideoDetails(videoUrl).fold(
                onSuccess = { details ->
                    _state.value = UiState.Success(details)
                    playDefault(details, savedPosition)
                    recordHistory(details)
                    loadDislikes(videoUrl)
                    loadSponsorSegments(videoUrl)
                },
                onFailure = { _state.value = UiState.Error(it.message ?: "Failed to load video") }
            )
        }
    }

    fun selectPlaybackSource(source: PlaybackSource, title: String) {
        val current = (state.value as? UiState.Success)?.data ?: return
        if (source is PlaybackSource.Muxed) {
            prefs.edit().putString("preferred_quality", source.label).apply()
        }
        when (source) {
            is PlaybackSource.Muxed ->
                playerController.play(source.url, title, source.label, current.url, current.thumbnailUrl)
            is PlaybackSource.AudioOnly ->
                playerController.play(source.url, title, source.label, current.url, current.thumbnailUrl)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        playerController.setPlaybackSpeed(speed)
    }

    fun seekBy(deltaMs: Long) {
        playerController.seekBy(deltaMs)
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun loadComments() {
        _showComments.value = true
        if (_commentsState.value is UiState.Loading) return
        _commentsState.value = UiState.Loading
        viewModelScope.launch {
            repository.getComments(videoUrl).fold(
                onSuccess = { _commentsState.value = UiState.Success(it) },
                onFailure = { _commentsState.value = UiState.Error(it.message ?: "Failed to load comments") }
            )
        }
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

    fun toggleWatchLater(details: VideoDetails) {
        viewModelScope.launch {
            if (isInWatchLater.value) {
                database.watchLaterDao().deleteByUrl(details.url)
            } else {
                database.watchLaterDao().insert(
                    WatchLaterEntity(
                        url = details.url,
                        title = details.title,
                        thumbnailUrl = details.thumbnailUrl,
                        uploaderName = details.uploaderName,
                        durationSeconds = details.durationSeconds,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun addToQueue(details: VideoDetails) {
        val preferred = prefs.getString("preferred_quality", null)
        val source = details.playbackOptions.filterIsInstance<PlaybackSource.Muxed>()
            .firstOrNull { it.label == preferred }
            ?: details.playbackOptions.firstOrNull()
            ?: return
        when (source) {
            is PlaybackSource.Muxed ->
                playerController.addToQueue(source.url, details.title, source.label, details.url, details.thumbnailUrl)
            is PlaybackSource.AudioOnly ->
                playerController.addToQueue(source.url, details.title, source.label, details.url, details.thumbnailUrl)
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerRemaining.value = minutes
        if (minutes <= 0) return
        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes
            while (remaining > 0) {
                delay(60_000L)
                remaining--
                _sleepTimerRemaining.value = remaining
            }
            playerController.stop()
        }
    }

    private fun autoSkipSponsorSegment(positionMs: Long) {
        if (positionMs <= 0L) return
        val segment = _sponsorSegments.value.firstOrNull { seg ->
            positionMs >= seg.startMs && positionMs < seg.endMs && seg.endMs !in skippedSegmentEnds
        } ?: return
        skippedSegmentEnds.add(segment.endMs)
        playerController.seekTo(segment.endMs)
    }

    private fun loadDislikes(videoUrl: String) {
        val videoId = repository.extractVideoId(videoUrl) ?: return
        viewModelScope.launch {
            repository.getDislikes(videoId).onSuccess { _dislikeInfo.value = it }
        }
    }

    private fun loadSponsorSegments(videoUrl: String) {
        val videoId = repository.extractVideoId(videoUrl) ?: return
        viewModelScope.launch {
            repository.getSponsorSegments(videoId).onSuccess { _sponsorSegments.value = it }
        }
    }

    private fun playDefault(details: VideoDetails, resumePositionMs: Long = 0L) {
        val savedQuality = prefs.getString("preferred_quality", null)
        val preferred = if (savedQuality != null) {
            details.playbackOptions.filterIsInstance<PlaybackSource.Muxed>()
                .firstOrNull { it.label == savedQuality }
        } else null
        val source = preferred ?: details.playbackOptions.firstOrNull() ?: return
        when (source) {
            is PlaybackSource.Muxed ->
                playerController.play(source.url, details.title, source.label, details.url, details.thumbnailUrl)
            is PlaybackSource.AudioOnly ->
                playerController.play(source.url, details.title, source.label, details.url, details.thumbnailUrl)
        }
        if (resumePositionMs > 5000L) {
            viewModelScope.launch {
                delay(1500L)
                playerController.seekTo(resumePositionMs)
            }
        }
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
                    watchedAt = System.currentTimeMillis(),
                    watchedPositionMs = 0L
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        sleepTimerJob?.cancel()
        val currentUrl = videoUrl
        val posMs = playerController.state.value.positionMs
        if (posMs > 5000L) {
            viewModelScope.launch(NonCancellable + Dispatchers.IO) {
                database.historyDao().updatePosition(currentUrl, posMs)
            }
        }
    }
}
