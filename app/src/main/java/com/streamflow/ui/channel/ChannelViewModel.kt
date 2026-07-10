package com.streamflow.ui.channel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.StreamFlowApp
import com.streamflow.data.YouTubeRepository
import com.streamflow.data.friendlyError
import com.streamflow.data.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

data class ChannelData(
    val name: String = "",
    val avatarUrl: String = "",
    val bannerUrl: String = "",
    val subscriberCount: Long = -1L,
    val videos: List<VideoItem> = emptyList(),
    val nextPage: Page? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null
)

class ChannelViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = YouTubeRepository()

    private val _channel = MutableStateFlow(ChannelData())
    val channel: StateFlow<ChannelData> = _channel

    private var loadedUrl = ""

    fun loadChannel(url: String) {
        if (loadedUrl == url && _channel.value.name.isNotEmpty()) return
        loadedUrl = url
        viewModelScope.launch {
            _channel.value = ChannelData(isLoading = true)
            try {
                val result = repo.getChannelInfo(url)
                _channel.value = ChannelData(
                    name = result.name,
                    avatarUrl = result.avatarUrl,
                    bannerUrl = result.bannerUrl,
                    subscriberCount = result.subscriberCount,
                    videos = result.videos,
                    nextPage = result.nextPage
                )
            } catch (e: Exception) {
                _channel.value = ChannelData(error = friendlyError(e))
            }
        }
    }

    fun loadMore() {
        val data = _channel.value
        val nextPage = data.nextPage ?: return
        if (data.isLoadingMore) return
        val url = loadedUrl
        viewModelScope.launch {
            _channel.value = data.copy(isLoadingMore = true)
            try {
                val result = repo.getChannelNextPage(url, nextPage)
                _channel.value = _channel.value.copy(
                    videos = _channel.value.videos + result.videos,
                    nextPage = result.nextPage,
                    isLoadingMore = false
                )
            } catch (_: Exception) {
                _channel.value = _channel.value.copy(isLoadingMore = false)
            }
        }
    }
}
