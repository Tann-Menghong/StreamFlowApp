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
    val watchedAt: Long,
    val watchedPositionMs: Long = 0L
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

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val channelUrl: String,
    val name: String,
    val avatarUrl: String?,
    val subscribedAt: Long
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long
)

@Entity(tableName = "watch_later")
data class WatchLaterEntity(
    @PrimaryKey val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val uploaderName: String,
    val durationSeconds: Long,
    val addedAt: Long
)
