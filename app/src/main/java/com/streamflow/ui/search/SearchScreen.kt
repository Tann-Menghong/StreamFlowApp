package com.streamflow.ui.search

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamflow.ui.components.ShimmerList
import com.streamflow.ui.components.VideoCard
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(onVideoClick: (String) -> Unit, vm: SearchViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    // Saveable: the typed query survives navigating away and back (the results
    // already do, via the ViewModel — losing just the text felt broken)
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    // Voice search via the system speech recognizer (follows the device language)
    val voiceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { spoken ->
                query = spoken
                vm.search(spoken)
            }
    }
    val voiceContext = androidx.compose.ui.platform.LocalContext.current
    fun startVoiceSearch() {
        try {
            voiceLauncher.launch(android.content.Intent(
                android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search YouTube")
            })
        } catch (_: Exception) {
            android.widget.Toast.makeText(voiceContext,
                "Voice search isn't available on this device",
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3
        }
    }
    // Keyed on isLoadingMore too: a tiny appended page can leave shouldLoadMore
    // stuck at true, and an effect keyed only on it would never re-fire —
    // pagination stalled until the user scrolled again
    val isLoadingMore = (state as? SearchUiState.Success)?.isLoadingMore == true
    LaunchedEffect(shouldLoadMore, isLoadingMore) {
        if (shouldLoadMore && !isLoadingMore) vm.loadMore()
    }

    // statusBarsPadding: keep the search bar below the clock/battery/wifi
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            // Ignore blank submits instead of firing an empty search
                            if (query.isNotBlank()) vm.search(query)
                            focusManager.clearFocus()
                        }),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Search YouTube…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        IconButton(onClick = { startVoiceSearch() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Mic, "Voice search",
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
            label = "search_state",
            modifier = Modifier.fillMaxSize()
        ) { s ->
            when (s) {
                is SearchUiState.Idle -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f), modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Search for videos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                    }
                }
                is SearchUiState.Loading -> ShimmerList()
                is SearchUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.retry() }) { Text("Retry") }
                    }
                }
                is SearchUiState.Success -> {
                    // Results animate in only once — scrolling back up used to
                    // replay the fade on every card that re-entered view
                    val animatedUrls = remember { mutableSetOf<String>() }
                    val durFilter by vm.durationFilter.collectAsState()
                    val dtFilter by vm.dateFilter.collectAsState()
                    val shown = s.videos.filter {
                        durFilter.matches(it.duration) && dtFilter.matches(it.uploadedEpoch)
                    }
                    Column(Modifier.fillMaxSize()) {
                    // Filter chips: duration + upload date (applied client-side)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var showDurMenu by remember { mutableStateOf(false) }
                        var showDateMenu by remember { mutableStateOf(false) }
                        Box {
                            FilterChip(
                                selected = durFilter != DurationFilter.ANY,
                                onClick = { showDurMenu = true },
                                label = { Text(if (durFilter == DurationFilter.ANY) "Length" else durFilter.label) }
                            )
                            DropdownMenu(expanded = showDurMenu, onDismissRequest = { showDurMenu = false }) {
                                DurationFilter.entries.forEach { f ->
                                    DropdownMenuItem(text = { Text(f.label) },
                                        onClick = { vm.durationFilter.value = f; showDurMenu = false })
                                }
                            }
                        }
                        Box {
                            FilterChip(
                                selected = dtFilter != DateFilter.ANY,
                                onClick = { showDateMenu = true },
                                label = { Text(if (dtFilter == DateFilter.ANY) "Upload date" else dtFilter.label) }
                            )
                            DropdownMenu(expanded = showDateMenu, onDismissRequest = { showDateMenu = false }) {
                                DateFilter.entries.forEach { f ->
                                    DropdownMenuItem(text = { Text(f.label) },
                                        onClick = { vm.dateFilter.value = f; showDateMenu = false })
                                }
                            }
                        }
                    }
                    if (shown.isEmpty() && s.videos.isNotEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No results match these filters",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else
                    LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    itemsIndexed(shown, key = { _, v -> v.url }) { index, video ->
                        var visible by remember { mutableStateOf(video.url in animatedUrls) }
                        LaunchedEffect(Unit) {
                            if (!visible) {
                                delay((index * 30L).coerceAtMost(240L))
                                animatedUrls.add(video.url); visible = true
                            }
                        }
                        AnimatedVisibility(visible, enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 6 }) {
                            VideoCard(video = video, onClick = { onVideoClick(video.url) })
                        }
                    }
                    if (s.isLoadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                            }
                        }
                    }
                }
                }
                }
            }
        }
    }
}
