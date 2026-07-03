package com.streamflow.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streamflow.app.data.model.VideoItem
import com.streamflow.app.di.ServiceLocator
import com.streamflow.app.ui.components.VideoListContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onVideoClick: (VideoItem) -> Unit, onChannelClick: (String) -> Unit = {}) {
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory { initializer { HomeViewModel(ServiceLocator.repository) } }
    )
    val state by viewModel.state.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("StreamFlow") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                items(HomeViewModel.CATEGORIES) { category ->
                    FilterChip(
                        selected = category == selectedCategory,
                        onClick = { viewModel.selectCategory(category) },
                        label = { Text(category) }
                    )
                }
            }
            VideoListContent(
                state = state,
                onVideoClick = onVideoClick,
                onRetry = viewModel::load,
                onChannelClick = onChannelClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
