package com.streamflow.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.streamflow.app.R
import com.streamflow.app.data.db.SubscriptionEntity
import com.streamflow.app.data.model.VideoItem
import com.streamflow.app.di.ServiceLocator
import com.streamflow.app.ui.components.VideoListContent
import com.streamflow.app.ui.components.VideoListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (String) -> Unit = {},
    onCheckForUpdates: () -> Unit = {}
) {
    val viewModel: LibraryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LibraryViewModel(ServiceLocator.database, ServiceLocator.repository) }
        }
    )
    var selectedTab by remember { mutableIntStateOf(0) }
    val bookmarks by viewModel.bookmarks.collectAsState()
    val history by viewModel.history.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val feedState by viewModel.feedState.collectAsState()

    LaunchedEffect(selectedTab) {
        if (selectedTab == 3) viewModel.loadFeed()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.tab_library)) },
                actions = {
                    IconButton(onClick = onCheckForUpdates) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = stringResource(R.string.check_for_updates))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.bookmarks)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.watch_history)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(stringResource(R.string.subscriptions)) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text(stringResource(R.string.feed)) }
                    )
                }

                when (selectedTab) {
                    0 -> VideoItemList(
                        videos = bookmarks,
                        emptyMessage = stringResource(R.string.empty_bookmarks),
                        onVideoClick = onVideoClick,
                        onChannelClick = onChannelClick
                    )
                    1 -> VideoItemList(
                        videos = history,
                        emptyMessage = stringResource(R.string.empty_history),
                        onVideoClick = onVideoClick,
                        onChannelClick = onChannelClick
                    )
                    2 -> SubscriptionList(
                        subscriptions = subscriptions,
                        onChannelClick = onChannelClick,
                        onUnsubscribe = viewModel::unsubscribe
                    )
                    else -> VideoListContent(
                        state = feedState,
                        onVideoClick = onVideoClick,
                        onRetry = viewModel::loadFeed,
                        onChannelClick = onChannelClick,
                        emptyMessage = stringResource(R.string.empty_feed)
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoItemList(
    videos: List<VideoItem>,
    emptyMessage: String,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (String) -> Unit
) {
    if (videos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            items(videos, key = { it.url }) { video ->
                VideoListItem(
                    video = video,
                    onClick = { onVideoClick(video) },
                    onUploaderClick = onChannelClick
                )
            }
        }
    }
}

@Composable
private fun SubscriptionList(
    subscriptions: List<SubscriptionEntity>,
    onChannelClick: (String) -> Unit,
    onUnsubscribe: (String) -> Unit
) {
    if (subscriptions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.empty_subscriptions))
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            items(subscriptions, key = { it.channelUrl }) { subscription ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChannelClick(subscription.channelUrl) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = subscription.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                    )
                    Text(
                        text = subscription.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 12.dp).weight(1f)
                    )
                    IconButton(onClick = { onUnsubscribe(subscription.channelUrl) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.unsubscribe))
                    }
                }
            }
        }
    }
}
