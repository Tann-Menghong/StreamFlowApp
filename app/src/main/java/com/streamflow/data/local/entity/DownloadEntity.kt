package com.streamflow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val url: String,     // original video URL
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val filePath: String,            // local file URI once complete ("" while downloading)
    val isAudio: Boolean,
    val downloadId: Long,            // system DownloadManager id
    val status: String,              // DOWNLOADING | DONE | FAILED
    val createdAt: Long = System.currentTimeMillis()
)
