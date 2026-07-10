package com.streamflow.data.model

data class VideoItem(
    val url: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val uploaderUrl: String = "",
    val viewCount: Long,
    val duration: Long,
    val uploadedAgo: String = "",
    val uploadedEpoch: Long = 0L
)

data class VideoChapter(
    val title: String,
    val startMs: Long
)

data class SubtitleTrack(
    val name: String,
    val url: String,
    val mimeType: String = "text/vtt"
)

data class VideoDetails(
    val url: String,
    val title: String,
    val uploaderName: String,
    val uploaderUrl: String = "",
    val viewCount: Long,
    val likeCount: Long,
    val duration: Long,
    val description: String,
    val streamUrl: String,
    val audioUrl: String?,
    val thumbnailUrl: String,
    val relatedVideos: List<VideoItem> = emptyList(),
    val chapters: List<VideoChapter> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val availableQualities: List<Int> = emptyList(),
    val currentQuality: Int = 0
)
