package com.streamflow.ui.donghua

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.StreamFlowApp
import com.streamflow.data.DonghuaCatalog
import com.streamflow.data.ExtractionError
import com.streamflow.data.YouTubeRepository
import com.streamflow.data.classifyExtractionError
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.model.VideoItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class DonghuaUiState {
    object Loading : DonghuaUiState()
    data class Success(
        val sections: List<DonghuaCatalog.Section>,
        val refreshing: Boolean = false,
    ) : DonghuaUiState()
    /** Carries the classification so ErrorState can pick an action that works. */
    data class Error(val kind: ExtractionError) : DonghuaUiState()
    /** Every query succeeded and returned nothing — not a failure. */
    object Empty : DonghuaUiState()
}

/**
 * Donghua as a content source over the ordinary YouTube pipeline.
 *
 * There is deliberately no playback code here. A Donghua item is a VideoItem
 * like any other, the screen hands its url to the shared player route, and
 * extraction, stream selection, the media session and downloads all happen in
 * the same places they do for Home and Search. The tab this replaced was a
 * WebView, so none of that was reachable from it.
 */
class DonghuaViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = YouTubeRepository()
    private val db = (app as StreamFlowApp).database

    private val _uiState = MutableStateFlow<DonghuaUiState>(DonghuaUiState.Loading)
    val uiState: StateFlow<DonghuaUiState> = _uiState

    private val _genre = MutableStateFlow(DonghuaCatalog.genres.first())
    val genre: StateFlow<DonghuaCatalog.Genre> = _genre

    private val history: StateFlow<List<HistoryEntity>> = db.historyDao()
        .getRecentWithProgress(40)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Part-watched donghua, recomputed whenever the tab or history changes. */
    private val _continueWatching = MutableStateFlow<List<VideoItem>>(emptyList())
    val continueWatching: StateFlow<List<VideoItem>> = _continueWatching

    init {
        load()
        viewModelScope.launch {
            history.collect { recomputeContinueWatching() }
        }
    }

    fun setGenre(g: DonghuaCatalog.Genre) {
        if (g.id == _genre.value.id) return
        _genre.value = g
        load()
    }

    fun refresh() = load(refreshing = true)

    fun retry() = load()

    private fun load(refreshing: Boolean = false) {
        viewModelScope.launch {
            val previous = _uiState.value
            _uiState.value = when {
                refreshing && previous is DonghuaUiState.Success ->
                    previous.copy(refreshing = true)
                else -> DonghuaUiState.Loading
            }

            val g = _genre.value
            try {
                // Rows are independent searches, so they run together rather
                // than one after another -- five sequential extractions is five
                // round trips the user waits through for no reason.
                val loaded = coroutineScope {
                    DonghuaCatalog.sources.map { source ->
                        source.id to async {
                            // One row failing must not take the tab down: a
                            // query that errors contributes nothing and
                            // assemble() drops it, exactly like an empty one.
                            runCatching {
                                repo.search(DonghuaCatalog.queryFor(source, g)).videos
                            }.getOrDefault(emptyList())
                        }
                    }.associate { (id, job) -> id to job.await() }
                }

                val sections = DonghuaCatalog.assemble(loaded)
                _uiState.value =
                    if (sections.isEmpty()) DonghuaUiState.Empty
                    else DonghuaUiState.Success(sections)
                recomputeContinueWatching()
            } catch (e: Exception) {
                // Reached only when the whole scope fails; per-row failures are
                // already absorbed above.
                _uiState.value = DonghuaUiState.Error(classifyExtractionError(e))
            }
        }
    }

    private fun recomputeContinueWatching() {
        val state = _uiState.value
        val onScreen = (state as? DonghuaUiState.Success)
            ?.sections?.flatMap { it.videos } ?: emptyList()
        val rows = history.value
        _continueWatching.value = DonghuaCatalog.continueWatching(
            watched = rows.map { it.toVideoItem() },
            progress = rows.associate { h ->
                // position is ms, duration is seconds.
                h.url to if (h.duration > 0L)
                    (h.position / 1000f / h.duration).coerceIn(0f, 1f) else 0f
            },
            onScreen = onScreen,
        )
    }

    private fun HistoryEntity.toVideoItem() = VideoItem(
        url = url, title = title, thumbnailUrl = thumbnailUrl,
        uploaderName = uploaderName, viewCount = viewCount, duration = duration
    )

    /** Resume fraction for a card, so the row can draw its progress bar. */
    fun progressFor(url: String): Float {
        val h = history.value.firstOrNull { it.url == url } ?: return 0f
        return if (h.duration > 0L) (h.position / 1000f / h.duration).coerceIn(0f, 1f) else 0f
    }
}
