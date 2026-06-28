package com.streamflow.app.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val title: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val qualityLabel: String = "",
    val playbackSpeed: Float = 1f,
    val error: String? = null
)

/**
 * Connects the UI to [PlaybackService] via a [MediaController]. The controller implements
 * Media3's [Player] interface so it can be attached directly to a [PlayerView] for video
 * rendering while also driving playback that keeps running when the app is backgrounded.
 */
@OptIn(UnstableApi::class)
class PlayerController(private val appContext: Context) {

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private var pendingFuture: ListenableFuture<MediaController>? = null
    private var tickerJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) startTicker() else stopTicker()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _state.update { it.copy(playbackSpeed = playbackParameters.speed) }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = error.message) }
        }
    }

    fun play(url: String, title: String, qualityLabel: String) {
        withController { player ->
            val speed = _state.value.playbackSpeed
            _state.update { PlayerUiState(title = title, qualityLabel = qualityLabel, playbackSpeed = speed) }
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            player.setPlaybackSpeed(speed)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        withController { player -> player.setPlaybackSpeed(speed) }
    }

    fun attachTo(playerView: PlayerView) {
        withController { player -> playerView.player = player }
    }

    fun detachFrom(playerView: PlayerView) {
        if (playerView.player === controller) {
            playerView.player = null
        }
    }

    fun release() {
        stopTicker()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        pendingFuture = null
    }

    private fun withController(action: (Player) -> Unit) {
        controller?.let { action(it); return }

        val future = pendingFuture ?: SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
            .let { token -> MediaController.Builder(appContext, token).buildAsync() }
            .also { pendingFuture = it }

        future.addListener(
            {
                val built = controller ?: future.get().also {
                    controller = it
                    it.addListener(listener)
                }
                action(built)
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = mainScope.launch {
            while (isActive) {
                controller?.let { player ->
                    _state.update {
                        it.copy(
                            positionMs = player.currentPosition.coerceAtLeast(0),
                            durationMs = player.duration.coerceAtLeast(0)
                        )
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }
}
