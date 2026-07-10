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

    fun removeDownload(url: String) = viewModelScope.launch { db.downloadDao().delete(url) }
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
    fun removeFavorite(url: String) = viewModelScope.launch { db.favoriteDao().delete(url) }
    fun removeHistory(url: String) = viewModelScope.launch { db.historyDao().delete(url) }
    fun removeWatchLater(url: String) = viewModelScope.launch { db.watchLaterDao().delete(url) }
    fun clearHistory() = viewModelScope.launch { db.historyDao().clearAll() }
    fun clearFavorites() = viewModelScope.launch { db.favoriteDao().clearAll() }
    fun clearWatchLater() = viewModelScope.launch { db.watchLaterDao().clearAll() }
}
