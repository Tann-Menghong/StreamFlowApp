package com.streamflow.data

import com.streamflow.data.model.Comment
import com.streamflow.data.model.SponsorSegment
import com.streamflow.data.model.SubtitleTrack
import com.streamflow.data.model.VideoChapter
import com.streamflow.data.model.VideoDetails
import com.streamflow.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
    private val httpClient = OkHttpClient()

    data class PagedResult(
        val videos: List<VideoItem>,
        val nextPage: Page?
    )

    data class ChannelResult(
        val name: String,
        val avatarUrl: String,
        val bannerUrl: String,
        val subscriberCount: Long,
        val videos: List<VideoItem>,
        val nextPage: Page?
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

            val maxHeight = maxHeightOverride ?: when (qualityPref) {
                "1080P" -> 1080
                "720P"  -> 720
                "480P"  -> 480
                "360P"  -> 360
                else    -> Int.MAX_VALUE
            }

            val streamUrl: String
            val audioUrl: String?

            // All heights available across muxed + video-only streams (for the quality menu)
            val availableQualities = (
                info.videoStreams.mapNotNull { s -> s.height.takeIf { it > 0 && !s.content.isNullOrEmpty() } } +
                info.videoOnlyStreams.mapNotNull { s -> s.height.takeIf { it > 0 && !s.content.isNullOrEmpty() } }
            ).distinct().sortedDescending()

            val muxedStream = info.videoStreams
                .filter { !it.content.isNullOrEmpty() && it.height <= maxHeight }
                .maxByOrNull { it.height }

            val videoOnlyStream = info.videoOnlyStreams
                .filter { !it.content.isNullOrEmpty() && it.height <= maxHeight }
                .maxByOrNull { it.height }

            val audioStream = info.audioStreams
                .filter { !it.content.isNullOrEmpty() }
                .maxByOrNull { it.averageBitrate }

            val hlsUrl = info.hlsUrl
            val dashUrl = try { info.dashMpdUrl } catch (_: Exception) { null }
            val isLive = try {
                info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
            } catch (_: Exception) { false }

            val currentQuality: Int
            when {
                // Live streams: play the HLS (or DASH) manifest; ExoPlayer handles quality adaptively
                isLive && (!hlsUrl.isNullOrEmpty() || !dashUrl.isNullOrEmpty()) -> {
                    streamUrl = if (!hlsUrl.isNullOrEmpty()) hlsUrl!! else dashUrl!!
                    audioUrl = null
                    currentQuality = 0
                }
                videoOnlyStream != null && audioStream != null &&
                    videoOnlyStream.height > (muxedStream?.height ?: 0) -> {
                    streamUrl = videoOnlyStream.content ?: throw Exception("Video-only stream URL is null")
                    audioUrl = audioStream.content
                    currentQuality = videoOnlyStream.height
                }
                muxedStream != null -> {
                    streamUrl = muxedStream.content ?: throw Exception("Muxed stream URL is null")
                    audioUrl = null
                    currentQuality = muxedStream.height
                }
                videoOnlyStream != null && audioStream != null -> {
                    streamUrl = videoOnlyStream.content ?: throw Exception("Video-only stream URL is null")
                    audioUrl = audioStream.content
                    currentQuality = videoOnlyStream.height
                }
                !hlsUrl.isNullOrEmpty() -> {
                    streamUrl = hlsUrl!!
                    audioUrl = null
                    currentQuality = 0
                }
                audioStream != null -> {
                    streamUrl = audioStream.content ?: throw Exception("Audio stream URL is null")
                    audioUrl = null
                    currentQuality = 0
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
                isLive = isLive
            )
            VideoDetailsCache.put(cacheKey, details)
            details
        }

    suspend fun getChannelInfo(channelUrl: String): ChannelResult = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(youtube, channelUrl)

        val avatarUrl = try { info.avatars.lastOrNull()?.url ?: "" } catch (_: Exception) { "" }
        val bannerUrl = try { info.banners.lastOrNull()?.url ?: "" } catch (_: Exception) { "" }
        val subscriberCount = try { info.subscriberCount } catch (_: Exception) { -1L }

        var videos = emptyList<VideoItem>()
        var nextPage: Page? = null

        try {
            val videoTab = info.tabs.firstOrNull { tab ->
                tab.contentFilters.any { it.contains("videos", ignoreCase = true) }
            } ?: info.tabs.firstOrNull()

            if (videoTab != null) {
                val tabInfo = ChannelTabInfo.getInfo(youtube, videoTab)
                videos = tabInfo.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() }
                nextPage = tabInfo.nextPage
            }
        } catch (_: Exception) {}

        ChannelResult(
            name = info.name ?: "",
            avatarUrl = avatarUrl,
            bannerUrl = bannerUrl,
            subscriberCount = subscriberCount,
            videos = videos,
            nextPage = nextPage
        )
    }

    suspend fun getChannelNextPage(channelUrl: String, nextPage: Page): PagedResult = withContext(Dispatchers.IO) {
        try {
            val info = ChannelInfo.getInfo(youtube, channelUrl)
            val videoTab = info.tabs.firstOrNull { tab ->
                tab.contentFilters.any { it.contains("videos", ignoreCase = true) }
            } ?: info.tabs.firstOrNull() ?: return@withContext PagedResult(emptyList(), null)

            val page = ChannelTabInfo.getMoreItems(youtube, videoTab, nextPage)
            PagedResult(
                videos = page.items.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
                nextPage = page.nextPage
            )
        } catch (e: Exception) {
            e.printStackTrace()
            PagedResult(emptyList(), null)
        }
    }

    suspend fun getComments(videoUrl: String): List<Comment> = withContext(Dispatchers.IO) {
        try {
            val info = CommentsInfo.getInfo(youtube, videoUrl)
            info.relatedItems.filterIsInstance<CommentsInfoItem>().map { item ->
                Comment(
                    author = item.uploaderName ?: "",
                    text = try { item.commentText?.content ?: "" } catch (_: Exception) { item.commentText.toString() },
                    likeCount = item.likeCount.coerceAtLeast(0).toLong(),
                    avatarUrl = try { item.thumbnails.firstOrNull()?.url ?: "" } catch (_: Exception) { "" },
                    isOwnerComment = false,
                    publishedTime = try { item.textualUploadDate ?: "" } catch (_: Exception) { "" },
                    replyCount = try { item.replyCount } catch (_: Exception) { 0 }
                )
            }
        } catch (_: Exception) {
            emptyList()
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
