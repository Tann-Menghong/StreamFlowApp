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
    private var commentsGeneration = 0

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
            // Don't cache an empty result: the repo maps failures to emptyList,
            // and caching it would pin "no replies" forever — leaving it out
            // lets the next tap retry the fetch
            if (result.isNotEmpty()) _replies.value = _replies.value + (key to result)
            _repliesLoading.value = _repliesLoading.value - key
        }
    }

    // ── Downloads ─────────────────────────────────────────────────────────────
    fun download(isAudio: Boolean, maxHeight: Int = Int.MAX_VALUE) {
        val d = (_uiState.value as? PlayerUiState.Ready)?.details ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                val streams = repo.getDownloadStreams(d.url, maxHeight)
                val streamUrl = (if (isAudio) streams.audioUrl else streams.videoUrl)
                if (streamUrl == null) {
                    android.widget.Toast.makeText(app, "No downloadable stream found", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val id = com.streamflow.data.DownloadHelper.enqueue(app, streamUrl, d.title, isAudio)
                // Save captions alongside video downloads (English preferred)
                if (!isAudio) {
                    val sub = d.subtitles.firstOrNull { it.name.contains("english", ignoreCase = true) }
                        ?: d.subtitles.firstOrNull()
                    sub?.let { com.streamflow.data.DownloadHelper.enqueueSubtitle(app, it.url, d.title) }
                }
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

    // ── Return YouTube Dislike + DeArrow (both pref-gated) ───────────────────
    private val _dislikes = MutableStateFlow<Long?>(null)
    val dislikes: StateFlow<Long?> = _dislikes

    private val _altTitle = MutableStateFlow<String?>(null)
    val altTitle: StateFlow<String?> = _altTitle

    private fun loadExtras(videoUrl: String) {
        viewModelScope.launch {
            if (!prefs.showDislikes.first()) return@launch
            val d = repo.getDislikes(videoUrl)
            // Stale guard: same class as sponsor segments — a slow response for
            // the previous video must not show under the new one
            if (_currentUrl.value == videoUrl) _dislikes.value = d
        }
        viewModelScope.launch {
            if (!prefs.deArrow.first()) return@launch
            val t = repo.getDeArrowTitle(videoUrl)
            if (_currentUrl.value == videoUrl) _altTitle.value = t
        }
    }

    // ── Audio-only (remembered across videos/sessions) ───────────────────────
    private val _audioOnly = MutableStateFlow(false)
    val audioOnly: StateFlow<Boolean> = _audioOnly

    init {
        viewModelScope.launch { _audioOnly.value = prefs.audioOnlyMode.first() }
    }

    // ── Playback queue ────────────────────────────────────────────────────────
    val queue = PlaybackQueue.queue

    // Clip-moment bookmarks for the current video (seekbar markers)
    val videoBookmarks: StateFlow<List<com.streamflow.data.local.entity.BookmarkEntity>> = _currentUrl
        .flatMapLatest { url ->
            if (url.isEmpty()) flowOf(emptyList())
            else db.bookmarkDao().getForVideo(url)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bumped per load/quality-change so a slow older extraction can't clobber
    // a newer one (stale-response race — same pattern as ChannelViewModel)
    private var loadGeneration = 0

    fun loadVideo(videoUrl: String) {
        _currentUrl.value = videoUrl
        _replies.value = emptyMap()
        _comments.value = emptyList()
        commentsLoadedFor = ""
        commentsGeneration++
        _sponsorSegments.value = emptyList()
        _dislikes.value = null
        _altTitle.value = null
        _aiOutput.value = ""
        transcriptCache = null
        aiSession++
        loadExtras(videoUrl)
        val gen = ++loadGeneration
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
                    // Data saver caps Auto at 480p; battery saver caps everything at
                    // 480p — but a genuine CAP, not a floor: if the user deliberately
                    // picked 360P (even lower, to save more than 480p would), battery
                    // saver must not raise it back up to 480p.
                    val saverOn = prefs.batterySaver.first()
                    val quality = when {
                        saverOn -> capQuality(qualityPref, "480P")
                        qualityPref == "AUTO" && prefs.dataSaver.first() -> "480P"
                        else -> qualityPref
                    }
                    val details = repo.getVideoDetails(videoUrl, quality)
                    if (gen != loadGeneration) return@launch // superseded by a newer video
                    _uiState.value = PlayerUiState.Ready(details)
                    recordHistory(details, videoUrl)
                    if (!saverOn) prefetchNext(details, quality) // saver: no background prefetch
                } catch (e: Exception) {
                    if (gen != loadGeneration) return@launch
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
        val wasAuto = _autoQuality.value
        _autoQuality.value = height == null
        val gen = ++loadGeneration
        viewModelScope.launch {
            // Battery saver is a hard cap (Settings literally says "Cap quality at
            // 480p") — without this, picking a quality from the in-player menu
            // silently bypassed it, so the setting didn't actually do what it says.
            val cappedHeight = if (prefs.batterySaver.first() && (height == null || height > 480)) 480 else height
            _uiState.value = PlayerUiState.Loading
            try {
                val details = repo.getVideoDetails(videoUrl, "AUTO", maxHeightOverride = cappedHeight)
                if (gen != loadGeneration) return@launch // a newer video/quality pick replaced this
                _uiState.value = PlayerUiState.Ready(details)
            } catch (_: Exception) {
                if (gen != loadGeneration) return@launch
                // Revert to the working stream rather than showing an error — and
                // put the quality menu back too, or it would claim the new pick took
                _autoQuality.value = wasAuto
                _uiState.value = PlayerUiState.Ready(current.details)
            }
        }
    }

    fun loadComments(videoUrl: String) {
        if (commentsLoadedFor == videoUrl) return
        commentsLoadedFor = videoUrl
        // Generation guard: without it, a slow fetch for the PREVIOUS video could
        // land after a video switch and show the wrong video's comments
        val gen = ++commentsGeneration
        viewModelScope.launch {
            _commentsLoading.value = true
            try {
                val result = repo.getComments(videoUrl)
                if (gen != commentsGeneration) return@launch
                _comments.value = result
                // The repo maps failures to an empty list, so an empty result may
                // mean "network hiccup", not "no comments" — clear the marker so
                // reopening the sheet retries instead of showing empty forever
                if (result.isEmpty() && commentsLoadedFor == videoUrl) commentsLoadedFor = ""
            } catch (_: Exception) {
                if (gen != commentsGeneration) return@launch
                _comments.value = emptyList()
                if (commentsLoadedFor == videoUrl) commentsLoadedFor = ""
            } finally {
                if (gen == commentsGeneration) _commentsLoading.value = false
            }
        }
    }

    fun loadSponsorSegments(videoUrl: String) {
        viewModelScope.launch {
            // User-chosen categories (Settings > Playback > SponsorBlock);
            // an empty set means auto-skip is off entirely
            val cats = prefs.sponsorCategories.first()
            val segments = try { repo.getSponsorSegments(videoUrl, cats) } catch (_: Exception) { emptyList() }
            // Stale guard: a slow fetch for the previous video must not attach its
            // segments to the new one — auto-skip would jump at the wrong times
            if (_currentUrl.value == videoUrl) _sponsorSegments.value = segments
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

    // Save the current moment as a bookmark (Library > Bookmarks)
    fun addBookmark(positionMs: Long) {
        val d = (_uiState.value as? PlayerUiState.Ready)?.details ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            db.bookmarkDao().insert(com.streamflow.data.local.entity.BookmarkEntity(
                videoUrl = d.url, title = d.title, thumbnailUrl = d.thumbnailUrl,
                uploaderName = d.uploaderName, positionMs = positionMs
            ))
            val s = positionMs / 1000
            val label = if (s >= 3600) "%d:%02d:%02d".format(java.util.Locale.US, s / 3600, (s % 3600) / 60, s % 60)
                        else "%d:%02d".format(java.util.Locale.US, s / 60, s % 60)
            android.widget.Toast.makeText(app, "Moment saved at $label", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun savePosition(url: String, positionMs: Long) {
        viewModelScope.launch {
            // Incognito must leave no trace: without this, re-watching a video that
            // already has a history row kept updating its saved progress (leak).
            if (prefs.incognito.first()) return@launch
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

    // Caps `pref` at `cap` without ever RAISING a quality the user picked lower
    // than the cap (e.g. battery saver capping at 480P must leave a manual
    // 360P choice alone, not bump it up).
    private fun capQuality(pref: String, cap: String): String {
        if (pref == "AUTO") return cap
        val order = listOf("360P", "480P", "720P", "1080P")
        val prefIdx = order.indexOf(pref)
        val capIdx = order.indexOf(cap)
        return if (prefIdx == -1 || capIdx == -1 || prefIdx <= capIdx) pref else cap
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
