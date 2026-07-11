package com.streamflow.ui.channel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflow.StreamFlowApp
import com.streamflow.data.YouTubeRepository
import com.streamflow.data.friendlyError
import com.streamflow.data.local.entity.SubscriptionEntity
import com.streamflow.data.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
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
    val error: String? = null,
    val availableTabs: List<String> = emptyList(),
    val selectedTab: String = "videos",
    val description: String = ""
)

class ChannelViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = YouTubeRepository()
    private val db = (app as StreamFlowApp).database

    private val _channel = MutableStateFlow(ChannelData())
    val channel: StateFlow<ChannelData> = _channel

    private val _currentUrl = MutableStateFlow("")
    val isSubscribed: StateFlow<Boolean> = _currentUrl
        .flatMapLatest { url -> if (url.isEmpty()) flowOf(false) else db.subscriptionDao().isSubscribed(url) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleSubscribe() {
        val url = _currentUrl.value
        val data = _channel.value
        if (url.isEmpty() || data.name.isEmpty()) return
        viewModelScope.launch {
            val dao = db.subscriptionDao()
            if (dao.isSubscribed(url).first()) {
                dao.delete(url)
            } else {
                dao.insert(SubscriptionEntity(channelUrl = url, name = data.name, avatarUrl = data.avatarUrl))
            }
        }
    }

    private var loadedUrl = ""
    private var currentTab = "videos"

    fun loadChannel(url: String, tab: String = "videos") {
        _currentUrl.value = url
        if (loadedUrl == url && currentTab == tab && _channel.value.name.isNotEmpty()) return
        loadedUrl = url
        currentTab = tab
        viewModelScope.launch {
            // Keep the header while switching tabs; full spinner only on first load
            val prev = _channel.value
            _channel.value = if (prev.name.isNotEmpty())
                prev.copy(videos = emptyList(), isLoading = true, selectedTab = tab)
            else ChannelData(isLoading = true, selectedTab = tab)
            try {
                val result = repo.getChannelInfo(url, tab)
                _channel.value = ChannelData(
                    name = result.name,
                    avatarUrl = result.avatarUrl,
                    bannerUrl = result.bannerUrl,
                    subscriberCount = result.subscriberCount,
                    videos = result.videos,
                    nextPage = result.nextPage,
                    availableTabs = result.availableTabs,
                    selectedTab = tab,
                    description = result.description
                )
            } catch (e: Exception) {
                _channel.value = ChannelData(error = friendlyError(e))
            }
        }
    }

    fun selectTab(tab: String) {
        if (tab == currentTab) return
        loadChannel(loadedUrl, tab)
    }

    fun loadMore() {
        val data = _channel.value
        val nextPage = data.nextPage ?: return
        if (data.isLoadingMore) return
        val url = loadedUrl
        val tab = currentTab
        viewModelScope.launch {
            _channel.value = data.copy(isLoadingMore = true)
            try {
                val result = repo.getChannelNextPage(url, nextPage, tab)
                if (currentTab != tab) return@launch // tab switched mid-load
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
