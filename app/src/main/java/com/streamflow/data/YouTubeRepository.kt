package com.streamflow.data

import com.streamflow.data.model.VideoDetails
import com.streamflow.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import com.streamflow.data.model.SubtitleTrack
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class YouTubeRepository {

    private val youtube get() = NewPipe.getService(ServiceList.YouTube.serviceId)

    data class PagedResult(
        val videos: List<VideoItem>,
        val nextPage: Page?
    )

    suspend fun getTrending(country: String = "US"): PagedResult = withContext(Dispatchers.IO) {
        NewPipe.setPreferredContentCountry(ContentCountry(country))
        val kiosk = youtube.kioskList.getDefaultKioskExtractor()
        kiosk.fetchPage()
        val page = kiosk.initialPage
        PagedResult(
            videos = page.items.filterIsInstance<StreamInfoItem>()
                .filter { it.duration >= 0 }
                .map { it.toVideoItem() },
            nextPage = page.nextPage
        )
    }

    suspend fun getTrendingNextPage(nextPage: Page): PagedResult = withContext(Dispatchers.IO) {
        try {
            val kiosk = youtube.kioskList.getDefaultKioskExtractor()
            kiosk.fetchPage()
            val page = kiosk.getPage(nextPage)
            PagedResult(
                videos = page.items.filterIsInstance<StreamInfoItem>()
                    .filter { it.duration >= 0 }
                    .map { it.toVideoItem() },
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

    suspend fun getVideoDetails(videoUrl: String, qualityPref: String = "AUTO"): VideoDetails =
        withContext(Dispatchers.IO) {
            val info = StreamInfo.getInfo(youtube, videoUrl)

                val related = info.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toVideoItem() }

                // Prefer higher-quality video-only + audio (adaptive)
                val maxHeight = when (qualityPref) {
                    "1080P" -> 1080
                    "720P" -> 720
                    "480P" -> 480
                    "360P" -> 360
                    else -> Int.MAX_VALUE
                }

                val streamUrl: String
                val audioUrl: String?

                // 1. Prefer muxed progressive streams — most compatible with ExoPlayer
                val muxedStream = info.videoStreams
                    .filter { !it.content.isNullOrEmpty() && it.height <= maxHeight }
                    .maxByOrNull { it.height }

                val videoOnlyStream = info.videoOnlyStreams
                    .filter { !it.content.isNullOrEmpty() && it.height <= maxHeight }
                    .maxByOrNull { it.height }

                val audioStream = info.audioStreams
                    .filter { !it.content.isNullOrEmpty() }
                    .maxByOrNull { it.averageBitrate }

                // 2. Try HLS manifest URL (YouTube's most reliable format)
                val hlsUrl = info.hlsUrl

                when {
                    muxedStream != null -> {
                        streamUrl = muxedStream.content ?: throw Exception("Muxed stream URL is null")
                        audioUrl = null
                    }
                    videoOnlyStream != null && audioStream != null -> {
                        streamUrl = videoOnlyStream.content ?: throw Exception("Video-only stream URL is null")
                        audioUrl = audioStream.content
                    }
                    !hlsUrl.isNullOrEmpty() -> {
                        streamUrl = hlsUrl!!
                        audioUrl = null
                    }
                    audioStream != null -> {
                        streamUrl = audioStream.content ?: throw Exception("Audio stream URL is null")
                        audioUrl = null
                    }
                    else -> throw Exception("No playable stream found for: $videoUrl")
                }

                val subs = try {
                    info.subtitles.map { s ->
                        SubtitleTrack(
                            name = s.displayLanguageName ?: s.languageTag ?: "Unknown",
                            url  = s.content ?: "",
                            code = s.languageTag ?: ""
                        )
                    }.filter { it.url.isNotEmpty() }
                } catch (_: Exception) { emptyList() }

                VideoDetails(
                    url = videoUrl,
                    title = info.name,
                    uploaderName = info.uploaderName ?: "Unknown",
                    viewCount = info.viewCount,
                    likeCount = info.likeCount,
                    duration = info.duration,
                    description = info.description?.content ?: "",
                    streamUrl = streamUrl,
                    audioUrl = audioUrl,
                    thumbnailUrl = info.thumbnails.firstOrNull()?.url ?: "",
                    relatedVideos = related,
                    subtitles = subs
                )
        }

    data class ChannelResult(
        val name: String,
        val avatarUrl: String,
        val bannerUrl: String,
        val subscriberCount: Long,
        val description: String,
        val videos: List<VideoItem>,
        val nextPage: Page?
    )

    suspend fun getChannelVideos(channelUrl: String): ChannelResult = withContext(Dispatchers.IO) {
        val extractor = youtube.getChannelExtractor(channelUrl)
        extractor.fetchPage()

        val name            = try { extractor.name } catch (_: Exception) { "Channel" }
        val avatarUrl       = try { extractor.avatars.firstOrNull()?.url ?: "" } catch (_: Exception) { "" }
        val bannerUrl       = try { extractor.banners.firstOrNull()?.url ?: "" } catch (_: Exception) { "" }
        val subscriberCount = try { extractor.subscriberCount } catch (_: Exception) { -1L }
        val description     = try { extractor.description ?: "" } catch (_: Exception) { "" }

        var videos: List<VideoItem> = emptyList()
        var nextPage: Page? = null

        try {
            val tabs = extractor.tabs
            // Prefer the "Videos" tab — its content filters contain "videos"
            val videosTab = tabs.firstOrNull { tab ->
                try { tab.contentFilters.any { it.equals("videos", ignoreCase = true) } }
                catch (_: Exception) { false }
            } ?: tabs.firstOrNull()

            if (videosTab != null) {
                val tabExtractor = youtube.getChannelTabExtractor(videosTab)
                tabExtractor.fetchPage()
                val page = tabExtractor.initialPage
                videos   = page.items.filterIsInstance<StreamInfoItem>()
                    .filter { it.duration >= 0 }
                    .map { it.toVideoItem() }
                nextPage = page.nextPage
            }
        } catch (_: Exception) { /* tab API unavailable — metadata only */ }

        ChannelResult(
            name            = name,
            avatarUrl       = avatarUrl,
            bannerUrl       = bannerUrl,
            subscriberCount = subscriberCount,
            description     = description,
            videos          = videos,
            nextPage        = nextPage
        )
    }

    suspend fun getChannelNextPage(channelUrl: String, nextPage: Page): PagedResult = withContext(Dispatchers.IO) {
        try {
            val extractor = youtube.getChannelExtractor(channelUrl)
            extractor.fetchPage()
            val tabs = extractor.tabs
            val videosTab = tabs.firstOrNull { tab ->
                try { tab.contentFilters.any { it.equals("videos", ignoreCase = true) } }
                catch (_: Exception) { false }
            } ?: tabs.firstOrNull()

            if (videosTab != null) {
                val tabExtractor = youtube.getChannelTabExtractor(videosTab)
                tabExtractor.fetchPage()
                val page = tabExtractor.getPage(nextPage)
                return@withContext PagedResult(
                    videos = page.items.filterIsInstance<StreamInfoItem>()
                        .filter { it.duration >= 0 }
                        .map { it.toVideoItem() },
                    nextPage = page.nextPage
                )
            }
        } catch (_: Exception) {}
        PagedResult(emptyList(), null)
    }

    private fun StreamInfoItem.toVideoItem() = VideoItem(
        url          = url,
        title        = name,
        thumbnailUrl = thumbnails.firstOrNull()?.url ?: "",
        uploaderName = uploaderName ?: "Unknown",
        viewCount    = viewCount,
        duration     = duration,
        uploaderUrl  = uploaderUrl ?: "",
        uploadedAgo  = try { textualUploadDate ?: "" } catch (_: Exception) { "" }
    )
}
