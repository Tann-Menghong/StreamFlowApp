package com.streamflow.ui.shorts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.StreamFlowApp
import com.streamflow.data.YouTubeRepository
import com.streamflow.data.friendlyError
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.model.VideoDetails
import com.streamflow.data.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

class ShortsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = YouTubeRepository()
    private val db = (app as StreamFlowApp).database
    private val prefs = (app as StreamFlowApp).prefs

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Per-url resolved stream details for the pages near the viewport
    private val _details = MutableStateFlow<Map<String, VideoDetails>>(emptyMap())
    val details: StateFlow<Map<String, VideoDetails>> = _details

    private val query = "#shorts"
    private var nextPage: Page? = null
    private var loadingMore = false
    private val loadingDetails = mutableSetOf<String>()
    private val watched = mutableSetOf<String>()

    init { loadFeed() }

    private fun List<VideoItem>.onlyShorts() =
        filter { it.duration in 1..75 && it.url.isNotEmpty() }

    fun loadFeed() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val result = repo.search(query)
                nextPage = result.nextPage
                _videos.value = result.videos.onlyShorts().distinctBy { it.url }
                if (_videos.value.isEmpty()) loadMore()
            } catch (e: Exception) {
                _error.value = friendlyError(e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadMore() {
        val page = nextPage ?: return
        if (loadingMore) return
        loadingMore = true
        viewModelScope.launch {
            try {
                // A whole page can filter down to zero shorts — keep walking pages
                // (bounded) so the feed doesn't dead-end while more results exist
                var p: Page? = page
                var hops = 0
                while (p != null && hops < 5) {
                    val result = repo.searchNextPage(query, p)
                    nextPage = result.nextPage
                    val added = result.videos.onlyShorts()
                    _videos.value = (_videos.value + added).distinctBy { it.url }
                    if (added.isNotEmpty()) break
                    p = result.nextPage
                    hops++
                }
            } catch (_: Exception) {
            } finally {
                loadingMore = false
            }
        }
    }

    // Resolve the playable stream for a page (480p is plenty for vertical video)
    fun loadDetails(url: String) {
        if (url.isEmpty() || _details.value.containsKey(url) || url in loadingDetails) return
        loadingDetails.add(url)
        viewModelScope.launch {
            try {
                val d = repo.getVideoDetails(url, "480P")
                _details.value = _details.value + (url to d)
            } catch (_: Exception) {
            } finally {
                loadingDetails.remove(url)
            }
        }
    }

    fun recordWatch(video: VideoItem) {
        if (video.url in watched) return
        watched.add(video.url)
        viewModelScope.launch {
            if (prefs.incognito.first()) return@launch
            try {
                // REPLACE would wipe a saved resume position if this URL was already
                // in history from a normal watch — carry it over (same as PlayerViewModel)
                val prevPos = try { db.historyDao().getPosition(video.url) } catch (_: Exception) { 0L }
                db.historyDao().insert(HistoryEntity(
                    url = video.url,
                    title = video.title,
                    thumbnailUrl = video.thumbnailUrl,
                    uploaderName = video.uploaderName,
                    viewCount = video.viewCount,
                    duration = video.duration,
                    position = prevPos
                ))
            } catch (_: Exception) {
            }
        }
    }
}
