package com.streamflow.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.StreamFlowApp
import com.streamflow.data.local.dao.PlaylistWithCount
import com.streamflow.data.local.entity.DownloadEntity
import com.streamflow.data.local.entity.FavoriteEntity
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.local.entity.SubscriptionEntity
import com.streamflow.data.local.entity.WatchLaterEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as StreamFlowApp).database

    val favorites: StateFlow<List<FavoriteEntity>> = db.favoriteDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryEntity>> = db.historyDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchLater: StateFlow<List<WatchLaterEntity>> = db.watchLaterDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriptions: StateFlow<List<SubscriptionEntity>> = db.subscriptionDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<DownloadEntity>> = db.downloadDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistWithCount>> = db.playlistDao().getPlaylistsWithCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<com.streamflow.data.local.entity.BookmarkEntity>> = db.bookmarkDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteBookmark(id: Long) = viewModelScope.launch { db.bookmarkDao().delete(id) }

    // Opening a bookmark: write its position into history so the player's
    // normal resume logic starts the video at the saved moment. If a history
    // row already exists, only its position is touched — a REPLACE insert here
    // would zero out that row's real viewCount/duration (bookmarks don't carry
    // them), and if the video later fails to re-extract, loadVideo's own
    // recordHistory (which would normally repair those fields) never runs,
    // leaving the corruption permanent.
    fun primeBookmarkPosition(b: com.streamflow.data.local.entity.BookmarkEntity, then: () -> Unit) {
        viewModelScope.launch {
            val hasExisting = try { db.historyDao().getPosition(b.videoUrl); true } catch (_: Exception) { false }
            if (hasExisting) {
                db.historyDao().updatePosition(b.videoUrl, b.positionMs)
            } else {
                db.historyDao().insert(HistoryEntity(
                    url = b.videoUrl, title = b.title, thumbnailUrl = b.thumbnailUrl,
                    uploaderName = b.uploaderName, viewCount = 0L, duration = 0L,
                    position = b.positionMs
                ))
            }
            then()
        }
    }

    fun removeDownload(url: String) = viewModelScope.launch { db.downloadDao().delete(url) }

    // Failed downloads used to dead-end (delete was the only option) — re-extract
    // a fresh stream URL and re-enqueue with the system DownloadManager
    fun retryDownload(d: DownloadEntity) = viewModelScope.launch {
        try {
            val streams = com.streamflow.data.YouTubeRepository().getDownloadStreams(d.url)
            val streamUrl = (if (d.isAudio) streams.audioUrl else streams.videoUrl) ?: run {
                android.widget.Toast.makeText(getApplication(),
                    "No downloadable stream found", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val id = com.streamflow.data.DownloadHelper.enqueue(getApplication(), streamUrl, d.title, d.isAudio)
            db.downloadDao().insert(d.copy(downloadId = id, status = "DOWNLOADING", filePath = ""))
        } catch (_: Exception) {
            android.widget.Toast.makeText(getApplication(),
                "Retry failed — check your connection", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    fun deletePlaylist(id: Long) = viewModelScope.launch {
        db.playlistDao().clearItems(id)
        db.playlistDao().deletePlaylist(id)
    }
    fun createPlaylist(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) db.playlistDao().create(
            com.streamflow.data.local.entity.PlaylistEntity(name = name.trim()))
    }

    fun unsubscribe(channelUrl: String) = viewModelScope.launch { db.subscriptionDao().delete(channelUrl) }
    fun setGroup(channelUrl: String, group: String) = viewModelScope.launch {
        db.subscriptionDao().updateGroup(channelUrl, group.trim())
    }
    fun setNotify(channelUrl: String, notify: Boolean) = viewModelScope.launch {
        db.subscriptionDao().updateNotify(channelUrl, notify)
    }
    // Undo snapshots: swipe-delete then Undo must restore the ORIGINAL row.
    // Rebuilding from the visible card data lost savedAt/addedAt (the restored
    // item jumped to a different list position) and, for history, wiped the
    // saved resume position and original watch date.
    private var lastDeletedFavorite: FavoriteEntity? = null
    private var lastDeletedHistory: HistoryEntity? = null
    private var lastDeletedWatchLater: WatchLaterEntity? = null

    fun removeFavorite(url: String) = viewModelScope.launch {
        lastDeletedFavorite = try { db.favoriteDao().getByUrl(url) } catch (_: Exception) { null }
        db.favoriteDao().delete(url)
    }
    fun removeHistory(url: String) = viewModelScope.launch {
        lastDeletedHistory = try { db.historyDao().getByUrl(url) } catch (_: Exception) { null }
        db.historyDao().delete(url)
    }
    fun removeWatchLater(url: String) = viewModelScope.launch {
        lastDeletedWatchLater = try { db.watchLaterDao().getByUrl(url) } catch (_: Exception) { null }
        db.watchLaterDao().delete(url)
    }

    fun restoreFavorite(v: com.streamflow.data.model.VideoItem) = viewModelScope.launch {
        db.favoriteDao().insert(
            lastDeletedFavorite?.takeIf { it.url == v.url } ?: FavoriteEntity(
                url = v.url, title = v.title, thumbnailUrl = v.thumbnailUrl,
                uploaderName = v.uploaderName, viewCount = v.viewCount, duration = v.duration))
    }
    fun restoreHistory(v: com.streamflow.data.model.VideoItem) = viewModelScope.launch {
        db.historyDao().insert(
            lastDeletedHistory?.takeIf { it.url == v.url } ?: HistoryEntity(
                url = v.url, title = v.title, thumbnailUrl = v.thumbnailUrl,
                uploaderName = v.uploaderName, viewCount = v.viewCount, duration = v.duration))
    }
    fun restoreWatchLater(v: com.streamflow.data.model.VideoItem) = viewModelScope.launch {
        db.watchLaterDao().insert(
            lastDeletedWatchLater?.takeIf { it.url == v.url } ?: WatchLaterEntity(
                url = v.url, title = v.title, thumbnailUrl = v.thumbnailUrl,
                uploaderName = v.uploaderName, viewCount = v.viewCount, duration = v.duration))
    }
    fun clearHistory() = viewModelScope.launch { db.historyDao().clearAll() }
    fun clearFavorites() = viewModelScope.launch { db.favoriteDao().clearAll() }
    fun clearWatchLater() = viewModelScope.launch { db.watchLaterDao().clearAll() }
}
