package com.streamflow.ui.pdtv

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.compose.AsyncImage
import com.streamflow.data.OkHttpDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class PdChannel(val name: String, val streamUrl: String, val logoUrl: String)

/**
 * Native Live-TV tab for pdtvhd.com. The site's own web player (Clappr/hls.js
 * inside a Blogger page) is unreliable in a WebView, but its channel list is
 * just direct HLS URLs — so we parse the list out of the page and play the
 * streams in ExoPlayer instead: instant start, no ads, no web player at all.
 */
class PdTvViewModel(app: Application) : AndroidViewModel(app) {
    private val sitePrefs = app.getSharedPreferences("pdtv_prefs", Context.MODE_PRIVATE)

    private val _channels = MutableStateFlow<List<PdChannel>>(emptyList())
    val channels: StateFlow<List<PdChannel>> = _channels

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    var lastChannelUrl: String
        get() = sitePrefs.getString("last_channel", "") ?: ""
        set(v) { sitePrefs.edit().putString("last_channel", v).apply() }

    init {
        // Cached list renders instantly; the network refresh updates it after
        loadCached()
        refresh()
    }

    private fun loadCached() {
        try {
            val arr = JSONArray(sitePrefs.getString("channels_json", "[]") ?: "[]")
            _channels.value = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                PdChannel(o.optString("n"), o.optString("u"), o.optString("l"))
                    .takeIf { it.streamUrl.isNotEmpty() }
            }
        } catch (_: Exception) {}
    }

    fun refresh() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val req = okhttp3.Request.Builder().url("https://www.pdtvhd.com/").build()
                    val html = OkHttpDownloader.instance.client.newCall(req).execute()
                        .use { it.body?.string() ?: "" }
                    CHANNEL_RE.findAll(html).map { m ->
                        PdChannel(
                            name = m.groupValues[1].trim(),
                            // The site's list has typos like "hhttps://" — repair
                            streamUrl = m.groupValues[2].trim()
                                .replace(Regex("^h+ttps://"), "https://"),
                            logoUrl = m.groupValues[3].trim()
                        )
                    }.filter { it.streamUrl.startsWith("http") && it.name.isNotEmpty() }
                        .distinctBy { it.name + "|" + it.streamUrl }
                        .toList()
                }
                if (parsed.isNotEmpty()) {
                    _channels.value = parsed
                    val arr = JSONArray()
                    parsed.forEach { c ->
                        arr.put(JSONObject().apply {
                            put("n", c.name); put("u", c.streamUrl); put("l", c.logoUrl)
                        })
                    }
                    sitePrefs.edit().putString("channels_json", arr.toString()).apply()
                } else if (_channels.value.isEmpty()) {
                    _error.value = "No channels found — pull to retry"
                }
            } catch (e: Exception) {
                if (_channels.value.isEmpty()) _error.value = "Couldn't load channels — check your connection"
            } finally {
                _loading.value = false
            }
        }
    }

    companion object {
        private val CHANNEL_RE =
            Regex("""\{\s*n:\s*"([^"]*)"\s*,\s*u:\s*"([^"]*)"\s*,\s*l:\s*"([^"]*)"\s*\}""")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdTvScreen(onFullscreenChange: (Boolean) -> Unit = {}) {
    val vm: PdTvViewModel = viewModel()
    val context = LocalContext.current
    val activity = context as? Activity
    val channels by vm.channels.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    var currentUrl by rememberSaveable { mutableStateOf(vm.lastChannelUrl) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }

    // One ExoPlayer for the tab's lifetime. Shares the app's warm OkHttp pool;
    // Referer mimics the site in case a stream host checks it.
    val exo = remember {
        val dsf = OkHttpDataSource.Factory(OkHttpDownloader.instance.client)
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.pdtvhd.com/"))
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsf))
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .build()
            .apply { playWhenReady = true }
    }
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                playerError = "This channel isn't responding — try another one"
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) playerError = null
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.release()
            onFullscreenChange(false)
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Live TV keeps the screen awake while the tab is open
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { }
    }

    fun play(url: String) {
        playerError = null
        currentUrl = url
        vm.lastChannelUrl = url
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.play()
    }

    // Auto-start: last watched channel if it still exists, else the first one
    LaunchedEffect(channels) {
        if (channels.isNotEmpty() && exo.currentMediaItem == null) {
            val url = channels.firstOrNull { it.streamUrl == currentUrl }?.streamUrl
                ?: channels.first().streamUrl
            play(url)
        }
    }

    fun setFullscreen(on: Boolean) {
        isFullscreen = on
        onFullscreenChange(on)
        activity?.let { act ->
            if (on) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                WindowCompat.getInsetsController(act.window, act.window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler(enabled = isFullscreen) { setFullscreen(false) }

    val playerSurface: @Composable (Modifier) -> Unit = { mod ->
        Box(mod.background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        player = exo
                        useController = true
                        controllerShowTimeoutMs = 2500
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            playerError?.let { msg ->
                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)),
                    contentAlignment = Alignment.Center) {
                    Text(msg, color = Color.White, fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
            IconButton(
                onClick = { setFullscreen(!isFullscreen) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
            ) {
                Icon(
                    if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    contentDescription = "Fullscreen", tint = Color.White
                )
            }
        }
    }

    if (isFullscreen) {
        playerSurface(Modifier.fillMaxSize())
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDTV Live", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh channels")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            playerSurface(Modifier.fillMaxWidth().aspectRatio(16f / 9f))

            if (loading && channels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (error != null && channels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { vm.refresh() }) { Text("Retry") }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(channels, key = { it.name + "|" + it.streamUrl }) { ch ->
                        val selected = ch.streamUrl == currentUrl
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                                .then(
                                    if (selected) Modifier.border(
                                        2.dp, MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(12.dp)
                                    ) else Modifier
                                )
                                .clickable { play(ch.streamUrl) }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1.5f)) {
                                AsyncImage(
                                    model = ch.logoUrl,
                                    contentDescription = ch.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(4.dp)
                                )
                                Text(
                                    "LIVE", color = Color.White, fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(Color(0xFFE53935), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                ch.name, fontSize = 11.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
