package com.streamflow.app.data.repository

import com.streamflow.app.data.model.PlaybackSource
import com.streamflow.app.data.model.VideoDetails
import com.streamflow.app.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Wraps NewPipeExtractor: the same extraction approach the NewPipe app uses to read YouTube's
 * public pages directly instead of going through the official (ad-injecting) API/player.
 */
class YoutubeRepository {

    private val service = ServiceList.YouTube

    suspend fun getTrending(): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val kioskList = service.kioskList
            val extractor = kioskList.getDefaultKioskExtractor(null, NewPipe.getPreferredLocalization())
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideoItem() }
        }
    }

    suspend fun search(query: String): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val searchExtractor = service.getSearchExtractor(query)
            searchExtractor.fetchPage()
            val searchInfo = SearchInfo.getInfo(searchExtractor)
            searchInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideoItem() }
        }
    }

    suspend fun getVideoDetails(url: String): Result<VideoDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val info = StreamInfo.getInfo(service, url)

            val muxed = info.videoStreams.orEmpty()
            val audio = info.audioStreams.orEmpty()

            val playbackOptions = buildPlaybackOptions(muxed, audio)
            val bestAudio = pickBestAudio(audio)

            VideoDetails(
                url = info.url,
                title = info.name,
                uploaderName = info.uploaderName ?: "",
                uploaderUrl = info.uploaderUrl,
                thumbnailUrl = bestThumbnail(info.thumbnails),
                viewCount = info.viewCount,
                textualUploadDate = info.textualUploadDate,
                durationSeconds = info.duration,
                description = info.description?.content ?: "",
                relatedVideos = info.relatedItems.orEmpty()
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toVideoItem() },
                playbackOptions = playbackOptions,
                bestAudioUrl = bestAudio?.content
            )
        }
    }

    private fun buildPlaybackOptions(
        muxed: List<VideoStream>,
        audio: List<AudioStream>
    ): List<PlaybackSource> {
        val options = mutableListOf<PlaybackSource>()

        muxed.sortedByDescending { resolutionRank(it.resolution) }
            .forEach { options += PlaybackSource.Muxed(it.content, it.resolution ?: "Auto") }

        pickBestAudio(audio)?.let { options += PlaybackSource.AudioOnly(it.content, "Audio only") }

        return options
    }

    private fun pickBestAudio(audio: List<AudioStream>): AudioStream? =
        audio.maxByOrNull { it.averageBitrate }

    private fun resolutionRank(resolution: String?): Int =
        resolution?.filter { it.isDigit() }?.toIntOrNull() ?: 0

    private fun bestThumbnail(thumbnails: List<Image>?): String? =
        thumbnails?.maxByOrNull { it.height }?.url

    private fun StreamInfoItem.toVideoItem(): VideoItem = VideoItem(
        url = url,
        title = name,
        thumbnailUrl = bestThumbnail(thumbnails),
        uploaderName = uploaderName ?: "",
        durationSeconds = duration,
        viewCount = viewCount,
        textualUploadDate = textualUploadDate,
        isShort = isShortFormContent
    )
}
