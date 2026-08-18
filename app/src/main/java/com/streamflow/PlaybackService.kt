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
    private val prevCommand = SessionCommand(CUSTOM_PREV, android.os.Bundle.EMPTY)
    private var advancing = false // guard against double-taps while resolving

    // Play-history back-stack for the notification's Previous button. We record
    // every media-item change (from the app OR the notification's Next), so
    // Previous replays the video you were just watching. Bounded so a long
    // session can't grow it without limit.
    private val backStack = ArrayDeque<String>()
    private var lastMediaId: String? = null
    // Set right before a Previous-initiated switch so the resulting transition
    // isn't itself pushed onto the stack (which would make prev/next oscillate).
    private var skipHistoryPush = false
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
        // Buffer budgets come from DeviceCaps.tier so a 2018 6 GB phone is not
        // handed a flagship-sized buffer.
        //
        // setTargetBufferBytes is the important addition. It was never set, so
        // DefaultLoadControl fell back to its per-track-type default of 128 MB
        // for video: with setPrioritizeTimeOverSizeThresholds(true) the time
        // limits are what usually stop loading, but on a high-bitrate stream the
        // byte ceiling is what stands between a big buffer and an OutOfMemory
        // kill on a device with less headroom. An explicit, tiered cap makes
        // that bound real on every device instead of theoretical on all of them.
        val caps = com.streamflow.data.DeviceCaps
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 20_000,
                /* maxBufferMs = */ caps.maxBufferMs,
                /* bufferForPlaybackMs = */ 800,
                /* bufferForPlaybackAfterRebufferMs = */ 1_500
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ caps.backBufferMs,
                /* retainBackBufferFromKeyframe = */ true
            )
            .setTargetBufferBytes(caps.targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Decoder fallback: if the preferred hardware decoder fails to init or
        // errors mid-stream, try the next decoder instead of stopping playback
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)

        // ── Track selection: deliberately left at ExoPlayer's defaults ────────
        //
        // v6.3.0 installed a custom DefaultTrackSelector here and it BROKE VIDEO
        // PLAYBACK — audio played, the picture stayed black. The cause was
        // setExceedRendererCapabilitiesIfNecessary(true): despite the reassuring
        // name, it does not mean "fall back to something safe", it means "select
        // a track even when the decoder cannot handle it". Paired with a raised
        // initial bandwidth estimate that pushed selection toward higher-bitrate
        // tracks, the player kept choosing streams this device could not decode.
        //
        // The stock selector already picks the best track the renderer actually
        // supports, and DefaultRenderersFactory.setEnableDecoderFallback(true)
        // above covers a decoder that fails at runtime. Do not re-add a custom
        // selector without testing playback on a real device first.
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
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    // Real playback resumed: forget the retry budget so a later,
                    // unrelated glitch gets its own full set of attempts rather
                    // than inheriting a spent counter from an hour ago.
                    retryAttempt = 0
                    retryJob?.cancel(); retryJob = null
                    com.streamflow.data.PlaybackRecovery.onRecovered()
                }
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    if (com.streamflow.data.SleepTimer.endOfVideo.value) {
                        player.pause()
                        com.streamflow.data.SleepTimer.clear()
                        return
                    }
                    maybeAutoAdvance()
                }
            }

            // The app had no onPlayerError handler at all: media3 reported the
            // failure, the player parked in STATE_IDLE, and nothing ever called
            // prepare() again. That is why a moment of bad signal — or a stream
            // URL expiring while the screen was off — ended playback for good.
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                handlePlayerError(error)
            }

            // Persist the resume position periodically while playing. It used to
            // be written only when the player SCREEN was disposed, so a process
            // killed in the background — the normal outcome on the aggressive
            // OEM launchers this app already works around — lost the position
            // entirely and restarted the episode from zero.
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startPositionTicker() else { stopPositionTicker(); savePositionNow() }
            }
            // Track play history for the Previous button: whenever the item changes
            // to a genuinely different video, remember the one we just left.
            override fun onMediaItemTransition(
                mediaItem: MediaItem?, reason: Int
            ) {
                val newId = mediaItem?.mediaId
                if (skipHistoryPush) {
                    skipHistoryPush = false // this transition WAS the Previous jump
                } else {
                    val prev = lastMediaId
                    if (prev != null && prev != newId) {
                        backStack.addLast(prev)
                        while (backStack.size > 50) backStack.removeFirst()
                    }
                }
                lastMediaId = newId
                // A different video is now loaded, so the previous one's
                // end-of-video claim is spent — release it, or replaying that
                // same video later would never auto-advance again.
                com.streamflow.data.AutoAdvance.reset()
                retryAttempt = 0
                // Must reset with the item, or recovering a NEW video would seek
                // it to the previous video's position.
                lastGoodPositionMs = 0L
                prefetchedFor = null
                retryJob?.cancel(); retryJob = null
                com.streamflow.data.PlaybackRecovery.onRecovered()
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

        // Previous + Next buttons for the notification + lock-screen controls.
        // Order matters: [Prev, Next] renders as the familiar |◀  ▶| pair around
        // play/pause in the compact notification.
        val prevButton = CommandButton.Builder()
            .setDisplayName("Previous")
            .setIconResId(R.drawable.ic_notif_prev)
            .setSessionCommand(prevCommand)
            .setEnabled(true)
            .build()
        val nextButton = CommandButton.Builder()
            .setDisplayName("Next")
            .setIconResId(R.drawable.ic_notif_next)
            .setSessionCommand(nextCommand)
            .setEnabled(true)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCustomLayout(listOf(prevButton, nextButton))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    // Grant the custom PREV/NEXT commands on top of the defaults, or
                    // the notification buttons would be rejected as unavailable
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                        .add(nextCommand)
                        .add(prevCommand)
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
                    when (customCommand.customAction) {
                        CUSTOM_NEXT -> { playNext(); return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS)) }
                        CUSTOM_PREV -> { playPrevious(); return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS)) }
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

    // ── Error recovery ───────────────────────────────────────────────────────

    private var retryAttempt = 0
    private var retryJob: kotlinx.coroutines.Job? = null
    private var positionTicker: kotlinx.coroutines.Job? = null

    // Where the failed item was when it died. currentPosition reads 0 once the
    // player has reset to IDLE, so the position has to be captured continuously
    // while things are healthy — otherwise every recovery restarts the episode.
    private var lastGoodPositionMs = 0L

    /** HTTP status behind this failure, or PlaybackRecovery.NO_STATUS. */
    private fun httpStatusOf(error: androidx.media3.common.PlaybackException): Int {
        var cause: Throwable? = error.cause
        var hops = 0
        while (cause != null && hops < 8) {
            if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
            hops++
        }
        return com.streamflow.data.PlaybackRecovery.NO_STATUS
    }

    private fun handlePlayerError(error: androidx.media3.common.PlaybackException) {
        val player = mediaSession?.player ?: return
        val mediaId = player.currentMediaItem?.mediaId
        val remote = mediaId != null &&
            !mediaId.startsWith("file://") && !mediaId.startsWith("content://")

        val plan = com.streamflow.data.PlaybackRecovery.plan(
            errorCode = error.errorCode,
            httpStatus = httpStatusOf(error),
            isRemote = remote
        )

        val exhausted = retryAttempt >= com.streamflow.data.PlaybackRecovery.MAX_ATTEMPTS
        if (plan == com.streamflow.data.RecoveryPlan.FATAL || exhausted) {
            // Say so rather than leaving a frozen frame behind an indicator that
            // never resolves — that is indistinguishable from the app hanging.
            com.streamflow.data.PlaybackRecovery.onGaveUp(plan, exhausted)
            retryAttempt = 0
            return
        }

        retryAttempt++
        val attempt = retryAttempt
        retryJob?.cancel()
        retryJob = serviceScope.launch {
            try {
                // Waiting for the network beats spending attempts against it.
                // Five retries fired blind take under 30 s and would all fail in
                // a tunnel, leaving nothing in reserve for the moment signal
                // actually returns.
                if (!com.streamflow.data.ConnectivityMonitor.online.value) {
                    com.streamflow.data.PlaybackRecovery.onAttempt(attempt, waitingForNetwork = true)
                    // Bounded: a phone left offline must not hold a coroutine and
                    // a wake lock indefinitely.
                    com.streamflow.data.ConnectivityMonitor.awaitOnline(120_000L)
                }
                com.streamflow.data.PlaybackRecovery.onAttempt(attempt, waitingForNetwork = false)
                delay(com.streamflow.data.PlaybackRecovery.backoffMs(attempt))

                val p = mediaSession?.player ?: return@launch
                val resumeAt = lastGoodPositionMs
                if (plan == com.streamflow.data.RecoveryPlan.REEXTRACT && remote && mediaId != null) {
                    reExtractInPlace(mediaId, resumeAt)
                } else {
                    // Same item, same position: re-preparing is all a transient
                    // network failure needs.
                    p.prepare()
                    if (resumeAt > 0L) p.seekTo(resumeAt)
                    p.play()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Recovery itself failed — extraction threw because the video was
                // pulled, went private, or is geo-blocked. No further
                // onPlayerError will arrive (prepare never happened), so this is
                // the last chance to tell the user rather than leave them
                // watching a still frame. The counter stays raised so repeated
                // failures still walk toward the ceiling.
                com.streamflow.data.PlaybackRecovery.onGaveUp(
                    plan,
                    exhausted = attempt >= com.streamflow.data.PlaybackRecovery.MAX_ATTEMPTS
                )
            }
        }
    }

    /** Re-resolve an expired stream URL for the video already loaded, and
     *  continue from where it stopped. */
    private suspend fun reExtractInPlace(videoUrl: String, resumeAt: Long) {
        // Drop every cached variant first. The 30-minute TTL is not enough on
        // its own — a signed URL can die well inside it, and re-extracting
        // straight from cache would hand the player the same dead URL back.
        com.streamflow.data.VideoDetailsCache.invalidate(videoUrl)
        val details = com.streamflow.data.YouTubeRepository()
            .getVideoDetails(videoUrl, resumeQuality())
        val player = mediaSession?.player ?: return
        val extras = Bundle().apply { details.audioUrl?.let { putString("audioUrl", it) } }
        val item = MediaItem.Builder()
            .setUri(details.streamUrl)
            // Same mediaId, so this is not a video change: no history push, no
            // mini-player churn, no auto-advance claim released.
            .setMediaId(videoUrl)
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                .setTitle(details.title)
                .setArtist(details.uploaderName)
                .setArtworkUri(android.net.Uri.parse(details.thumbnailUrl))
                .build())
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
            .build()
        player.setMediaItem(item, resumeAt)
        player.prepare()
        player.play()
    }

    // ── Resume-position persistence ──────────────────────────────────────────

    private fun startPositionTicker() {
        if (positionTicker?.isActive == true) return
        positionTicker = serviceScope.launch {
            while (true) {
                delay(5_000L)
                val p = mediaSession?.player ?: continue
                val pos = try { p.currentPosition } catch (_: Exception) { 0L }
                // Only track a position the player is genuinely at. Capturing it
                // here is what lets recovery resume instead of restarting.
                if (pos > 0L && p.playbackState == androidx.media3.common.Player.STATE_READY) {
                    lastGoodPositionMs = pos
                }
                savePositionNow()
                maybePrefetchNext(p)
            }
        }
    }

    // Video URL whose successor has already been warmed, so the prefetch runs
    // once per video rather than on every 5-second tick.
    private var prefetchedFor: String? = null

    /**
     * Warm the NEXT video's extraction shortly before the current one ends.
     *
     * Extraction is by far the slowest part of opening a video — it is the one
     * number the stats overlay singles out — and until now auto-advance paid it
     * in full, after the current video had already gone silent. That gap is the
     * "unnecessary loading delay" between episodes.
     *
     * This only populates VideoDetailsCache; the advance path calls
     * getVideoDetails as before and simply finds it already there. Nothing
     * downstream had to change, and if the prefetch fails or never runs, the
     * behaviour is exactly what it was.
     */
    private fun maybePrefetchNext(player: androidx.media3.common.Player) {
        val currentId = player.currentMediaItem?.mediaId ?: return
        if (prefetchedFor == currentId) return
        val duration = player.duration
        if (duration <= 0L) return // live or unknown length: no "nearly over"
        val remaining = duration - player.currentPosition
        // 25 s is comfortably longer than a typical extraction but short enough
        // that a user who skips away has usually already gone.
        if (remaining !in 0..25_000L) return
        if (!com.streamflow.data.ConnectivityMonitor.online.value) return
        prefetchedFor = currentId

        serviceScope.launch {
            try {
                val next = com.streamflow.data.PlaybackQueue.queue.value.firstOrNull()?.url
                    ?: relatedOfCurrent()
                    ?: return@launch
                if (isLocalOrDirectUrl(next)) return@launch // nothing to extract
                com.streamflow.data.YouTubeRepository().getVideoDetails(next, resumeQuality())
            } catch (_: Exception) {
                // A failed warm-up costs nothing: the advance path re-extracts.
            }
        }
    }

    private fun stopPositionTicker() {
        positionTicker?.cancel(); positionTicker = null
    }

    private fun savePositionNow() {
        val p = mediaSession?.player ?: return
        val id = p.currentMediaItem?.mediaId ?: return
        val pos = try { p.currentPosition } catch (_: Exception) { 0L }
        if (pos <= 1_000L) return
        val app = application as StreamFlowApp
        // Deliberately NOT serviceScope: onTaskRemoved and onDestroy both save,
        // and onDestroy cancels serviceScope — a write launched there would be
        // cancelled before Room finished it, losing the very position this call
        // exists to protect. appScope outlives the service.
        app.appScope.launch {
            try {
                if (app.prefs.incognito.first()) return@launch
                app.database.historyDao().updatePosition(id, pos)
            } catch (_: Exception) {}
        }
    }

    // ── End-of-video advancement ─────────────────────────────────────────────

    /**
     * Advance when the video ends and nothing else will.
     *
     * The player screen runs its own countdown with a Cancel button, and that UX
     * is worth keeping — so the service stands down whenever a player screen is
     * actually on screen. It steps in for the two cases that were silently
     * broken: audio playing under the mini player with no player screen open,
     * and the app backgrounded or the screen off, where the countdown effect had
     * no one to show a countdown to.
     */
    private fun maybeAutoAdvance() {
        val player = mediaSession?.player ?: return
        val endedId = player.currentMediaItem?.mediaId ?: return
        val uiOwnsIt = com.streamflow.data.PlayerUiPresence.active &&
            com.streamflow.data.AppForeground.isForeground
        if (uiOwnsIt) return
        if (!com.streamflow.data.AutoAdvance.claim(endedId)) return
        serviceScope.launch {
            // The queue is explicit user intent and plays regardless of the
            // toggle; related-video autoplay is what the toggle governs. This
            // mirrors the on-screen rule exactly so the two paths cannot
            // disagree about what "auto-play off" means.
            val autoPlayOn = try {
                (application as StreamFlowApp).prefs.autoPlay.first()
            } catch (_: Exception) { true }
            advance(allowRelated = autoPlayOn)
        }
    }

    // Advance to the next video, mirroring the on-screen "next": the queued video
    // takes priority (explicit user intent), otherwise the current video's first
    // related video. Runs off-main for extraction, then plays on the main thread.
    private fun playNext() {
        // A button press is explicit intent, so it ignores the auto-play
        // preference entirely — that toggle governs what happens on its own.
        serviceScope.launch { advance(allowRelated = true, announce = true) }
    }

    /**
     * Play whatever comes next: the queue first (explicit user intent), then the
     * current video's first related video.
     *
     * A queued video that has been removed, made private or geo-blocked used to
     * end the session silently — one failed extraction and the exception was
     * swallowed with nothing playing. Now a failure moves on to the next
     * candidate, so one dead entry in a long queue costs one skip rather than
     * the rest of the queue.
     *
     * @param allowRelated whether falling through to the current video's first
     *        related video is permitted. False when Settings > Playback >
     *        Auto-play is off: the queue is still honoured there, because
     *        queueing something is an explicit request to play it.
     * @param announce whether to tell the user when there is genuinely nothing
     *        left — appropriate for a button press, noise for the screen-off
     *        end of the last video in a queue.
     */
    private suspend fun advance(allowRelated: Boolean, announce: Boolean = false) {
        if (advancing) return
        advancing = true
        try {
            // Bounded so a queue full of dead links cannot spin: three failures
            // in a row is a broken queue, not bad luck.
            var attempts = 0
            while (attempts < 3) {
                val queued = com.streamflow.data.PlaybackQueue.popNext() ?: break
                attempts++
                try {
                    resolveAndPlay(queued.url, queued)
                    return
                } catch (_: Exception) {
                    // Dead entry: already popped, so the loop moves to the next.
                }
            }
            if (!allowRelated) return
            val rel = try { relatedOfCurrent() } catch (_: Exception) { null }
            if (rel != null) {
                try {
                    resolveAndPlay(rel, null)
                    return
                } catch (_: Exception) {}
            }
            if (announce) {
                android.widget.Toast.makeText(
                    this@PlaybackService, "Nothing up next", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } finally {
            advancing = false
        }
    }

    // Replay the previously watched video from the play-history stack.
    private fun playPrevious() {
        if (advancing) return
        val prevUrl = backStack.removeLastOrNull()
        if (prevUrl == null) {
            android.widget.Toast.makeText(this, "No previous video", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        advancing = true
        // Don't let the resulting item change push onto the back-stack, or Prev
        // and Next would just bounce between the same two videos.
        skipHistoryPush = true
        serviceScope.launch {
            try { resolveAndPlay(prevUrl, null) }
            catch (_: Exception) { skipHistoryPush = false }
            finally { advancing = false }
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
        // Don't reload the video that's already playing (queue head == current).
        // Clear the history-skip flag too: no transition will fire to consume it.
        if (player.currentMediaItem?.mediaId == url) { skipHistoryPush = false; return }
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

    /**
     * The user swiped the app off the Recents screen.
     *
     * Media3 leaves this to the app, and doing nothing has two bad outcomes at
     * once: if playback is stopped the service lingers with a dead notification
     * the user cannot get rid of, and if it is playing there is no guarantee the
     * resume position survives the process going away. So: keep playing when
     * playing (that is the whole point of a media session), but bank the
     * position first, and shut down cleanly otherwise.
     */
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        savePositionNow()
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopPositionTicker()
        retryJob?.cancel()
        com.streamflow.data.PlaybackRecovery.onRecovered()
        serviceScope.cancel()
        backStack.clear()
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
        private const val CUSTOM_PREV = "com.streamflow.action.PREV"
    }
}
