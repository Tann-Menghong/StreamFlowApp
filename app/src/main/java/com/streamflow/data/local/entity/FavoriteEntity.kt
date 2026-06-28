package com.streamflow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val url: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val viewCount: Long,
    val duration: Long,
    val savedAt: Long = System.currentTimeMillis()
)
