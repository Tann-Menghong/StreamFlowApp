package com.streamflow.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val uploaderName: String,
    val durationSeconds: Long,
    val watchedAt: Long
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val uploaderName: String,
    val durationSeconds: Long,
    val savedAt: Long
)
