package com.streamflow.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.streamflow.app.R
import com.streamflow.app.data.model.VideoItem

@Composable
fun VideoListContent(
    state: UiState<List<VideoItem>>,
    onVideoClick: (VideoItem) -> Unit,
    onRetry: () -> Unit,
    emptyMessage: String? = null,
    modifier: Modifier = Modifier
) {
    when (state) {
        is UiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is UiState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message)
                Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.retry))
                }
            }
        }

        is UiState.Success -> {
            if (state.data.isEmpty() && emptyMessage != null) {
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(emptyMessage, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.data, key = { it.url }) { video ->
                        VideoListItem(video = video, onClick = { onVideoClick(video) })
                    }
                }
            }
        }
    }
}
