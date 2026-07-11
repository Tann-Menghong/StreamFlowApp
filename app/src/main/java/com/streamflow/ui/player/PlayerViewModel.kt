package com.streamflow.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.StreamFlowApp
import com.streamflow.data.PlaybackQueue
import com.streamflow.data.YouTubeRepository
import com.streamflow.data.ai.AiEngine
import com.streamflow.data.friendlyError
import com.streamflow.data.local.entity.DownloadEntity
import com.streamflow.data.local.entity.FavoriteEntity
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.local.entity.PlaylistEntity
import com.streamflow.data.local.entity.PlaylistItemEntity
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

    // ── Comment replies (keyed by comment identity, toggle to collapse) ───────
    private val _replies = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())
    val replies: StateFlow<Map<String, List<Comment>>> = _replies

    private val _repliesLoading = MutableStateFlow<Set<String>>(emptySet())
    val repliesLoading: StateFlow<Set<String>> = _repliesLoading

    fun replyKey(c: Comment) = "${c.author}|${c.publishedTime}|${c.text.hashCode()}"

    fun toggleReplies(videoUrl: String, comment: Comment) {
        val key = replyKey(comment)
        if (_replies.value.containsKey(key)) {
            _replies.value = _replies.value - key
            return
        }
        val page = comment.repliesPage ?: return
        if (key in _repliesLoading.value) return
        viewModelScope.launch {
            _repliesLoading.value = _repliesLoading.value + key
            val result = try { repo.getCommentReplies(videoUrl, page) } catch (_: Exception) { emptyList() }
            _replies.value = _replies.value + (key to result)
            _repliesLoading.value = _repliesLoading.value - key
        }
    }

    // ── Downloads ─────────────────────────────────────────────────────────────
    fun download(isAudio: Boolean) {
        val d = (_uiState.value as? PlayerUiState.Ready)?.details ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                val streams = repo.getDownloadStreams(d.url)
                val streamUrl = (if (isAudio) streams.audioUrl else streams.videoUrl)
                if (streamUrl == null) {
                    android.widget.Toast.makeText(app, "No downloadable stream found", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val id = com.streamflow.data.DownloadHelper.enqueue(app, streamUrl, d.title, isAudio)
                db.downloadDao().insert(DownloadEntity(
                    url = d.url, title = d.title, thumbnailUrl = d.thumbnailUrl,
                    uploaderName = d.uploaderName, filePath = "", isAudio = isAudio,
                    downloadId = id, status = "DOWNLOADING"
                ))
                android.widget.Toast.makeText(app,
                    if (isAudio) "Downloading audio…" else "Downloading video (${streams.videoHeight}p)…",
                    android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(app, "Download failed to start", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Local playlists ───────────────────────────────────────────────────────
    val playlists = db.playlistDao().getPlaylistsWithCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addToPlaylist(playlistId: Long) {
        val d = (_uiState.value as? PlayerUiState.Ready)?.details ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            db.playlistDao().addItem(PlaylistItemEntity(
                playlistId = playlistId, url = d.url, title = d.title,
                thumbnailUrl = d.thumbnailUrl, uploaderName = d.uploaderName, duration = d.duration
            ))
            android.widget.Toast.makeText(app, "Saved to playlist", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun createPlaylistAndAdd(name: String) {
        if (name.isBlank()) return
        val d = (_uiState.value as? PlayerUiState.Ready)?.details ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val id = db.playlistDao().create(PlaylistEntity(name = name.trim()))
            db.playlistDao().addItem(PlaylistItemEntity(
                playlistId = id, url = d.url, title = d.title,
                thumbnailUrl = d.thumbnailUrl, uploaderName = d.uploaderName, duration = d.duration
            ))
            android.widget.Toast.makeText(app, "Created \"${name.trim()}\"", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // ── On-device AI: summary + Q&A over the transcript ──────────────────────
    private val _aiOutput = MutableStateFlow("")
    val aiOutput: StateFlow<String> = _aiOutput

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy

    private val summaryCache = HashMap<String, String>()
    private var transcriptCache: Pair<String, String>? = null // videoUrl to transcript
    private var aiSession = 0 // bumped on video change so in-flight output can't leak into the new video

    // Transcript from subtitles when available (prefer English), else the
    // description — enough signal for a summary when captions are missing.
    private suspend fun aiSourceText(d: VideoDetails): String {
        transcriptCache?.let { if (it.first == d.url) return it.second }
        val track = d.subtitles.firstOrNull { it.name.contains("english", ignoreCase = true) }
            ?: d.subtitles.firstOrNull()
        val transcript = track?.let {
            try { AiEngine.fetchTranscript(it.url) } catch (_: Exception) { "" }
        }.orEmpty()
        val text = transcript.ifBlank { d.description }
        transcriptCache = d.url to text
        return text
    }

    private fun runAi(cacheKey: String?, buildTask: suspend (VideoDetails) -> String) {
        val d = (_uiState.value as? PlayerUiState.Ready)?.details ?: return
        cacheKey?.let { summaryCache[it] }?.let { _aiOutput.value = it; return }
        if (_aiBusy.value) return
        val session = aiSession
        viewModelScope.launch {
            _aiBusy.value = true
            _aiOutput.value = ""
            try {
                val prompt = buildTask(d)
                val result = AiEngine.generate(getApplication(), prompt) { partial ->
                    if (session == aiSession) _aiOutput.value = partial
                }
                if (session == aiSession) _aiOutput.value = result
                cacheKey?.let { summaryCache[it] = result }
            } catch (e: Exception) {
                if (session == aiSession) _aiOutput.value = "Couldn't run the AI: ${e.message ?: "unknown error"}"
            } finally {
                _aiBusy.value = false
            }
        }
    }

    fun aiSummarize() {
        val d = (_uiState.value as? PlayerUiState.Ready)?.details ?: return
        runAi(cacheKey = d.url) { details ->
            val source = AiEngine.fitToBudget(aiSourceText(details))
            AiEngine.chatPrompt(
                "Summarize this YouTube video in 4 to 6 short bullet points.\n\n" +
                "Title: ${details.title}\nChannel: ${details.uploaderName}\n\nTranscript:\n$source"
            )
        }
    }

    fun aiAsk(question: String) {
        if (question.isBlank()) return
        runAi(cacheKey = null) { details ->
            val source = AiEngine.fitToBudget(aiSourceText(details), AiEngine.PROMPT_CHAR_BUDGET - question.length)
            AiEngine.chatPrompt(
                "Using this YouTube video transcript, answer the question briefly.\n\n" +
                "Title: ${details.title}\n\nTranscript:\n$source\n\nQuestion: ${question.trim()}"
            )
        }
    }

    fun clearAiOutput() { _aiOutput.value = "" }

    // ── SponsorBlock ──────────────────────────────────────────────────────────
    private val _sponsorSegments = MutableStateFlow<List<SponsorSegment>>(emptyList())
    val sponsorSegments: StateFlow<List<SponsorSegment>> = _sponsorSegments

    // ── Audio-only (remembered across videos/sessions) ───────────────────────
    private val _audioOnly = MutableStateFlow(false)
    val audioOnly: StateFlow<Boolean> = _audioOnly

    init {
        viewModelScope.launch { _audioOnly.value = prefs.audioOnlyMode.first() }
    }

    // ── Playback queue ────────────────────────────────────────────────────────
    val queue = PlaybackQueue.queue

    fun loadVideo(videoUrl: String) {
        _currentUrl.value = videoUrl
        _replies.value = emptyMap()
        _comments.value = emptyList()
        commentsLoadedFor = ""
        _sponsorSegments.value = emptyList()
        _aiOutput.value = ""
        transcriptCache = null
        aiSession++
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
                    // Network-aware quality: a separate (usually lower) preference
                    // applies on mobile data; "SAME" falls back to the Wi-Fi setting
                    val cellularPref = prefs.qualityCellular.first()
                    val qualityPref = if (isOnCellular() && cellularPref != "SAME") cellularPref
                                      else prefs.quality.first()
                    _autoQuality.value = qualityPref == "AUTO"
                    // Data saver caps Auto at 480p
                    val quality = if (qualityPref == "AUTO" && prefs.dataSaver.first()) "480P" else qualityPref
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
            delay(3_000L) // extraction is tiny next to video buffering; start early so "next" is instant
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
        viewModelScope.launch { prefs.setAudioOnlyMode(_audioOnly.value) }
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
        if (prefs.incognito.first()) return // incognito: leave no history
        // REPLACE would wipe the saved resume position back to 0 (the "history
        // videos restart from the beginning" bug) — carry the old position over
        val prevPos = try { db.historyDao().getPosition(url) } catch (_: Exception) { 0L }
        db.historyDao().insert(HistoryEntity(
            url = url,
            title = details.title,
            thumbnailUrl = details.thumbnailUrl,
            uploaderName = details.uploaderName,
            viewCount = details.viewCount,
            duration = details.duration,
            position = prevPos
        ))
    }

    private fun isOnCellular(): Boolean = try {
        val cm = getApplication<Application>()
            .getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null &&
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    } catch (_: Exception) { false }

    private fun isDirectStream(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("file://") || lower.startsWith("content://") ||
               lower.contains(".m3u8") || lower.contains(".mp4") ||
               lower.contains(".m4a") || lower.contains(".webm") ||
               lower.contains("/hls/") || lower.contains("/stream/")
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
