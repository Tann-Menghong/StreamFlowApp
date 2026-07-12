package com.streamflow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// A saved moment inside a video ("2:35 — that part"), listed in Library > Bookmarks
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val videoUrl: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val positionMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)
