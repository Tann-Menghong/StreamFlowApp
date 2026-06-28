package com.streamflow.app.data.model

/** Lightweight UI-facing representation of a YouTube video, mapped from NewPipeExtractor's StreamInfoItem. */
data class VideoItem(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val uploaderName: String,
    val uploaderUrl: String?,
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

/** A YouTube channel's profile info plus its uploaded videos, mapped from NewPipeExtractor's ChannelInfo. */
data class ChannelDetails(
    val url: String,
    val name: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val subscriberCount: Long,
    val description: String,
    val videos: List<VideoItem>,
    val playlists: List<PlaylistItem>
)

/** A playlist's summary as shown in a list (e.g. a channel's Playlists tab), mapped from PlaylistInfoItem. */
data class PlaylistItem(
    val url: String,
    val name: String,
    val thumbnailUrl: String?,
    val uploaderName: String,
    val streamCount: Long
)

/** A playlist's full details plus its videos, mapped from NewPipeExtractor's PlaylistInfo. */
data class PlaylistDetails(
    val url: String,
    val name: String,
    val uploaderName: String,
    val thumbnailUrl: String?,
    val streamCount: Long,
    val videos: List<VideoItem>
)

/** A channel's summary as shown in a list (e.g. search results), mapped from ChannelInfoItem. */
data class ChannelItem(
    val url: String,
    val name: String,
    val thumbnailUrl: String?,
    val subscriberCount: Long,
    val description: String
)

/** A single search result, which YouTube mixes between matching videos and channels. */
sealed class SearchResultItem {
    data class Video(val video: VideoItem) : SearchResultItem()
    data class Channel(val channel: ChannelItem) : SearchResultItem()
}
