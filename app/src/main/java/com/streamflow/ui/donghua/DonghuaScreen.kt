package com.streamflow.ui.donghua

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamflow.data.DonghuaCatalog
import com.streamflow.data.model.VideoItem
import com.streamflow.ui.components.EmptyState
import com.streamflow.ui.components.ErrorState
import com.streamflow.ui.components.ShimmerList
import com.streamflow.ui.components.VideoCard

/**
 * Donghua — a discovery tab over the ordinary YouTube pipeline.
 *
 * This was a WebView pointed at a third-party streaming site. Nothing on it was
 * a VideoItem, so nothing on it could be downloaded, favourited, queued,
 * resumed or added to Watch Later, and it could not use the app's own loading,
 * empty or error states. It also could not be fixed from here when the site
 * itself failed to play — which is what the black screen was.
 *
 * There is no playback code in this file. Cards call [onVideoClick] with a
 * url, the host routes that to the shared player, and everything downstream —
 * extraction, stream selection, the media session, PiP, background audio,
 * downloads, history — is the same code path Home and Search use. Donghua is a
 * source of videos, not a second way to play them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonghuaScreen(
    onVideoClick: (String) -> Unit,
    onChannelClick: ((String) -> Unit)? = null,
    vm: DonghuaViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    val genre by vm.genre.collectAsState()
    val resume by vm.continueWatching.collectAsState()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Donghua", fontWeight = FontWeight.ExtraBold) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Rounded.Refresh, "Refresh donghua",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Genre chips. Changing one re-runs every row's search rather than
            // filtering what is already loaded: the rows are searches, and
            // filtering five short lists client-side would mostly empty them.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DonghuaCatalog.genres.forEach { g ->
                    FilterChip(
                        selected = g.id == genre.id,
                        onClick = { vm.setGenre(g) },
                        label = { Text(g.label) }
                    )
                }
            }

            when (val s = state) {
                is DonghuaUiState.Loading -> ShimmerList()

                is DonghuaUiState.Error -> ErrorState(
                    error = s.kind,
                    onRetry = { vm.retry() }
                )

                is DonghuaUiState.Empty -> EmptyState(
                    icon = Icons.Rounded.LiveTv,
                    title = "Nothing to show right now",
                    subtitle = "No donghua came back for these searches. " +
                        "Try another genre, or check back later.",
                    actionLabel = "Try again",
                    onAction = { vm.retry() }
                )

                is DonghuaUiState.Success -> {
                    if (s.refreshing) {
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        if (resume.isNotEmpty()) {
                            item(key = "resume") {
                                DonghuaRow(
                                    title = "Continue watching",
                                    videos = resume,
                                    progressOf = vm::progressFor,
                                    onVideoClick = onVideoClick,
                                    onChannelClick = onChannelClick
                                )
                            }
                        }
                        items(s.sections, key = { it.source.id }) { section ->
                            DonghuaRow(
                                title = section.source.title,
                                videos = section.videos,
                                progressOf = vm::progressFor,
                                onVideoClick = onVideoClick,
                                onChannelClick = onChannelClick
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One titled shelf.
 *
 * VideoCard fills the width it is given, so each card is boxed to a fixed width
 * to make a horizontal shelf out of the same component the vertical feeds use —
 * rather than a second card implementation that would drift from it.
 */
@Composable
private fun DonghuaRow(
    title: String,
    videos: List<VideoItem>,
    progressOf: (String) -> Float,
    onVideoClick: (String) -> Unit,
    onChannelClick: ((String) -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${videos.size}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos, key = { it.url }) { video ->
                Box(Modifier.width(240.dp)) {
                    VideoCard(
                        video = video,
                        onClick = { onVideoClick(video.url) },
                        progressFraction = progressOf(video.url),
                        onChannelClick = onChannelClick
                    )
                }
            }
        }
    }
}
