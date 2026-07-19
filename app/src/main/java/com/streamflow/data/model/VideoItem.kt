package com.streamflow.data.model

data class VideoItem(
    val url: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val uploaderUrl: String = "",
    val uploaderAvatarUrl: String = "",
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

// A selectable audio language (YouTube multi-audio / dubbed tracks)
data class AudioTrackOption(
    val name: String,
    val url: String,
    val isOriginal: Boolean
)

// Seek-preview sprite sheets (YouTube storyboards): each url is a grid page of
// frames laid out framesPerPageX × framesPerPageY, one frame per durationPerFrameMs
data class Storyboard(
    val urls: List<String>,
    val frameWidth: Int,
    val frameHeight: Int,
    val totalCount: Int,
    val framesPerPageX: Int,
    val framesPerPageY: Int,
    val durationPerFrameMs: Int
)

data class VideoDetails(
    val url: String,
    val title: String,
    val uploaderName: String,
    val uploaderUrl: String = "",
    val uploaderAvatarUrl: String = "",
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
    val currentQuality: Int = 0,
    val isLive: Boolean = false,
    val videoCodec: String = "",
    val storyboard: Storyboard? = null,
    val uploadedAgo: String = "",
    // Only populated when the video has MORE than one audio language
    val audioTracks: List<AudioTrackOption> = emptyList()
)
