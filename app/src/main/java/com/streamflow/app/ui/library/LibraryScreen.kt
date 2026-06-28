package com.streamflow.app.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streamflow.app.R
import com.streamflow.app.data.model.VideoItem
import com.streamflow.app.di.ServiceLocator
import com.streamflow.app.ui.components.VideoListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (String) -> Unit = {},
    onCheckForUpdates: () -> Unit = {}
) {
    val viewModel: LibraryViewModel = viewModel(
        factory = viewModelFactory { initializer { LibraryViewModel(ServiceLocator.database) } }
    )
    var selectedTab by remember { mutableIntStateOf(0) }
    val bookmarks by viewModel.bookmarks.collectAsState()
    val history by viewModel.history.collectAsState()

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
                }

                val videoItems = if (selectedTab == 0) bookmarks else history
                val emptyMessage = if (selectedTab == 0) {
                    stringResource(R.string.empty_bookmarks)
                } else {
                    stringResource(R.string.empty_history)
                }

                if (videoItems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(emptyMessage)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(12.dp)) {
                        items(videoItems, key = { it.url }) { video ->
                            VideoListItem(
                                video = video,
                                onClick = { onVideoClick(video) },
                                onUploaderClick = onChannelClick
                            )
                        }
                    }
                }
            }
        }
    }
}
