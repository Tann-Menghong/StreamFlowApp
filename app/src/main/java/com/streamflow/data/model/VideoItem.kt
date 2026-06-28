package com.streamflow.data.model

data class VideoItem(
    val url: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val viewCount: Long,
    val duration: Long
)

data class VideoDetails(
    val url: String,
    val title: String,
    val uploaderName: String,
    val viewCount: Long,
    val likeCount: Long,
    val description: String,
    val streamUrl: String,
    val audioUrl: String?,
    val thumbnailUrl: String,
    val relatedVideos: List<VideoItem> = emptyList()
)
