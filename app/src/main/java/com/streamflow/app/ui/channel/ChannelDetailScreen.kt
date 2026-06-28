package com.streamflow.app.ui.channel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.streamflow.app.data.model.ChannelDetails
import com.streamflow.app.data.model.VideoItem
import com.streamflow.app.di.ServiceLocator
import com.streamflow.app.ui.components.UiState
import com.streamflow.app.ui.components.VideoListItem
import com.streamflow.app.ui.components.formatSubscriberCount

@Composable
fun ChannelDetailScreen(
    channelUrl: String,
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit
) {
    val viewModel: ChannelDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ChannelDetailViewModel(ServiceLocator.repository, channelUrl) }
        }
    )
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state as? UiState.Success)?.data?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is UiState.Error -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(current.message)
                    Button(onClick = viewModel::load, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
                is UiState.Success -> ChannelBody(channel = current.data, onVideoClick = onVideoClick)
            }
        }
    }
}

@Composable
private fun ChannelBody(channel: ChannelDetails, onVideoClick: (VideoItem) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (channel.bannerUrl != null) {
            item {
                AsyncImage(
                    model = channel.bannerUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f)
                )
            }
        }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = channel.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(CircleShape)
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(channel.name, style = MaterialTheme.typography.titleMedium)
                        val subscribers = formatSubscriberCount(channel.subscriberCount)
                        if (subscribers.isNotBlank()) {
                            Text(
                                text = subscribers,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                if (channel.description.isNotBlank()) {
                    Text(
                        text = channel.description,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }

        if (channel.videos.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.channel_videos),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(channel.videos, key = { it.url }) { video ->
                VideoListItem(
                    video = video,
                    onClick = { onVideoClick(video) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}
