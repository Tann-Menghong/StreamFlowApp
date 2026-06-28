package com.streamflow.app.ui.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streamflow.app.R
import com.streamflow.app.data.model.VideoItem
import com.streamflow.app.di.ServiceLocator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onVideoClick: (VideoItem) -> Unit, onChannelClick: (String) -> Unit = {}) {
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory { initializer { SearchViewModel(ServiceLocator.repository) } }
    )
    val query by viewModel.query.collectAsState()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )
        }
    ) { padding ->
        SearchResultContent(
            state = state,
            onVideoClick = onVideoClick,
            onChannelClick = onChannelClick,
            onRetry = viewModel::retry,
            modifier = Modifier.padding(padding)
        )
    }
}
