package com.streamflow.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// A saved moment inside a video ("2:35 — that part"), listed in Library > Bookmarks.
// Indexed on videoUrl: the per-video lookup runs on every video open and the
// primary key is an autoGenerate id, so nothing indexed the column actually
// being filtered on — every open scanned the whole table.
@Entity(tableName = "bookmarks", indices = [Index("videoUrl")])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val videoUrl: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val positionMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)
