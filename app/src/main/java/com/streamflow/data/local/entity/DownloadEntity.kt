package com.streamflow.data.local.entity

import androidx.room.Entity

// Composite key: with url alone, downloading the AUDIO of a video you'd already
// downloaded as VIDEO replaced the row (REPLACE insert) — the first download's
// completion update then matched nothing and the finished file was orphaned
@Entity(tableName = "downloads", primaryKeys = ["url", "isAudio"])
data class DownloadEntity(
    val url: String,                 // original video URL
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val filePath: String,            // local file URI once complete ("" while downloading)
    val isAudio: Boolean,
    val downloadId: Long,            // system DownloadManager id
    val status: String,              // DOWNLOADING | DONE | FAILED
    val createdAt: Long = System.currentTimeMillis()
)
