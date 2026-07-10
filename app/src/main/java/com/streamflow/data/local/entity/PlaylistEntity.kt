package com.streamflow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_items", primaryKeys = ["playlistId", "url"])
data class PlaylistItemEntity(
    val playlistId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val duration: Long,
    val addedAt: Long = System.currentTimeMillis()
)
