package com.streamflow.data

import com.streamflow.data.model.Comment
import com.streamflow.data.model.SponsorSegment
import com.streamflow.data.model.Storyboard
import com.streamflow.data.model.SubtitleTrack
import com.streamflow.data.model.VideoChapter
import com.streamflow.data.model.VideoDetails
import com.streamflow.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.net.URLEncoder

class YouTubeRepository {

    private val youtube get() = NewPipe.getService(ServiceList.YouTube.serviceId)
    private val httpClient = OkHttpDownloader.instance.client

    data class PagedResult(
        val videos: List<VideoItem>,
        val nextPage: Page?,
        val playlists: List<PlaylistItem> = emptyList()
    )

    data class ChannelResult(
        val name: String,
        val avatarUrl: String,
        val bannerUrl: String,
        val subscriberCount: Long,
        val videos: List<VideoItem>,
        val nextPage: Page?,
        val availableTabs: List<String> = emptyList(), // "videos", "shorts", "livestreams", "playlists"
        val description: String = "",
        val playlists: List<PlaylistItem> = emptyList()
    )

    // Search results for the Channels / Playlists filter tabs
    data class ChannelItem(
        val name: String,
        val url: String,
        val avatarUrl: String,
        val subscriberCount: Long,
        val streamCount: Long,
        val description: String
    )

    data class PlaylistItem(
        val name: String,
        val url: String,
        val thumbnailUrl: String,
        val uploaderName: String,
        val streamCount: Long
    )

    suspend fun getTrending(country: String = "US"): PagedResult = withContext(Dispatchers.IO) {
        NewPipe.setPreferredContentCountry(ContentCountry(country))
        val kiosk = youtube.kioskList.getDefaultKioskExtractor()
        kiosk.fetchPage()
        val page = kiosk.initialPage
        PagedResult(
            videos = page.items.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
            nextPage = page.nextPage
        )
    }

    suspend fun getTrendingNextPage(nextPage: Page): PagedResult = withContext(Dispatchers.IO) {
        try {
            val kiosk = youtube.kioskList.getDefaultKioskExtractor()
            kiosk.fetchPage()
            val page = kiosk.getPage(nextPage)
            PagedResult(
                videos = page.items.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
                nextPage = page.nextPage
            )
        } catch (e: Exception) {
            e.printStackTrace()
            PagedResult(emptyList(), null)
        }
    }

    suspend fun search(query: String): PagedResult = withContext(Dispatchers.IO) {
        val extractor = youtube.getSearchExtractor(query)
        extractor.fetchPage()
        val page = extractor.initialPage
        PagedResult(
            videos = page.items.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
            nextPage = page.nextPage
        )
    }

    suspend fun searchNextPage(query: String, nextPage: Page): PagedResult = withContext(Dispatchers.IO) {
        val extractor = youtube.getSearchExtractor(query)
        extractor.fetchPage()
        val page = extractor.getPage(nextPage)
        PagedResult(
            videos = page.items.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
            nextPage = page.nextPage
        )
    }

    suspend fun searchChannels(query: String): List<ChannelItem> = withContext(Dispatchers.IO) {
        val extractor = youtube.getSearchExtractor(query, listOf("channels"), "")
        extractor.fetchPage()
        extractor.initialPage.items
            .filterIsInstance<org.schabi.newpipe.extractor.channel.ChannelInfoItem>()
            .map { c ->
                ChannelItem(
                    name = c.name ?: "",
                    url = c.url ?: "",
                    avatarUrl = try { c.thumbnails.lastOrNull()?.url ?: "" } catch (_: Exception) { "" },
                    subscriberCount = try { c.subscriberCount } catch (_: Exception) { -1L },
                    streamCount = try { c.streamCount } catch (_: Exception) { -1L },
                    description = try { c.description ?: "" } catch (_: Exception) { "" }
                )
            }.filter { it.url.isNotEmpty() }.distinctBy { it.url }
    }

    suspend fun searchPlaylists(query: String): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val extractor = youtube.getSearchExtractor(query, listOf("playlists"), "")
        extractor.fetchPage()
        extractor.initialPage.items
            .filterIsInstance<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>()
            .map { p ->
                PlaylistItem(
                    name = p.name ?: "",
                    url = p.url ?: "",
                    thumbnailUrl = try { p.thumbnails.lastOrNull()?.url ?: "" } catch (_: Exception) { "" },
                    uploaderName = try { p.uploaderName ?: "" } catch (_: Exception) { "" },
                    streamCount = try { p.streamCount } catch (_: Exception) { -1L }
                )
            }.filter { it.url.isNotEmpty() }.distinctBy { it.url }
    }

    suspend fun getVideoDetails(
        videoUrl: String,
        qualityPref: String = "AUTO",
        maxHeightOverride: Int? = null
    ): VideoDetails =
        withContext(Dispatchers.IO) {
            val cacheKey = VideoDetailsCache.key(videoUrl, qualityPref, maxHeightOverride)
            VideoDetailsCache.get(cacheKey)?.let { return@withContext it }

            val info = StreamInfo.getInfo(youtube, videoUrl)

            val related = info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideoItem() }

            // AUTO starts at 720p for fast startup (highest resolutions are often
            // VP9/AV1 — software-decoded on many phones — and need far more
            // bandwidth to begin playing). Manual picks can still go higher.
            val maxHeight = maxHeightOverride ?: when (qualityPref) {
                "1080P" -> 1080
                "720P"  -> 720
                "480P"  -> 480
                "360P"  -> 360
                else    -> 720
            }

            val streamUrl: String
            val audioUrl: String?

            // All heights available across muxed + video-only streams (for the quality menu)
            val availableQualities = (
                info.videoStreams.mapNotNull { s -> s.height.takeIf { it > 0 && !s.content.isNullOrEmpty() } } +
                info.videoOnlyStreams.mapNotNull { s -> s.height.takeIf { it > 0 && !s.content.isNullOrEmpty() } }
            ).distinct().sortedDescending()

            // At equal height prefer MP4/H.264 (hardware-decoded everywhere) over
            // WEBM VP9/AV1 — this is the difference between instant start and a
            // long buffering wait on most devices
            val muxedStream = info.videoStreams
                .filter { !it.content.isNullOrEmpty() && it.height <= maxHeight }
                .maxWithOrNull(compareBy(
                    { it.height },
                    { if (it.format == org.schabi.newpipe.extractor.MediaFormat.MPEG_4) 1 else 0 }
                ))

            val videoOnlyStream = info.videoOnlyStreams
                .filter { !it.content.isNullOrEmpty() && it.height <= maxHeight }
                .maxWithOrNull(compareBy(
                    { it.height },
                    { if (it.format == org.schabi.newpipe.extractor.MediaFormat.MPEG_4) 1 else 0 }
                ))

            // Prefer M4A audio (pairs safely with MP4 video in the merged source)
            val audioStream = info.audioStreams
                .filter { !it.content.isNullOrEmpty() }
                .maxWithOrNull(compareBy(
                    { if (it.format == org.schabi.newpipe.extractor.MediaFormat.M4A) 1 else 0 },
                    { it.averageBitrate }
                ))

            val hlsUrl = info.hlsUrl
            val dashUrl = try { info.dashMpdUrl } catch (_: Exception) { null }
            val isLive = try {
                info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
            } catch (_: Exception) { false }

            val currentQuality: Int
            val videoCodec: String
            when {
                // Live streams: play the HLS (or DASH) manifest; ExoPlayer handles quality adaptively
                isLive && (!hlsUrl.isNullOrEmpty() || !dashUrl.isNullOrEmpty()) -> {
                    streamUrl = if (!hlsUrl.isNullOrEmpty()) hlsUrl!! else dashUrl!!
                    audioUrl = null
                    currentQuality = 0
                    videoCodec = "Adaptive"
                }
                videoOnlyStream != null && audioStream != null &&
                    videoOnlyStream.height > (muxedStream?.height ?: 0) -> {
                    streamUrl = videoOnlyStream.content ?: throw Exception("Video-only stream URL is null")
                    audioUrl = audioStream.content
                    currentQuality = videoOnlyStream.height
                    videoCodec = codecLabel(videoOnlyStream.format)
                }
                muxedStream != null -> {
                    streamUrl = muxedStream.content ?: throw Exception("Muxed stream URL is null")
                    audioUrl = null
                    currentQuality = muxedStream.height
                    videoCodec = codecLabel(muxedStream.format)
                }
                videoOnlyStream != null && audioStream != null -> {
                    streamUrl = videoOnlyStream.content ?: throw Exception("Video-only stream URL is null")
                    audioUrl = audioStream.content
                    currentQuality = videoOnlyStream.height
                    videoCodec = codecLabel(videoOnlyStream.format)
                }
                !hlsUrl.isNullOrEmpty() -> {
                    streamUrl = hlsUrl!!
                    audioUrl = null
                    currentQuality = 0
                    videoCodec = "Adaptive"
                }
                audioStream != null -> {
                    streamUrl = audioStream.content ?: throw Exception("Audio stream URL is null")
                    audioUrl = null
                    currentQuality = 0
                    videoCodec = "Audio"
                }
                else -> throw Exception("No playable stream found for: $videoUrl")
            }

            val chapters = try {
                info.streamSegments.map { seg ->
                    VideoChapter(title = seg.title, startMs = seg.startTimeSeconds.toLong() * 1000L)
                }
            } catch (_: Exception) { emptyList() }

            val subtitles = try {
                info.subtitles.mapNotNull { sub ->
                    val url = sub.content ?: return@mapNotNull null
                    if (url.isEmpty()) return@mapNotNull null
                    SubtitleTrack(
                        name = sub.displayLanguageName ?: "Unknown",
                        url = url,
                        mimeType = "text/vtt"
                    )
                }
            } catch (_: Exception) { emptyList() }

            // Seek-preview sprite sheets; pick the frameset closest to ~200px wide
            // (big enough to see, small enough to load instantly while scrubbing)
            val storyboard = try {
                info.previewFrames
                    .filter { it.urls.isNotEmpty() && it.frameWidth > 0 && it.durationPerFrame > 0 }
                    .minByOrNull { kotlin.math.abs(it.frameWidth - 200) }
                    ?.let { fs ->
                        Storyboard(
                            urls = fs.urls,
                            frameWidth = fs.frameWidth,
                            frameHeight = fs.frameHeight,
                            totalCount = fs.totalCount,
                            framesPerPageX = fs.framesPerPageX,
                            framesPerPageY = fs.framesPerPageY,
                            durationPerFrameMs = fs.durationPerFrame
                        )
                    }
            } catch (_: Exception) { null }

            val details = VideoDetails(
                url = videoUrl,
                title = info.name,
                uploaderName = info.uploaderName ?: "Unknown",
                uploaderUrl = try { info.uploaderUrl ?: "" } catch (_: Exception) { "" },
                uploaderAvatarUrl = try { info.uploaderAvatars.lastOrNull()?.url ?: "" } catch (_: Exception) { "" },
                viewCount = info.viewCount,
                likeCount = info.likeCount,
                duration = info.duration,
                description = info.description?.content ?: "",
                streamUrl = streamUrl,
                audioUrl = audioUrl,
                thumbnailUrl = info.thumbnails.firstOrNull()?.url ?: "",
                relatedVideos = related,
                chapters = chapters,
                subtitles = subtitles,
                availableQualities = if (isLive) emptyList() else availableQualities,
                currentQuality = currentQuality,
                isLive = isLive,
                videoCodec = videoCodec,
                storyboard = storyboard
            )
            VideoDetailsCache.put(cacheKey, details)
            details
        }

    private fun codecLabel(format: org.schabi.newpipe.extractor.MediaFormat?): String = when (format) {
        org.schabi.newpipe.extractor.MediaFormat.MPEG_4 -> "H.264 / MP4"
        org.schabi.newpipe.extractor.MediaFormat.WEBM   -> "VP9 / WebM"
        org.schabi.newpipe.extractor.MediaFormat.v3GPP  -> "3GPP"
        else -> format?.getName() ?: ""
    }

    suspend fun getChannelInfo(channelUrl: String, tabFilter: String = "videos"): ChannelResult = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(youtube, channelUrl)

        val avatarUrl = try { info.avatars.lastOrNull()?.url ?: "" } catch (_: Exception) { "" }
        val bannerUrl = try { info.banners.lastOrNull()?.url ?: "" } catch (_: Exception) { "" }
        val subscriberCount = try { info.subscriberCount } catch (_: Exception) { -1L }

        val availableTabs = try {
            info.tabs.mapNotNull { it.contentFilters.firstOrNull()?.lowercase() }
                .filter { it in listOf("videos", "shorts", "livestreams", "playlists") }
                .distinct()
        } catch (_: Exception) { emptyList() }

        var videos = emptyList<VideoItem>()
        var playlists = emptyList<PlaylistItem>()
        var nextPage: Page? = null

        try {
            val videoTab = info.tabs.firstOrNull { tab ->
                tab.contentFilters.any { it.contains(tabFilter, ignoreCase = true) }
            } ?: info.tabs.firstOrNull()

            if (videoTab != null) {
                val tabInfo = ChannelTabInfo.getInfo(youtube, videoTab)
                videos = tabInfo.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() }
                playlists = tabInfo.relatedItems
                    .filterIsInstance<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>()
                    .map { it.toPlaylistItem() }.filter { it.url.isNotEmpty() }.distinctBy { it.url }
                nextPage = tabInfo.nextPage
            }
        } catch (_: Exception) {}

        ChannelResult(
            name = info.name ?: "",
            avatarUrl = avatarUrl,
            bannerUrl = bannerUrl,
            subscriberCount = subscriberCount,
            videos = videos,
            nextPage = nextPage,
            availableTabs = availableTabs,
            description = try { info.description ?: "" } catch (_: Exception) { "" },
            playlists = playlists
        )
    }

    private fun org.schabi.newpipe.extractor.playlist.PlaylistInfoItem.toPlaylistItem() = PlaylistItem(
        name = name ?: "",
        url = url ?: "",
        thumbnailUrl = try { thumbnails.lastOrNull()?.url ?: "" } catch (_: Exception) { "" },
        uploaderName = try { uploaderName ?: "" } catch (_: Exception) { "" },
        streamCount = try { streamCount } catch (_: Exception) { -1L }
    )

    suspend fun getChannelNextPage(channelUrl: String, nextPage: Page, tabFilter: String = "videos"): PagedResult = withContext(Dispatchers.IO) {
        try {
            val info = ChannelInfo.getInfo(youtube, channelUrl)
            val videoTab = info.tabs.firstOrNull { tab ->
                tab.contentFilters.any { it.contains(tabFilter, ignoreCase = true) }
            } ?: info.tabs.firstOrNull() ?: return@withContext PagedResult(emptyList(), null)

            val page = ChannelTabInfo.getMoreItems(youtube, videoTab, nextPage)
            PagedResult(
                videos = page.items.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
                nextPage = page.nextPage,
                playlists = page.items
                    .filterIsInstance<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>()
                    .map { it.toPlaylistItem() }.filter { it.url.isNotEmpty() }.distinctBy { it.url }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            PagedResult(emptyList(), null)
        }
    }

    private fun CommentsInfoItem.toComment() = Comment(
        author = uploaderName ?: "",
        text = try { commentText?.content ?: "" } catch (_: Exception) { commentText.toString() },
        likeCount = likeCount.coerceAtLeast(0).toLong(),
        avatarUrl = try { thumbnails.firstOrNull()?.url ?: "" } catch (_: Exception) { "" },
        isOwnerComment = false,
        publishedTime = try { textualUploadDate ?: "" } catch (_: Exception) { "" },
        replyCount = try { replyCount } catch (_: Exception) { 0 },
        repliesPage = try { replies } catch (_: Exception) { null }
    )

    suspend fun getComments(videoUrl: String): List<Comment> = withContext(Dispatchers.IO) {
        try {
            val info = CommentsInfo.getInfo(youtube, videoUrl)
            info.relatedItems.filterIsInstance<CommentsInfoItem>().map { it.toComment() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getCommentReplies(videoUrl: String, page: Page): List<Comment> = withContext(Dispatchers.IO) {
        try {
            val extractor = youtube.getCommentsExtractor(videoUrl)
            extractor.getPage(page).items
                .filterIsInstance<CommentsInfoItem>()
                .map { it.toComment() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Live search suggestions while the user types
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        try {
            youtube.suggestionExtractor.suggestionList(query).take(8)
        } catch (_: Exception) {
            emptyList()
        }
    }

    data class DownloadStreams(
        val videoUrl: String?,   // best muxed stream (single playable file)
        val videoHeight: Int,
        val audioUrl: String?    // best audio-only stream
    )

    // Streams suitable for saving as a single file (muxed video, or audio-only)
    suspend fun getDownloadStreams(videoUrl: String): DownloadStreams = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(youtube, videoUrl)
        val muxed = info.videoStreams.filter { !it.content.isNullOrEmpty() }.maxByOrNull { it.height }
        val audio = info.audioStreams.filter { !it.content.isNullOrEmpty() }.maxByOrNull { it.averageBitrate }
        DownloadStreams(
            videoUrl = muxed?.content,
            videoHeight = muxed?.height ?: 0,
            audioUrl = audio?.content
        )
    }

    data class RemotePlaylist(
        val name: String,
        val uploaderName: String,
        val thumbnailUrl: String,
        val videoCount: Long,
        val videos: List<VideoItem>,
        val nextPage: Page?
    )

    suspend fun getRemotePlaylist(playlistUrl: String): RemotePlaylist = withContext(Dispatchers.IO) {
        val info = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(youtube, playlistUrl)
        RemotePlaylist(
            name = info.name ?: "Playlist",
            uploaderName = try { info.uploaderName ?: "" } catch (_: Exception) { "" },
            thumbnailUrl = try { info.thumbnails.firstOrNull()?.url ?: "" } catch (_: Exception) { "" },
            videoCount = try { info.streamCount } catch (_: Exception) { -1L },
            videos = info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
            nextPage = info.nextPage
        )
    }

    suspend fun getRemotePlaylistNextPage(playlistUrl: String, page: Page): PagedResult = withContext(Dispatchers.IO) {
        try {
            val result = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getMoreItems(youtube, playlistUrl, page)
            PagedResult(
                videos = result.items.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
                nextPage = result.nextPage
            )
        } catch (_: Exception) {
            PagedResult(emptyList(), null)
        }
    }

    suspend fun getSponsorSegments(videoUrl: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        try {
            val videoId = videoUrl.substringAfter("v=").substringBefore("&").take(20)
            if (videoId.length < 11) return@withContext emptyList()
            val categories = URLEncoder.encode(
                """["sponsor","selfpromo","interaction","intro","outro","preview","music_offtopic"]""",
                "UTF-8"
            )
            val request = Request.Builder()
                .url("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$categories")
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val seg = obj.getJSONArray("segment")
                SponsorSegment(
                    category = obj.getString("category"),
                    startMs = (seg.getDouble(0) * 1000).toLong(),
                    endMs = (seg.getDouble(1) * 1000).toLong()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun StreamInfoItem.toVideoItem() = VideoItem(
        url = url,
        title = name,
        thumbnailUrl = thumbnails.firstOrNull()?.url ?: "",
        uploaderName = uploaderName ?: "Unknown",
        uploaderUrl = try { uploaderUrl ?: "" } catch (_: Exception) { "" },
        uploaderAvatarUrl = try { uploaderAvatars.lastOrNull()?.url ?: "" } catch (_: Exception) { "" },
        viewCount = viewCount,
        duration = duration,
        uploadedAgo = try { textualUploadDate ?: "" } catch (_: Exception) { "" },
        uploadedEpoch = try {
            uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: 0L
        } catch (_: Exception) { 0L }
    )
}
