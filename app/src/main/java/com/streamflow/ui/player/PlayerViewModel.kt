package com.streamflow.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.StreamFlowApp
import com.streamflow.data.PlaybackQueue
import com.streamflow.data.YouTubeRepository
import com.streamflow.data.friendlyError
import com.streamflow.data.local.entity.FavoriteEntity
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.local.entity.SubscriptionEntity
import com.streamflow.data.local.entity.WatchLaterEntity
import com.streamflow.data.model.Comment
import com.streamflow.data.model.SponsorSegment
import com.streamflow.data.model.VideoDetails
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class PlayerUiState {
    object Loading : PlayerUiState()
    data class Ready(val details: VideoDetails) : PlayerUiState()
    data class Error(val message: String) : PlayerUiState()
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = YouTubeRepository()
    private val db = (app as StreamFlowApp).database
    private val prefs = (app as StreamFlowApp).prefs

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState

    private val _currentUrl = MutableStateFlow("")
    val isFavorite: Flow<Boolean> = _currentUrl
        .flatMapLatest { url -> if (url.isEmpty()) flowOf(false) else db.favoriteDao().isFavorite(url) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isInWatchLater: Flow<Boolean> = _currentUrl
        .flatMapLatest { url -> if (url.isEmpty()) flowOf(false) else db.watchLaterDao().isInWatchLater(url) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoPlay: Flow<Boolean> = prefs.autoPlay

    // ── Subscription (keyed on the current video's channel) ───────────────────
    val isSubscribed: StateFlow<Boolean> = _uiState
        .flatMapLatest { s ->
            val url = (s as? PlayerUiState.Ready)?.details?.uploaderUrl ?: ""
            if (url.isEmpty()) flowOf(false) else db.subscriptionDao().isSubscribed(url)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleSubscribe() {
        val d = (_uiState.value as? PlayerUiState.Ready)?.details ?: return
        if (d.uploaderUrl.isEmpty()) return
        viewModelScope.launch {
            val dao = db.subscriptionDao()
            if (dao.isSubscribed(d.uploaderUrl).first()) {
                dao.delete(d.uploaderUrl)
            } else {
                dao.insert(SubscriptionEntity(channelUrl = d.uploaderUrl, name = d.uploaderName, avatarUrl = d.uploaderAvatarUrl))
            }
        }
    }

    // Persist the user's in-player quality pick as the default for future videos
    fun rememberQuality(height: Int?) {
        val pref = when {
            height == null -> "AUTO"
            height >= 1080 -> "1080P"
            height >= 720  -> "720P"
            height >= 480  -> "480P"
            else           -> "360P"
        }
        viewModelScope.launch { prefs.setQuality(pref) }
    }

    // ── Comments ──────────────────────────────────────────────────────────────
    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments

    private val _commentsLoading = MutableStateFlow(false)
    val commentsLoading: StateFlow<Boolean> = _commentsLoading

    private var commentsLoadedFor = ""

    // ── SponsorBlock ──────────────────────────────────────────────────────────
    private val _sponsorSegments = MutableStateFlow<List<SponsorSegment>>(emptyList())
    val sponsorSegments: StateFlow<List<SponsorSegment>> = _sponsorSegments

    // ── Audio-only ────────────────────────────────────────────────────────────
    private val _audioOnly = MutableStateFlow(false)
    val audioOnly: StateFlow<Boolean> = _audioOnly

    // ── Playback queue ────────────────────────────────────────────────────────
    val queue = PlaybackQueue.queue

    fun loadVideo(videoUrl: String) {
        _currentUrl.value = videoUrl
        _comments.value = emptyList()
        commentsLoadedFor = ""
        _sponsorSegments.value = emptyList()
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading
            if (isDirectStream(videoUrl)) {
                val details = VideoDetails(
                    url = videoUrl,
                    title = extractTitleFromUrl(videoUrl),
                    uploaderName = extractHostFromUrl(videoUrl),
                    viewCount = 0L,
                    likeCount = 0L,
                    duration = 0L,
                    description = "",
                    streamUrl = videoUrl,
                    audioUrl = null,
                    thumbnailUrl = ""
                )
                _uiState.value = PlayerUiState.Ready(details)
                recordHistory(details, videoUrl)
            } else {
                try {
                    val quality = prefs.quality.first()
                    _autoQuality.value = quality == "AUTO"
                    val details = repo.getVideoDetails(videoUrl, quality)
                    _uiState.value = PlayerUiState.Ready(details)
                    recordHistory(details, videoUrl)
                    prefetchNext(details, quality)
                } catch (e: Exception) {
                    _uiState.value = PlayerUiState.Error(friendlyError(e))
                }
            }
        }
    }

    // Warm the details cache for the most likely next video (queue head, else first
    // related) so tapping next / autoplay starts instantly. Delayed so it never
    // competes with the current video's startup.
    private fun prefetchNext(details: com.streamflow.data.model.VideoDetails, quality: String) {
        val currentUrl = details.url
        viewModelScope.launch {
            delay(2_000L)
            if ((_uiState.value as? PlayerUiState.Ready)?.details?.url != currentUrl) return@launch
            // Warm up to 2 likely-next videos, one at a time so playback bandwidth wins
            val candidates = buildList {
                PlaybackQueue.queue.value.firstOrNull()?.url?.let { add(it) }
                details.relatedVideos.take(2).forEach { add(it.url) }
            }.distinct().filter { it != currentUrl }.take(2)
            for (url in candidates) {
                if ((_uiState.value as? PlayerUiState.Ready)?.details?.url != currentUrl) return@launch
                try { repo.getVideoDetails(url, quality) } catch (_: Exception) {}
            }
        }
    }

    // ── Quality mode (auto vs manual pick) ────────────────────────────────────
    private val _autoQuality = MutableStateFlow(true)
    val autoQuality: StateFlow<Boolean> = _autoQuality

    // Reload the same video at a specific resolution (null = Auto, best available).
    fun changeQuality(videoUrl: String, height: Int?) {
        val current = _uiState.value as? PlayerUiState.Ready ?: return
        _autoQuality.value = height == null
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading
            try {
                val details = repo.getVideoDetails(videoUrl, "AUTO", maxHeightOverride = height)
                _uiState.value = PlayerUiState.Ready(details)
            } catch (_: Exception) {
                // Revert to the working stream rather than showing an error
                _uiState.value = PlayerUiState.Ready(current.details)
            }
        }
    }

    fun loadComments(videoUrl: String) {
        if (commentsLoadedFor == videoUrl) return
        commentsLoadedFor = videoUrl
        viewModelScope.launch {
            _commentsLoading.value = true
            try {
                _comments.value = repo.getComments(videoUrl)
            } catch (_: Exception) {
                _comments.value = emptyList()
            } finally {
                _commentsLoading.value = false
            }
        }
    }

    fun loadSponsorSegments(videoUrl: String) {
        viewModelScope.launch {
            try {
                _sponsorSegments.value = repo.getSponsorSegments(videoUrl)
            } catch (_: Exception) {
                _sponsorSegments.value = emptyList()
            }
        }
    }

    fun toggleAudioOnly() {
        _audioOnly.value = !_audioOnly.value
    }

    fun toggleFavorite() {
        val state = _uiState.value as? PlayerUiState.Ready ?: return
        val details = state.details
        viewModelScope.launch {
            val fav = db.favoriteDao()
            val currently = fav.isFavorite(details.url).first()
            if (currently) {
                fav.delete(details.url)
            } else {
                fav.insert(FavoriteEntity(
                    url = details.url,
                    title = details.title,
                    thumbnailUrl = details.thumbnailUrl,
                    uploaderName = details.uploaderName,
                    viewCount = details.viewCount,
                    duration = details.duration
                ))
            }
        }
    }

    fun toggleWatchLater() {
        val state = _uiState.value as? PlayerUiState.Ready ?: return
        val details = state.details
        viewModelScope.launch {
            val wl = db.watchLaterDao()
            val currently = wl.isInWatchLater(details.url).first()
            if (currently) {
                wl.delete(details.url)
            } else {
                wl.insert(WatchLaterEntity(
                    url = details.url,
                    title = details.title,
                    thumbnailUrl = details.thumbnailUrl,
                    uploaderName = details.uploaderName,
                    viewCount = details.viewCount,
                    duration = details.duration
                ))
            }
        }
    }

    fun savePosition(url: String, positionMs: Long) {
        viewModelScope.launch {
            db.historyDao().updatePosition(url, positionMs)
        }
    }

    suspend fun getSavedPosition(url: String): Long {
        return try {
            db.historyDao().getPosition(url)
        } catch (e: Exception) {
            0L
        }
    }

    private suspend fun recordHistory(details: VideoDetails, url: String) {
        db.historyDao().insert(HistoryEntity(
            url = url,
            title = details.title,
            thumbnailUrl = details.thumbnailUrl,
            uploaderName = details.uploaderName,
            viewCount = details.viewCount,
            duration = details.duration
        ))
    }

    private fun isDirectStream(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") || lower.contains(".mp4") ||
               lower.contains(".webm") || lower.contains("/hls/") ||
               lower.contains("/stream/")
    }

    private fun extractTitleFromUrl(url: String): String {
        return try {
            val path = url.substringAfterLast("/").substringBefore("?")
                .replace("-", " ").replace("_", " ")
                .substringBeforeLast(".")
                .replaceFirstChar { it.uppercase() }
            path.ifBlank { "Video" }
        } catch (e: Exception) { "Video" }
    }

    private fun extractHostFromUrl(url: String): String {
        return try {
            val host = url.removePrefix("https://").removePrefix("http://")
                .substringBefore("/")
            host.ifBlank { "Unknown" }
        } catch (e: Exception) { "Unknown" }
    }
}
