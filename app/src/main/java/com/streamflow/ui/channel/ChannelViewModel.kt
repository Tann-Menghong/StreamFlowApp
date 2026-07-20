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
    val description: String = "",
    val playlists: List<YouTubeRepository.PlaylistItem> = emptyList()
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

    // Bumped on every loadChannel() call so an in-flight request from a tab (or
    // channel) the user has since switched away from can't win a race and clobber
    // the tab/channel they're actually looking at with its stale response.
    private var loadGeneration = 0

    fun loadChannel(url: String, tab: String = "videos") {
        _currentUrl.value = url
        if (loadedUrl == url && currentTab == tab && _channel.value.name.isNotEmpty()) return
        loadedUrl = url
        currentTab = tab
        val gen = ++loadGeneration
        viewModelScope.launch {
            // Keep the header while switching tabs; full spinner only on first load
            val prev = _channel.value
            _channel.value = if (prev.name.isNotEmpty())
                prev.copy(videos = emptyList(), isLoading = true, selectedTab = tab)
            else ChannelData(isLoading = true, selectedTab = tab)
            try {
                val result = repo.getChannelInfo(url, tab)
                if (gen != loadGeneration) return@launch // superseded by a newer tab/channel switch
                pushChannelShortcut(url, result.name)
                _channel.value = ChannelData(
                    name = result.name,
                    avatarUrl = result.avatarUrl,
                    bannerUrl = result.bannerUrl,
                    subscriberCount = result.subscriberCount,
                    videos = result.videos,
                    nextPage = result.nextPage,
                    availableTabs = result.availableTabs,
                    selectedTab = tab,
                    description = result.description,
                    playlists = result.playlists
                )
            } catch (e: Exception) {
                if (gen != loadGeneration) return@launch
                _channel.value = ChannelData(error = friendlyError(e))
            }
        }
    }

    fun selectTab(tab: String) {
        if (tab == currentTab) return
        loadChannel(loadedUrl, tab)
    }

    // Long-press the launcher icon → jump straight to recently visited channels.
    // pushDynamicShortcut handles the ranking/eviction against the app's
    // static shortcuts automatically.
    private fun pushChannelShortcut(channelUrl: String, name: String) {
        if (name.isBlank()) return
        try {
            val ctx = getApplication<Application>()
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(channelUrl), ctx, com.streamflow.MainActivity::class.java)
            val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(
                ctx, "channel_${channelUrl.hashCode()}")
                .setShortLabel(name.take(24))
                .setLongLabel(name.take(48))
                .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(
                    ctx, com.streamflow.R.mipmap.ic_launcher))
                .setIntent(intent)
                .build()
            androidx.core.content.pm.ShortcutManagerCompat.pushDynamicShortcut(ctx, shortcut)
        } catch (_: Exception) {}
    }

    // Synchronous re-entrancy guard: the scroll-driven trigger can fire twice
    // before the coroutine flips data.isLoadingMore, so relying on that state
    // snapshot alone let two identical page fetches run at once (double network,
    // and only the dedupe at append time saved the list from a crash).
    private var loadingMorePage = false

    fun loadMore() {
        val data = _channel.value
        val nextPage = data.nextPage ?: return
        if (data.isLoadingMore || loadingMorePage) return
        loadingMorePage = true
        val url = loadedUrl
        val tab = currentTab
        val gen = loadGeneration
        viewModelScope.launch {
            _channel.value = data.copy(isLoadingMore = true)
            try {
                val result = repo.getChannelNextPage(url, nextPage, tab)
                if (gen != loadGeneration) return@launch // tab/channel switched mid-load
                // Dedupe at append time — NewPipe pagination can return the same
                // video across two pages, and the LazyColumn's key = { it.url }
                // crashes on a duplicate key if that happens unguarded.
                val existingUrls = _channel.value.videos.mapTo(HashSet()) { it.url }
                _channel.value = _channel.value.copy(
                    videos = _channel.value.videos + result.videos.filter { it.url !in existingUrls },
                    playlists = (_channel.value.playlists + result.playlists).distinctBy { it.url },
                    nextPage = result.nextPage,
                    isLoadingMore = false
                )
            } catch (_: Exception) {
                if (gen != loadGeneration) return@launch
                _channel.value = _channel.value.copy(isLoadingMore = false)
            } finally {
                // Always release the guard — including on the stale-generation
                // early returns above — or a tab/channel switch mid-load would
                // pin it true and block all future pagination on this VM.
                loadingMorePage = false
            }
        }
    }
}
