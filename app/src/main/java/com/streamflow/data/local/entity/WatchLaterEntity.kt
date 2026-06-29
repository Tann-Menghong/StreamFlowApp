package com.streamflow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_later")
data class WatchLaterEntity(
    @PrimaryKey val url: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val viewCount: Long,
    val duration: Long,
    val addedAt: Long = System.currentTimeMillis()
)
