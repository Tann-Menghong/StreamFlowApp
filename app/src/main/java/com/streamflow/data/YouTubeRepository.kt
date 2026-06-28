package com.streamflow.data

import com.streamflow.data.model.VideoDetails
import com.streamflow.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class YouTubeRepository {

    private val youtube get() = NewPipe.getService(ServiceList.YouTube.serviceId)

    data class PagedResult(
        val videos: List<VideoItem>,
        val nextPage: Page?
    )

    suspend fun getTrending(): PagedResult = withContext(Dispatchers.IO) {
        try {
            val kiosk = youtube.kioskList.getDefaultKioskExtractor()
            kiosk.fetchPage()
            val page = kiosk.initialPage
            PagedResult(
                videos = page.items.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
                nextPage = page.nextPage
            )
        } catch (e: Exception) {
            e.printStackTrace()
            PagedResult(emptyList(), null)
        }
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
        try {
            val extractor = youtube.getSearchExtractor(query)
            extractor.fetchPage()
            val page = extractor.initialPage
            PagedResult(
                videos = page.items.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
                nextPage = page.nextPage
            )
        } catch (e: Exception) {
            e.printStackTrace()
            PagedResult(emptyList(), null)
        }
    }

    suspend fun searchNextPage(query: String, nextPage: Page): PagedResult = withContext(Dispatchers.IO) {
        try {
            val extractor = youtube.getSearchExtractor(query)
            extractor.fetchPage()
            val page = extractor.getPage(nextPage)
            PagedResult(
                videos = page.items.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
                nextPage = page.nextPage
            )
        } catch (e: Exception) {
            e.printStackTrace()
            PagedResult(emptyList(), null)
        }
    }

    suspend fun getVideoDetails(videoUrl: String, qualityPref: String = "AUTO"): VideoDetails? =
        withContext(Dispatchers.IO) {
            try {
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

                val videoOnlyStream = info.videoOnlyStreams
                    .filter { !it.getUrl().isNullOrEmpty() && it.height <= maxHeight }
                    .maxByOrNull { it.height }

                val audioStream = info.audioStreams
                    .filter { !it.getUrl().isNullOrEmpty() }
                    .maxByOrNull { it.averageBitrate }

                // Fall back to muxed if no video-only streams
                val muxedStream = info.videoStreams
                    .filter { !it.getUrl().isNullOrEmpty() && it.height <= maxHeight }
                    .maxByOrNull { it.height }

                val streamUrl: String
                val audioUrl: String?

                if (videoOnlyStream != null && audioStream != null) {
                    streamUrl = videoOnlyStream.getUrl()!!
                    audioUrl = audioStream.getUrl()
                } else if (muxedStream != null) {
                    streamUrl = muxedStream.getUrl()!!
                    audioUrl = null
                } else if (audioStream != null) {
                    streamUrl = audioStream.getUrl()!!
                    audioUrl = null
                } else {
                    return@withContext null
                }

                VideoDetails(
                    url = videoUrl,
                    title = info.name,
                    uploaderName = info.uploaderName ?: "Unknown",
                    viewCount = info.viewCount,
                    likeCount = info.likeCount,
                    description = info.description?.content ?: "",
                    streamUrl = streamUrl,
                    audioUrl = audioUrl,
                    thumbnailUrl = info.thumbnails.firstOrNull()?.url ?: "",
                    relatedVideos = related
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    private fun StreamInfoItem.toVideoItem() = VideoItem(
        url = url,
        title = name,
        thumbnailUrl = thumbnails.firstOrNull()?.url ?: "",
        uploaderName = uploaderName ?: "Unknown",
        viewCount = viewCount,
        duration = duration
    )
}
