package com.streamflow.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.app.data.db.AppDatabase
import com.streamflow.app.data.db.BookmarkEntity
import com.streamflow.app.data.db.HistoryEntity
import com.streamflow.app.data.model.VideoItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val database: AppDatabase) : ViewModel() {

    val history: StateFlow<List<VideoItem>> = database.historyDao().observeAll()
        .map { entities -> entities.map { it.toVideoItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<VideoItem>> = database.bookmarkDao().observeAll()
        .map { entities -> entities.map { it.toVideoItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearHistory() {
        viewModelScope.launch { database.historyDao().clear() }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch { database.bookmarkDao().deleteByUrl(url) }
    }

    private fun HistoryEntity.toVideoItem() = VideoItem(
        url = url,
        title = title,
        thumbnailUrl = thumbnailUrl,
        uploaderName = uploaderName,
        uploaderUrl = null,
        durationSeconds = durationSeconds,
        viewCount = -1,
        textualUploadDate = null,
        isShort = false
    )

    private fun BookmarkEntity.toVideoItem() = VideoItem(
        url = url,
        title = title,
        thumbnailUrl = thumbnailUrl,
        uploaderName = uploaderName,
        uploaderUrl = null,
        durationSeconds = durationSeconds,
        viewCount = -1,
        textualUploadDate = null,
        isShort = false
    )
}
