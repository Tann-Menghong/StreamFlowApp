package com.streamflow.app.data.model

/** Lightweight UI-facing representation of a YouTube video, mapped from NewPipeExtractor's StreamInfoItem. */
data class VideoItem(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val uploaderName: String,
    val durationSeconds: Long,
    val viewCount: Long,
    val textualUploadDate: String?,
    val isShort: Boolean
)

/** A directly playable single-file option (YouTube's muxed progressive formats, or an audio-only fallback). */
sealed class PlaybackSource {
    data class Muxed(val url: String, val label: String) : PlaybackSource()
    data class AudioOnly(val url: String, val label: String) : PlaybackSource()
}

data class VideoDetails(
    val url: String,
    val title: String,
    val uploaderName: String,
    val uploaderUrl: String?,
    val thumbnailUrl: String?,
    val viewCount: Long,
    val textualUploadDate: String?,
    val durationSeconds: Long,
    val description: String,
    val relatedVideos: List<VideoItem>,
    val playbackOptions: List<PlaybackSource>,
    val bestAudioUrl: String?
)
