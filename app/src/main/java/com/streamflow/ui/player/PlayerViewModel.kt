package com.streamflow.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.StreamFlowApp
import com.streamflow.data.YouTubeRepository
import com.streamflow.data.local.entity.FavoriteEntity
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.model.VideoDetails
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

    val autoPlay: Flow<Boolean> = prefs.autoPlay

    fun loadVideo(videoUrl: String) {
        _currentUrl.value = videoUrl
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading
            if (isDirectStream(videoUrl)) {
                // Raw HLS/MP4 stream (e.g. from Donghua tab) — play directly
                val details = VideoDetails(
                    url = videoUrl,
                    title = extractTitleFromUrl(videoUrl),
                    uploaderName = extractHostFromUrl(videoUrl),
                    viewCount = 0L,
                    likeCount = 0L,
                    description = "",
                    streamUrl = videoUrl,
                    audioUrl = null,
                    thumbnailUrl = ""
                )
                _uiState.value = PlayerUiState.Ready(details)
                recordHistory(details, videoUrl)
            } else {
                // YouTube URL — use NewPipe extractor
                try {
                    val quality = prefs.quality.first()
                    val details = repo.getVideoDetails(videoUrl, quality)
                    _uiState.value = PlayerUiState.Ready(details)
                    recordHistory(details, videoUrl)
                } catch (e: Exception) {
                    _uiState.value = PlayerUiState.Error("${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
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
                    duration = 0L
                ))
            }
        }
    }

    private suspend fun recordHistory(details: VideoDetails, url: String) {
        db.historyDao().insert(HistoryEntity(
            url = url,
            title = details.title,
            thumbnailUrl = details.thumbnailUrl,
            uploaderName = details.uploaderName,
            viewCount = details.viewCount,
            duration = 0L
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
