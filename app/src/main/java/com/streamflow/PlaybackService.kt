package com.streamflow

import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Downloaded files and other direct streams aren't YouTube URLs — running them
// through the extractor during Bluetooth playback resumption would just throw
private fun isLocalOrDirectUrl(url: String): Boolean {
    val lower = url.lowercase()
    // Kept in sync with PlayerViewModel.isDirectStream: a /hls/ or /stream/ URL is
    // a direct media link too, and skipping it here meant Bluetooth resume would
    // hand it to the YouTube extractor (which throws) and abort resumption.
    return lower.startsWith("file://") || lower.startsWith("content://") ||
        lower.contains(".m3u8") || lower.contains(".mp4") ||
        lower.contains(".m4a") || lower.contains(".webm") ||
        lower.contains("/hls/") || lower.contains("/stream/")
}

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Custom "Next" button for the media notification / lock screen. The app plays
    // one MediaItem at a time (the queue + related auto-play are app-managed, not
    // an ExoPlayer playlist), so the native next button never appears — this
    // command reproduces the on-screen "next" behaviour: play the queued video,
    // else the current video's first related video.
    private val nextCommand = SessionCommand(CUSTOM_NEXT, android.os.Bundle.EMPTY)
    private var advancing = false // guard against double-taps while resolving
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    private var boostGainMb = 0
    private var audioSessionId = 0
    private var equalizer: android.media.audiofx.Equalizer? = null
    private var eqPresetName = "OFF"
    private var customBands: List<Int> = emptyList() // millibels, for CUSTOM

    // Apply the user's chosen equalizer preset (matched by name — indices vary
    // per device), or the hand-tuned band levels when the preset is "CUSTOM"
    private fun applyEq() {
        try {
            equalizer?.release()
            equalizer = null
            if (eqPresetName == "OFF" || audioSessionId == 0) return
            val eq = android.media.audiofx.Equalizer(0, audioSessionId)
            if (eqPresetName == "CUSTOM") {
                val range = eq.bandLevelRange // [min, max] millibels
                for (i in 0 until eq.numberOfBands) {
                    val lvl = (customBands.getOrNull(i) ?: 0)
                        .coerceIn(range[0].toInt(), range[1].toInt())
                    eq.setBandLevel(i.toShort(), lvl.toShort())
                }
            } else {
                val idx = (0 until eq.numberOfPresets).firstOrNull {
                    eq.getPresetName(it.toShort()).equals(eqPresetName, ignoreCase = true)
                }
                if (idx == null) { eq.release(); return }
                eq.usePreset(idx.toShort())
            }
            eq.enabled = true
            equalizer = eq
        } catch (_: Exception) { equalizer = null }
    }

    // Amplify quiet videos beyond 100% via LoudnessEnhancer (gain in millibels)
    private fun applyBoost() {
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            if (boostGainMb > 0 && audioSessionId != 0) {
                loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId).apply {
                    setTargetGain(boostGainMb)
                    enabled = true
                }
            }
        } catch (_: Exception) {
            loudnessEnhancer = null
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Reuse the app-wide client (same UA headers) so media requests share the
        // warm connection pool instead of opening cold connections
        val httpClient = com.streamflow.data.OkHttpDownloader.instance.client
        // Read-through disk cache on top: replaying a video or seeking backwards
        // past the back-buffer streams from disk instead of the network
        val dsf = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(com.streamflow.data.MediaCache.get(this))
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(httpClient))
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val defaultMsf = DefaultMediaSourceFactory(dsf)
        val mediaSourceFactory = object : androidx.media3.exoplayer.source.MediaSource.Factory
            by defaultMsf {
            override fun createMediaSource(
                mediaItem: MediaItem
            ): androidx.media3.exoplayer.source.MediaSource {
                val audioUrl = mediaItem.requestMetadata.extras?.getString("audioUrl")
                return if (audioUrl != null) {
                    val video = ProgressiveMediaSource.Factory(dsf).createMediaSource(mediaItem)
                    val audio = ProgressiveMediaSource.Factory(dsf)
                        .createMediaSource(MediaItem.fromUri(audioUrl))
                    // ProgressiveMediaSource ignores subtitle configs, so merge them explicitly
                    val sources = mutableListOf<androidx.media3.exoplayer.source.MediaSource>(video, audio)
                    mediaItem.localConfiguration?.subtitleConfigurations?.forEach { sub ->
                        sources.add(SingleSampleMediaSource.Factory(dsf)
                            .createMediaSource(sub, C.TIME_UNSET))
                    }
                    MergingMediaSource(*sources.toTypedArray())
                } else {
                    defaultMsf.createMediaSource(mediaItem)
                }
            }
        }

        // Start playback with just 0.8s buffered (default is 2.5s) for faster video
        // start, while keeping a large max buffer for smooth long-form playback.
        // High-RAM devices buffer further ahead and keep a back-buffer so small
        // rewinds replay instantly instead of re-fetching from the network.
        val highPerf = com.streamflow.data.DeviceCaps.isHighPerf
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 20_000,
                /* maxBufferMs = */ if (highPerf) 120_000 else 60_000,
                /* bufferForPlaybackMs = */ 800,
                /* bufferForPlaybackAfterRebufferMs = */ 1_500
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ if (highPerf) 20_000 else 0,
                /* retainBackBufferFromKeyframe = */ true
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Decoder fallback: if the preferred hardware decoder fails to init or
        // errors mid-stream, try the next decoder instead of stopping playback
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        // Seeks snap to the nearest keyframe instead of decoding the whole group
        // of frames up to the exact position — double-tap skips land instantly
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        // Hold a network wake lock while playing so background / screen-off audio
        // (and audio-only mode) doesn't stall when the device tries to doze — the
        // WAKE_LOCK permission is already declared. Released automatically when
        // playback pauses/stops, so it costs nothing while idle.
        player.setWakeMode(C.WAKE_MODE_NETWORK)

        audioSessionId = player.audioSessionId
        // "End of video" sleep mode: stop right here when the video finishes —
        // enforced in the service (like the timed deadline) so autoplay screen
        // churn can't cancel it
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED &&
                    com.streamflow.data.SleepTimer.endOfVideo.value) {
                    player.pause()
                    com.streamflow.data.SleepTimer.clear()
                }
            }
        })
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                sessionId: Int
            ) {
                audioSessionId = sessionId
                applyBoost()
                applyEq()
            }
        })
        // distinctUntilChanged: DataStore re-emits on EVERY preference write, and
        // without it toggling any unrelated setting released + recreated the
        // LoudnessEnhancer/Equalizer mid-playback (audible glitch)
        serviceScope.launch {
            (application as StreamFlowApp).prefs.volumeBoost.distinctUntilChanged().collect { v ->
                boostGainMb = v.toIntOrNull() ?: 0
                applyBoost()
            }
        }
        serviceScope.launch {
            (application as StreamFlowApp).prefs.eqPreset.distinctUntilChanged().collect { v ->
                eqPresetName = v
                applyEq()
            }
        }
        serviceScope.launch {
            (application as StreamFlowApp).prefs.eqBands.distinctUntilChanged().collect { v ->
                customBands = v
                if (eqPresetName == "CUSTOM") applyEq()
            }
        }

        // Enforce the sleep timer here, not in the UI: the player screen is
        // recreated on every autoplay/related-video switch, which used to
        // silently cancel the timer — the service outlives all of that.
        serviceScope.launch {
            com.streamflow.data.SleepTimer.deadlineAt.collectLatest { deadline ->
                if (deadline <= 0L) return@collectLatest
                delay((deadline - System.currentTimeMillis()).coerceAtLeast(0L))
                mediaSession?.player?.pause()
                com.streamflow.data.SleepTimer.clear()
            }
        }

        // Tapping the media notification / lock-screen card opens the app
        val sessionActivity = android.app.PendingIntent.getActivity(
            this, 0,
            android.content.Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // The "Next" button shown in the notification + lock-screen controls
        val nextButton = CommandButton.Builder()
            .setDisplayName("Next")
            .setIconResId(R.drawable.ic_notif_next)
            .setSessionCommand(nextCommand)
            .setEnabled(true)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCustomLayout(listOf(nextButton))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    // Grant the custom NEXT command on top of the defaults, or the
                    // notification button would be rejected as an unavailable command
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                        .add(nextCommand)
                        .build()
                    return MediaSession.ConnectionResult.accept(
                        sessionCommands,
                        Player.Commands.Builder().addAllCommands().build()
                    )
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: android.os.Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == CUSTOM_NEXT) {
                        playNext()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }

                // Bluetooth/headset "play" after the app was killed: re-extract the
                // last watched video and resume from its saved position
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): com.google.common.util.concurrent.ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val future = com.google.common.util.concurrent
                        .SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                    serviceScope.launch {
                        try {
                            val app = application as StreamFlowApp
                            // Skip downloaded/local files — they'd fail the YouTube
                            // extractor and abort resumption entirely; find the most
                            // recent entry that's actually a YouTube URL instead.
                            val last = app.database.historyDao().getAll().first()
                                .firstOrNull { !isLocalOrDirectUrl(it.url) }
                                ?: throw Exception("no resumable history")
                            // Headset resume can happen on mobile data — honour the
                            // battery/data-saver 480p cap instead of pulling a full
                            // 1080p stream the user asked us not to on the go.
                            val details = com.streamflow.data.YouTubeRepository()
                                .getVideoDetails(last.url, resumeQuality())
                            val extras = Bundle().apply {
                                details.audioUrl?.let { putString("audioUrl", it) }
                            }
                            val item = MediaItem.Builder()
                                .setUri(details.streamUrl)
                                .setMediaId(details.url)
                                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(details.title)
                                    .setArtist(details.uploaderName)
                                    .setArtworkUri(android.net.Uri.parse(details.thumbnailUrl))
                                    .build())
                                .setRequestMetadata(MediaItem.RequestMetadata.Builder()
                                    .setExtras(extras).build())
                                .build()
                            future.set(MediaSession.MediaItemsWithStartPosition(
                                listOf(item), 0, last.position))
                        } catch (e: Exception) {
                            future.setException(e)
                        }
                    }
                    return future
                }
            })
            .build()
    }

    // Advance to the next video, mirroring the on-screen "next": the queued video
    // takes priority (explicit user intent), otherwise the current video's first
    // related video. Runs off-main for extraction, then plays on the main thread.
    private fun playNext() {
        if (advancing) return
        advancing = true
        serviceScope.launch {
            try {
                val queued = com.streamflow.data.PlaybackQueue.popNext()
                when {
                    queued != null -> resolveAndPlay(queued.url, queued)
                    else -> {
                        val rel = relatedOfCurrent()
                        if (rel != null) resolveAndPlay(rel, null)
                        else android.widget.Toast.makeText(
                            this@PlaybackService, "Nothing up next", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
            } finally {
                advancing = false
            }
        }
    }

    private suspend fun relatedOfCurrent(): String? {
        val currentUrl = mediaSession?.player?.currentMediaItem?.mediaId ?: return null
        if (isLocalOrDirectUrl(currentUrl)) return null
        return try {
            com.streamflow.data.YouTubeRepository()
                .getVideoDetails(currentUrl, resumeQuality())
                .relatedVideos.firstOrNull()?.url
        } catch (_: Exception) { null }
    }

    // Battery/data-saver cap, shared with headset resume
    private suspend fun resumeQuality(): String {
        val prefs = (application as StreamFlowApp).prefs
        return if (prefs.batterySaver.first() || prefs.dataSaver.first()) "480P" else "AUTO"
    }

    // Per-channel speed override wins over the global default (mirrors PlayerScreen)
    private suspend fun playbackSpeedFor(uploaderUrl: String?): Float {
        val prefs = (application as StreamFlowApp).prefs
        val ch = if (!uploaderUrl.isNullOrEmpty()) prefs.channelSpeeds.first()[uploaderUrl] else null
        return ch ?: (prefs.defaultSpeed.first().toFloatOrNull() ?: 1f)
    }

    // [hint] carries title/thumb for a DIRECT stream (which can't be extracted).
    private suspend fun resolveAndPlay(url: String, hint: com.streamflow.data.model.VideoItem?) {
        val player = mediaSession?.player ?: return
        // Don't reload the video that's already playing (queue head == current)
        if (player.currentMediaItem?.mediaId == url) return
        val app = application as StreamFlowApp

        // Direct stream / downloaded file: play as-is, no YouTube extraction
        if (isLocalOrDirectUrl(url)) {
            val item = MediaItem.Builder()
                .setUri(url).setMediaId(url)
                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(hint?.title ?: "Video")
                    .setArtist(hint?.uploaderName ?: "")
                    .apply {
                        hint?.thumbnailUrl?.takeIf { it.isNotEmpty() }
                            ?.let { setArtworkUri(android.net.Uri.parse(it)) }
                    }.build())
                .build()
            player.setMediaItem(item); player.prepare(); player.play()
            player.setPlaybackSpeed(playbackSpeedFor(hint?.uploaderUrl))
            com.streamflow.ui.components.MiniPlayerState.update(
                com.streamflow.ui.components.MiniPlayerData(
                    url = url, title = hint?.title ?: "Video",
                    thumbnailUrl = hint?.thumbnailUrl ?: "",
                    uploaderName = hint?.uploaderName ?: "", isPlaying = true))
            return
        }

        val details = com.streamflow.data.YouTubeRepository().getVideoDetails(url, resumeQuality())
        val extras = Bundle().apply { details.audioUrl?.let { putString("audioUrl", it) } }
        val builder = MediaItem.Builder()
            .setUri(details.streamUrl)
            .setMediaId(details.url)
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                .setTitle(details.title)
                .setArtist(details.uploaderName)
                .setArtworkUri(android.net.Uri.parse(details.thumbnailUrl))
                .build())
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
        // Live manifests often lack a file extension — hint the type (as PlayerScreen does)
        if (details.isLive) {
            builder.setMimeType(
                if (details.streamUrl.contains("mpd", true) || details.streamUrl.contains("dash", true))
                    androidx.media3.common.MimeTypes.APPLICATION_MPD
                else androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        }
        player.setMediaItem(builder.build())
        player.prepare()
        player.play()
        player.setPlaybackSpeed(playbackSpeedFor(details.uploaderUrl))
        // Keep the in-app mini player in sync with what the notification advanced to
        com.streamflow.ui.components.MiniPlayerState.update(
            com.streamflow.ui.components.MiniPlayerData(
                url = details.url, title = details.title,
                thumbnailUrl = details.thumbnailUrl,
                uploaderName = details.uploaderName, isPlaying = true))
        // Record history (unless incognito), carrying any existing resume position
        if (!app.prefs.incognito.first()) {
            val prevPos = try { app.database.historyDao().getPosition(details.url) } catch (_: Exception) { 0L }
            app.database.historyDao().insert(com.streamflow.data.local.entity.HistoryEntity(
                url = details.url, title = details.title, thumbnailUrl = details.thumbnailUrl,
                uploaderName = details.uploaderName, viewCount = details.viewCount,
                duration = details.duration, position = prevPos))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        serviceScope.cancel()
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        loudnessEnhancer = null
        try { equalizer?.release() } catch (_: Exception) {}
        equalizer = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private const val CUSTOM_NEXT = "com.streamflow.action.NEXT"
    }
}
