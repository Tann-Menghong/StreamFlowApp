package com.streamflow.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.streamflow.ui.components.VideoCard
import com.streamflow.ui.components.formatViews

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelUrl: String,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    vm: ChannelViewModel = viewModel()
) {
    LaunchedEffect(channelUrl) { vm.loadChannel(channelUrl) }
    val state by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Channel", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        when (val s = state) {
            is ChannelUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is ChannelUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.loadChannel(channelUrl) }) { Text("Retry") }
                }
            }

            is ChannelUiState.Ready -> {
                val ch = s.channel
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        // Channel header
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (ch.avatarUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = ch.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(88.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            } else {
                                Box(
                                    Modifier.size(88.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, null,
                                        modifier = Modifier.size(44.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(ch.name, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground)
                            if (ch.subscriberCount > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text("${formatViews(ch.subscriberCount)} subscribers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (ch.description.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                var expanded by remember { mutableStateOf(false) }
                                Text(
                                    ch.description, fontSize = 13.sp, lineHeight = 18.sp,
                                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (!expanded) {
                                    TextButton(onClick = { expanded = true },
                                        contentPadding = PaddingValues(0.dp)) {
                                        Text("Show more", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
                        Spacer(Modifier.height(8.dp))
                        Text("Videos", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }

                    if (ch.videos.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 40.dp),
                                contentAlignment = Alignment.Center) {
                                Text("No videos found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(ch.videos, key = { it.url }) { video ->
                            Box(Modifier.padding(horizontal = 14.dp)) {
                                VideoCard(video = video, onClick = { onVideoClick(video.url) })
                            }
                        }
                    }
                }
            }
        }
    }
}
