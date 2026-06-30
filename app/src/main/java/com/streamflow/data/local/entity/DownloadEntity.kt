package com.streamflow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val url: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val filePath: String,
    val downloadId: Long = -1L,
    val addedAt: Long = System.currentTimeMillis()
)
