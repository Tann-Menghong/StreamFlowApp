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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Downloaded files and other direct streams aren't YouTube URLs — running them
// through the extractor during Bluetooth playback resumption would just throw.
//
// This used to be a local copy of the rule, one of three that had drifted apart.
// It now delegates to the single classifier so the service, the player screen
// and the ViewModel cannot disagree about what a file:// URI is again.
private fun isLocalOrDirectUrl(url: String): Boolean =
    com.streamflow.data.MediaUrl.isLocalOrDirect(url)

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Legacy custom next/previous commands. The notification no longer uses
    // them — PlayerCommandBridge exposes the NATIVE commands instead, so
    // headsets, cars and Android Auto can drive them too. These stay registered
    // because they are cheap and anything already sending them keeps working.
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

        // Keep the media notification while the player sits IDLE.
        //
        // media3 1.8 changed the default: controllers no longer get a
        // notification for an idle player without active preparation or
        // playback. That is sensible in general and wrong for this app
        // specifically, because IDLE is exactly where recovery lives. After a
        // failure, scheduleRecovery() can wait up to 120 s for the network to
        // come back before it re-prepares -- and under the new default the
        // notification would vanish for that whole window. Someone listening
        // with the screen off would watch their controls disappear and conclude
        // playback had died, at the precise moment the app was working hardest
        // to bring it back.
        //
        // ALWAYS is the pre-1.8 behaviour, chosen deliberately: this upgrade
        // should change how playback is decoded, not what the user sees while it
        // recovers.
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_ALWAYS)

        // Reuse the app-wide client (same UA headers) so media requests share the
        // warm connection pool instead of opening cold connections
        val httpClient = com.streamflow.data.OkHttpDownloader.instance.client

        // DefaultDataSource wraps the OkHttp source and adds the schemes OkHttp
        // cannot speak: file://, content://, asset://. That matters now that
        // downloaded files reach the player instead of being misrouted into the
        // WebView — handing a file:// URI straight to OkHttpDataSource fails
        // immediately, so the fix to the routing would have achieved nothing on
        // its own. http(s) still goes to the same warm OkHttp connection pool.
        val baseFactory = androidx.media3.datasource.DefaultDataSource.Factory(
            this, OkHttpDataSource.Factory(httpClient)
        )

        // Read-through disk cache on top: replaying a video or seeking backwards
        // past the back-buffer streams from disk instead of the network
        val dsf = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(com.streamflow.data.MediaCache.get(this))
            .setUpstreamDataSourceFactory(baseFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val defaultMsf = DefaultMediaSourceFactory(dsf)
        // No disk cache for a file that is already on the disk — caching a
        // download would store a second copy of it and evict genuinely useful
        // stream data to do so.
        val localMsf = DefaultMediaSourceFactory(baseFactory)

        val mediaSourceFactory = object : androidx.media3.exoplayer.source.MediaSource.Factory
            by defaultMsf {
            override fun createMediaSource(
                mediaItem: MediaItem
            ): androidx.media3.exoplayer.source.MediaSource {
                val scheme = mediaItem.localConfiguration?.uri?.scheme?.lowercase()
                if (scheme == null || scheme == "file" || scheme == "content") {
                    return localMsf.createMediaSource(mediaItem)
                }
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
                // Arm or disarm the "buffering that never ends" watchdog. Must
                // run for every state, including the ones that END a stall.
                updateBufferWatchdog(player)
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    // Real playback resumed: forget the retry budget so a later,
                    // unrelated glitch gets its own full set of attempts rather
                    // than inheriting a spent counter from an hour ago.
                    if (retryAttempt > 0) {
                        com.streamflow.data.PlaybackLog.info(
                            "recovery", "recovered after $retryAttempt attempt(s)")
                    }
                    retryAttempt = 0
                    retryJob?.cancel(); retryJob = null
                    com.streamflow.data.PlaybackRecovery.onRecovered()
                    // A video restarted from the beginning has spent no
                    // end-of-video claim yet. reset() only ran on a media-item
                    // transition, and replaying the same video fires none — so
                    // the claim from the first play-through stood and the second
                    // ending advanced nowhere.
                    val id = player.currentMediaItem?.mediaId
                    if (id != null && player.currentPosition < 2_000L) {
                        com.streamflow.data.AutoAdvance.releaseIfClaimed(id)
                    }
                }
                // A MID-PLAYBACK stall only. Buffering at position zero is
                // normal startup, and buffering while paused is the user's
                // doing — neither says anything about the link's capacity.
                if (playbackState == androidx.media3.common.Player.STATE_BUFFERING &&
                    player.currentPosition > 0L && player.playWhenReady
                ) {
                    noteStall()
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

            /**
             * A seek past the buffer produces exactly the same STATE_BUFFERING
             * that a starved link does, and this player's headline gestures are
             * double-tap-to-skip and drag-to-scrub. Three skips in a minute on
             * perfect Wi-Fi therefore tripped the stall threshold and told the
             * user their connection was too slow — then re-extracted and
             * interrupted the video to "fix" it.
             */
            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int
            ) {
                if (reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK ||
                    reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    ignoreStallsUntil =
                        android.os.SystemClock.elapsedRealtime() + SEEK_SETTLE_MS
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

            // Pausing during a buffer does not change playbackState, so without
            // this the watchdog would keep counting and "recover" a video the
            // user had deliberately paused.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                updateBufferWatchdog(player)
            }
            // Track play history for the Previous button: whenever the item changes
            // to a genuinely different video, remember the one we just left.
            override fun onMediaItemTransition(
                mediaItem: MediaItem?, reason: Int
            ) {
                val newId = mediaItem?.mediaId
                val prev = lastMediaId
                // reExtractInPlace() replaces the MediaItem with a fresh stream
                // URL under the SAME mediaId, and media3 reports that as a
                // transition because the URI changed. Everything below is
                // per-VIDEO state, so running it on a same-video re-prepare
                // destroyed the very recovery it was part of: the resume
                // position was zeroed (a second failure within the 5 s ticker
                // window then restarted a 40-minute video from 0:00) and the
                // attempt counter was reset (so MAX_ATTEMPTS was never reached
                // and a permanently dead video re-extracted forever, each round
                // costing a network fetch and up to a 120 s offline wait).
                val isNewVideo = prev != newId
                if (skipHistoryPush) {
                    skipHistoryPush = false // this transition WAS the Previous jump
                } else if (prev != null && isNewVideo) {
                    backStack.addLast(prev)
                    while (backStack.size > 50) backStack.removeFirst()
                }
                lastMediaId = newId
                if (!isNewVideo) return
                // A different video is now loaded, so the previous one's
                // end-of-video claim is spent — release it, or replaying that
                // same video later would never auto-advance again.
                com.streamflow.data.AutoAdvance.reset()
                retryAttempt = 0
                // Stall evidence is per-video: a bad patch during the last
                // episode should not step the next one down before it has had a
                // chance. The chosen override survives, because the link that
                // caused it has not changed.
                recentStalls.clear()
                recentAhead.clear()
                comfortableStreak = 0
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
        // Stall evidence and the step-down it produced belong to one connection.
        // drop(1) so the initial value is not treated as a change.
        serviceScope.launch {
            com.streamflow.data.ConnectivityMonitor.metered
                .drop(1).distinctUntilChanged().collect { onNetworkChanged() }
        }
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
                delay((deadline - com.streamflow.data.SleepTimer.now()).coerceAtLeast(0L))
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

        // Previous / Next are now NATIVE commands, not a custom layout.
        //
        // The custom CommandButtons that used to live here only ever worked in
        // our own notification. A custom SessionCommand can only be invoked by
        // something that knows it exists, and a Bluetooth headset, a car head
        // unit, Android Auto and Wear all speak plain media-button vocabulary
        // instead — so on the surface that matters most for background
        // listening, "next" did nothing at all.
        //
        // PlayerCommandBridge advertises COMMAND_SEEK_TO_NEXT / _PREVIOUS and
        // routes them into the same playNext() / playPrevious() the buttons
        // called. media3's notification provider renders its own prev/next as
        // soon as those commands are available, which is why setCustomLayout is
        // gone: keeping it would put two of each button in the notification.
        // The custom commands themselves stay registered below, so anything
        // already sending them keeps working.
        val sessionPlayer = PlayerCommandBridge(
            player,
            onNext = { playNext() },
            onPrevious = { playPrevious() }
        )

        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(sessionActivity)
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

    // ── Buffering watchdog ───────────────────────────────────────────────────
    //
    // ExoPlayer has no buffering timeout of its own. It reports STATE_BUFFERING
    // and waits — forever, if that is how long the bytes take. An error is only
    // raised when the data source actually fails, so a server that accepts the
    // connection and then trickles (or sends nothing at all, on a link too slow
    // to trip OkHttp's 20 s read timeout between packets) leaves the player
    // buffering with no error, no timeout and nothing to recover from.
    //
    // The v6.9.0 stall detector cannot see it either: it requires
    // currentPosition > 0, so the entire initial load — the case users
    // describe as "stuck loading" — was explicitly excluded from the only
    // stall-detection the app had.
    //
    // This closes that hole at the layer that already owns bounded retries, so
    // a stuck load walks the same attempt budget as a failed one and ends at the
    // same visible give-up state rather than an indicator that never resolves.

    private var bufferWatchdog: kotlinx.coroutines.Job? = null
    private var bufferingSince = 0L

    /** Called on every state change; starts or clears the watchdog. */
    private fun updateBufferWatchdog(player: androidx.media3.common.Player) {
        val buffering = player.playbackState == androidx.media3.common.Player.STATE_BUFFERING &&
            player.playWhenReady
        if (!buffering) {
            bufferingSince = 0L
            bufferWatchdog?.cancel()
            bufferWatchdog = null
            return
        }
        if (bufferWatchdog?.isActive == true) return // already watching this stall
        val startedAt = android.os.SystemClock.elapsedRealtime()
        bufferingSince = startedAt
        val atStartup = player.currentPosition <= 0L
        bufferWatchdog = serviceScope.launch { watchBuffer(startedAt, atStartup) }
    }

    /**
     * Watch the buffer FILL, not the clock.
     *
     * A single delay() cannot tell a dead connection from a slow one, and the
     * two want opposite deadlines: nothing arriving is decided in twelve
     * seconds, while a 1080p stream trickling in over a weak link deserves the
     * full ceiling because it is still on its way to playing. Polling the
     * buffered position answers that directly -- as long as the buffer grows the
     * app keeps waiting, and the hard limit still bounds the whole wait.
     *
     * The poll exists only while playback is stuck, which is rare and
     * self-terminating; a healthy video never runs this loop at all.
     */
    private suspend fun watchBuffer(startedAt: Long, atStartup: Boolean) {
        var lastAhead = -1L
        var lastProgressAt = startedAt
        while (true) {
            delay(WATCH_POLL_MS)
            val p = mediaSession?.player ?: return
            // Anything that resolved the stall cleared bufferingSince.
            if (bufferingSince != startedAt) return
            if (p.playbackState != androidx.media3.common.Player.STATE_BUFFERING ||
                !p.playWhenReady
            ) return

            val now = android.os.SystemClock.elapsedRealtime()
            val ahead = try {
                (p.bufferedPosition - p.currentPosition).coerceAtLeast(0L)
            } catch (_: Exception) { 0L }
            if (lastAhead < 0L || com.streamflow.data.BufferHealth.progressed(lastAhead, ahead)) {
                lastProgressAt = now
            }
            lastAhead = ahead

            if (com.streamflow.data.BufferHealth.exhausted(
                    waitedMs = now - startedAt,
                    sinceProgressMs = now - lastProgressAt,
                    atStartup = atStartup
                )
            ) {
                onBufferTimedOut(p, now - startedAt, now - lastProgressAt, ahead)
                return
            }
        }
    }

    private fun onBufferTimedOut(
        player: androidx.media3.common.Player,
        waitedMs: Long,
        sinceProgressMs: Long,
        aheadMs: Long
    ) {
        val mediaId = player.currentMediaItem?.mediaId
        val atStartup = player.currentPosition <= 0L
        val dead = sinceProgressMs >= com.streamflow.data.BufferHealth.NO_PROGRESS_MS
        com.streamflow.data.PlaybackLog.warn(
            "watchdog",
            (if (dead) "buffer stopped growing for ${sinceProgressMs / 1000}s"
            else "only ${aheadMs / 1000}s buffered after ${waitedMs / 1000}s") + " " +
                (if (atStartup) "while starting" else "at ${player.currentPosition / 1000}s") +
                " video=${com.streamflow.data.PlaybackLog.ref(mediaId)}"
        )
        val remote = mediaId != null && !com.streamflow.data.MediaUrl.isLocalFile(mediaId)
        // A local file that will not buffer is not going to be fixed by
        // re-extracting a URL it does not have.
        val plan = if (remote) com.streamflow.data.RecoveryPlan.REEXTRACT
        else com.streamflow.data.RecoveryPlan.RETRY
        scheduleRecovery(plan, mediaId, remote, "stalled")
    }

    private fun handlePlayerError(error: androidx.media3.common.PlaybackException) {
        val player = mediaSession?.player ?: return
        val mediaId = player.currentMediaItem?.mediaId
        val remote = mediaId != null &&
            !mediaId.startsWith("file://") && !mediaId.startsWith("content://")

        val status = httpStatusOf(error)
        val plan = com.streamflow.data.PlaybackRecovery.plan(
            errorCode = error.errorCode,
            httpStatus = status,
            isRemote = remote
        )

        // The single most useful line in the whole log: what failed, what the
        // server said about it, and which of the three plans that produced.
        com.streamflow.data.PlaybackLog.warn(
            "player",
            "error code=${error.errorCode}" +
                (if (status != com.streamflow.data.PlaybackRecovery.NO_STATUS) " http=$status" else "") +
                " plan=$plan video=${com.streamflow.data.PlaybackLog.ref(mediaId)}"
        )

        scheduleRecovery(plan, mediaId, remote, error.errorCodeName)
    }

    /**
     * The one recovery path, shared by a reported error and a silent stall.
     *
     * Both failures want identical treatment — bounded attempts, wait for signal
     * rather than spend attempts against a dead network, re-extract or re-prepare,
     * and a visible give-up when the budget runs out — so they share one
     * implementation and one attempt counter. A stall that keeps retrying on its
     * own schedule while errors retry on another is how two recovery systems
     * fight each other.
     */
    private fun scheduleRecovery(
        plan: com.streamflow.data.RecoveryPlan,
        mediaId: String?,
        remote: Boolean,
        cause: String
    ) {
        val exhausted = retryAttempt >= com.streamflow.data.PlaybackRecovery.MAX_ATTEMPTS
        if (plan == com.streamflow.data.RecoveryPlan.FATAL || exhausted) {
            // Say so rather than leaving a frozen frame behind an indicator that
            // never resolves — that is indistinguishable from the app hanging.
            com.streamflow.data.PlaybackLog.error(
                "recovery",
                if (exhausted) "gave up after ${com.streamflow.data.PlaybackRecovery.MAX_ATTEMPTS} attempts"
                else "unrecoverable ($cause)"
            )
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
                    com.streamflow.data.PlaybackLog.info(
                        "recovery", "attempt $attempt waiting for network")
                    com.streamflow.data.PlaybackRecovery.onAttempt(attempt, waitingForNetwork = true)
                    // Bounded: a phone left offline must not hold a coroutine and
                    // a wake lock indefinitely.
                    val back = com.streamflow.data.ConnectivityMonitor.awaitOnline(120_000L)
                    if (!back) com.streamflow.data.PlaybackLog.warn(
                        "recovery", "still offline after 120s")
                }
                com.streamflow.data.PlaybackRecovery.onAttempt(attempt, waitingForNetwork = false)
                delay(com.streamflow.data.PlaybackRecovery.backoffMs(attempt))

                val p = mediaSession?.player ?: return@launch
                val resumeAt = lastGoodPositionMs
                if (plan == com.streamflow.data.RecoveryPlan.REEXTRACT && remote && mediaId != null) {
                    com.streamflow.data.PlaybackLog.info(
                        "recovery", "attempt $attempt re-extracting from ${resumeAt / 1000}s")
                    reExtractInPlace(mediaId, resumeAt)
                } else {
                    com.streamflow.data.PlaybackLog.info(
                        "recovery", "attempt $attempt re-preparing from ${resumeAt / 1000}s")
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
                com.streamflow.data.PlaybackLog.error(
                    "recovery", "attempt $attempt failed: ${e.javaClass.simpleName} ${e.message ?: ""}")
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
        // Read BEFORE the extraction: fetching a new stream URL takes seconds on
        // exactly the bad link that made this necessary, and a user who gives up
        // and pauses during that window must not have playback forced back on
        // when it lands.
        val wasPlaying = try { mediaSession?.player?.playWhenReady ?: true } catch (_: Exception) { true }
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
                .apply {
                    // An empty thumbnail URL parses to an empty Uri, which the
                    // notification provider then tries to load as artwork.
                    if (details.thumbnailUrl.isNotEmpty()) {
                        setArtworkUri(android.net.Uri.parse(details.thumbnailUrl))
                    }
                }
                .build())
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
            .build()
        player.setMediaItem(item, resumeAt)
        player.prepare()
        // Re-preparing buffers by definition. Without this the recovery's own
        // buffering counted as evidence of a weak link, so three expired URLs in
        // a minute produced a bogus "Lowered to 480p" that no amount of
        // bandwidth would have prevented.
        recentStalls.clear()
        // Occupancy samples were measured against the bitrate this call is
        // replacing, so they say nothing about the stream that follows.
        recentAhead.clear()
        comfortableStreak = 0
        ignoreStallsUntil = android.os.SystemClock.elapsedRealtime() + DOWNGRADE_SETTLE_MS
        if (wasPlaying) player.play()
    }

    // ── Resume-position persistence ──────────────────────────────────────────

    /**
     * The in-memory capture stays on a 5-second beat — recovery reads
     * lastGoodPositionMs and a coarser sample would make every recovery restart
     * further back than it needs to. What changed is the DATABASE write.
     *
     * Persisting on every tick meant 720 Room writes an hour, most of them
     * recording a position the previous write had almost already recorded, and
     * all of them continuing with the screen off during background listening.
     * The 5-second frequency was chosen to survive an OEM process kill, which
     * is right; the cost was never weighed. Writing every third tick, and only
     * when the position has genuinely moved, keeps the guarantee (at most 15 s
     * of a resume position lost, which is a comfort feature, not data) at a
     * third of the disk traffic.
     */
    private var ticksSinceSave = 0
    private var lastSavedPositionMs = 0L

    private fun startPositionTicker() {
        if (positionTicker?.isActive == true) return
        ticksSinceSave = 0
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
                // One reading of the buffer, on a ticker that was already
                // running, drives the ladder in both directions.
                noteBufferHealth(p)
                ticksSinceSave++
                if (ticksSinceSave >= SAVE_EVERY_N_TICKS) {
                    ticksSinceSave = 0
                    // A paused or stalled player reports the same position tick
                    // after tick; there is nothing to record.
                    if (kotlin.math.abs(pos - lastSavedPositionMs) >= 5_000L) savePositionNow()
                }
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
        lastSavedPositionMs = pos
        val app = application as StreamFlowApp
        // Deliberately NOT serviceScope: onTaskRemoved and onDestroy both save,
        // and onDestroy cancels serviceScope — a write launched there would be
        // cancelled before Room finished it, losing the very position this call
        // exists to protect. appScope outlives the service.
        app.appScope.launch {
            try {
                if (app.prefs.incognito.first()) return@launch
                app.database.historyDao().updatePosition(id, pos)
            } catch (e: Exception) {
                com.streamflow.data.PlaybackLog.warn(
                    "position", "save failed: ${e.javaClass.simpleName}")
            }
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
                    com.streamflow.data.PlaybackLog.info(
                        "advance", "queued -> ${com.streamflow.data.PlaybackLog.ref(queued.url)}")
                    return
                } catch (e: Exception) {
                    // Dead entry: already popped, so the loop moves to the next.
                    com.streamflow.data.PlaybackLog.warn(
                        "advance",
                        "skipped dead queue entry ${com.streamflow.data.PlaybackLog.ref(queued.url)}" +
                            " (${e.javaClass.simpleName})"
                    )
                }
            }
            if (!allowRelated) return
            val rel = try { relatedOfCurrent() } catch (_: Exception) { null }
            if (rel != null) {
                try {
                    resolveAndPlay(rel, null)
                    com.streamflow.data.PlaybackLog.info(
                        "advance", "related -> ${com.streamflow.data.PlaybackLog.ref(rel)}")
                    return
                } catch (e: Exception) {
                    com.streamflow.data.PlaybackLog.warn(
                        "advance", "related video failed (${e.javaClass.simpleName})")
                }
            }
            com.streamflow.data.PlaybackLog.info("advance", "nothing up next")
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

    /**
     * The quality to extract at, mirroring what the player screen would choose.
     *
     * This used to return a flat "AUTO" or "480P" and never looked at the user's
     * quality setting at all — so someone who had pinned 360p on mobile data got
     * AUTO the moment the service advanced to the next episode by itself, which
     * is exactly when they were least likely to be watching for it.
     */
    /** The user's own choice for the network in use, before any capping. */
    private suspend fun basePreference(): String {
        val prefs = (application as StreamFlowApp).prefs
        val cellularPref = try { prefs.qualityCellular.first() } catch (_: Exception) { "SAME" }
        return if (com.streamflow.data.ConnectivityMonitor.metered.value && cellularPref != "SAME") {
            cellularPref
        } else {
            try { prefs.quality.first() } catch (_: Exception) { com.streamflow.data.QualityLadder.AUTO }
        }
    }

    private suspend fun resumeQuality(): String {
        val prefs = (application as StreamFlowApp).prefs
        val base = basePreference()
        // A stall-driven step-down outranks the preference — but not forever, and
        // not past the user contradicting it. The override used to survive for
        // the life of the process: once three stalls had fired, every later
        // video was pinned lower for the rest of the session, with nothing in
        // the UI saying so and no way to undo it short of force-stopping the
        // app. Changing the quality setting is an explicit instruction and wins;
        // so does moving to a different network, since the link that justified
        // the override is no longer the link in use.
        if (com.streamflow.data.AdaptiveQuality.rung != null &&
            base != com.streamflow.data.AdaptiveQuality.baseline
        ) {
            com.streamflow.data.PlaybackLog.info(
                "quality", "preference changed to ${com.streamflow.data.QualityLadder.label(base)}, dropping step-down")
            com.streamflow.data.AdaptiveQuality.clear()
        }
        val effective = com.streamflow.data.AdaptiveQuality.rung?.let {
            com.streamflow.data.QualityLadder.cap(base, it)
        } ?: base
        val saverOn = try { prefs.batterySaver.first() } catch (_: Exception) { false }
        val dataSaverOn = try { prefs.dataSaver.first() } catch (_: Exception) { false }
        return when {
            saverOn -> com.streamflow.data.QualityLadder.cap(effective, "480P")
            effective == com.streamflow.data.QualityLadder.AUTO && dataSaverOn -> "480P"
            else -> effective
        }
    }

    // ── Adaptive quality ─────────────────────────────────────────────────────
    //
    // The app plays progressive streams, not adaptive DASH, so ExoPlayer's own
    // track selection has nothing to switch between: quality is fixed at
    // extraction and never revisited. A connection that degrades mid-video
    // therefore produces an endless rebuffer loop at a bitrate the link can no
    // longer carry — the player stalls, recovers, stalls again, forever, and
    // never tries the one thing that would fix it.
    //
    // Three stalls inside a minute is not bad luck, it is a link that cannot
    // sustain this stream. Stepping down one rung and re-extracting at the same
    // position turns stopped video into degraded video.

    private val recentStalls = ArrayDeque<Long>()
    // The step-down the app has imposed now lives in AdaptiveQuality, so the
    // player screen can SHOW it and an explicit quality pick can CLEAR it.
    // As two private fields here it was invisible and unreachable, and the UI
    // consequently reported a quality the service was no longer playing.
    private var downgrading = false
    /** Re-preparing after a step-down buffers by definition; that buffering must
     *  not be counted as evidence for another step-down. */
    private var ignoreStallsUntil = 0L

    /** Rolling buffer-occupancy samples, one per 5 s ticker beat. */
    private val recentAhead = ArrayDeque<Long>()
    private var comfortableStreak = 0
    /** The rung a step-up moved to, on probation until it proves itself. */
    private var stepUpRung: String? = null
    private var stepUpAt = 0L
    /** Rungs this link has demonstrably failed to carry. Cleared when the
     *  network changes, because the evidence belonged to the old link. */
    private val failedRungs = mutableSetOf<String>()

    /**
     * Forget stall evidence gathered on a different connection.
     *
     * Walking out of a dead spot onto Wi-Fi must not leave the session pinned to
     * the rung that the dead spot justified.
     */
    private fun onNetworkChanged() {
        recentStalls.clear()
        recentAhead.clear()
        comfortableStreak = 0
        stepUpRung = null
        failedRungs.clear()
        if (com.streamflow.data.AdaptiveQuality.rung != null) {
            com.streamflow.data.PlaybackLog.info("quality", "network changed, dropping step-down")
            com.streamflow.data.AdaptiveQuality.clear()
        }
    }

    /**
     * Read the buffer once per ticker beat and let it drive the ladder.
     *
     * The step-down this feeds already existed, but its only evidence was
     * STALLS -- three of them, meaning the app acted only after the user had
     * watched the video stop three separate times. A buffer draining toward
     * zero says the same thing before the first stall, from data the player was
     * already reporting, so the fix now lands ahead of the symptom instead of
     * behind it.
     *
     * And it is the only signal that can UNDO a step-down. Until now one bad
     * patch pinned the rest of the session to a lower rung, with nothing in the
     * app able to decide the link had recovered.
     */
    private fun noteBufferHealth(player: androidx.media3.common.Player) {
        // Only a playing video measures the link. Paused or buffering readings
        // describe the player's state, not the connection's capacity.
        if (player.playbackState != androidx.media3.common.Player.STATE_READY ||
            !player.playWhenReady
        ) return
        // A live stream deliberately keeps a small buffer near the edge; that is
        // not starvation, and there is no ladder to walk for it anyway.
        if (try { player.isCurrentMediaItemLive } catch (_: Exception) { false }) return

        val ahead = try {
            (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
        } catch (_: Exception) { return }

        recentAhead.addLast(ahead)
        while (recentAhead.size > com.streamflow.data.BufferHealth.STARVING_SAMPLES) {
            recentAhead.removeFirst()
        }
        comfortableStreak =
            if (com.streamflow.data.BufferHealth.comfortable(ahead)) comfortableStreak + 1 else 0

        // The settle window after any re-extract covers this too: a buffer
        // refilling from zero looks starved for the first few seconds.
        if (android.os.SystemClock.elapsedRealtime() < ignoreStallsUntil) return

        if (com.streamflow.data.BufferHealth.starving(recentAhead.toList())) {
            com.streamflow.data.PlaybackLog.info(
                "quality", "buffer down to ${ahead / 1000}s, stepping down before it stalls")
            // This path never reaches noteStall(), so probation has to be
            // judged here too or a rung could fail silently and be retried.
            failProbationIfRecent(android.os.SystemClock.elapsedRealtime())
            recentAhead.clear()
            maybeStepDownQuality()
            return
        }

        val duration = try { player.duration } catch (_: Exception) { 0L }
        val remaining = if (duration > 0L) duration - player.currentPosition else -1L
        if (com.streamflow.data.BufferHealth.readyToStepUp(
                comfortableStreak, com.streamflow.data.AdaptiveQuality.rung != null, remaining)
        ) {
            comfortableStreak = 0
            maybeStepUpQuality()
        }
    }

    /**
     * Give back quality the app took away, once the link has earned it.
     *
     * This is the one place the app re-extracts a video that is playing
     * perfectly well, so every guard here exists to make that rare and
     * worthwhile: two full minutes of a comfortable buffer against fifteen
     * seconds to step down, never past what the user asked for, never inside
     * the last minute of a video, and never onto a rung this link has already
     * failed. A rung that stalls inside its probation window is retired for the
     * session rather than retried every two minutes for the rest of the film.
     */
    private fun maybeStepUpQuality() {
        if (downgrading) return
        val player = mediaSession?.player ?: return
        val mediaId = player.currentMediaItem?.mediaId ?: return
        if (com.streamflow.data.MediaUrl.classify(mediaId) !=
            com.streamflow.data.MediaKind.YOUTUBE
        ) return
        val current = com.streamflow.data.AdaptiveQuality.rung ?: return
        val resumeFrom = try { player.currentPosition } catch (_: Exception) { lastGoodPositionMs }

        downgrading = true
        serviceScope.launch {
            try {
                val ceiling = basePreference()
                val next = com.streamflow.data.QualityLadder.stepUp(
                    current, ceiling, com.streamflow.data.DeviceCaps.autoMaxHeight
                )
                if (next == null || next in failedRungs) {
                    // Nowhere useful to go. Hold the reading down so this does
                    // not re-evaluate every two minutes for the rest of the video.
                    ignoreStallsUntil =
                        android.os.SystemClock.elapsedRealtime() + DOWNGRADE_SETTLE_MS
                    return@launch
                }
                com.streamflow.data.PlaybackLog.info(
                    "quality",
                    "buffer healthy -> ${com.streamflow.data.QualityLadder.label(current)} " +
                        "back up to ${com.streamflow.data.QualityLadder.label(next)}"
                )
                // Reaching the user's own preference means the step-down is
                // fully undone; an override equal to the preference would only
                // survive to confuse the next read.
                if (next == ceiling) {
                    com.streamflow.data.AdaptiveQuality.clear()
                } else {
                    com.streamflow.data.AdaptiveQuality.raise(next)
                }
                stepUpRung = next
                stepUpAt = android.os.SystemClock.elapsedRealtime()
                ignoreStallsUntil = stepUpAt + DOWNGRADE_SETTLE_MS
                reExtractInPlace(mediaId, resumeFrom)
                // No toast. The user never asked for the step-down and mostly
                // did not notice it; announcing its reversal would draw
                // attention to a problem that is already over.
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                com.streamflow.data.PlaybackLog.warn(
                    "quality", "step-up failed: ${e.javaClass.simpleName}")
            } finally {
                downgrading = false
            }
        }
    }

    /** A rung that could not survive its probation window is the wrong rung for
     *  this link, and must not be offered again while the link lasts. */
    private fun failProbationIfRecent(now: Long) {
        val rung = stepUpRung ?: return
        if (now - stepUpAt >= com.streamflow.data.BufferHealth.STEP_UP_PROBATION_MS) {
            stepUpRung = null
            return
        }
        com.streamflow.data.PlaybackLog.warn(
            "quality",
            "${com.streamflow.data.QualityLadder.label(rung)} stalled within probation, retiring it")
        failedRungs += rung
        stepUpRung = null
    }

    private fun noteStall() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now < ignoreStallsUntil) return
        failProbationIfRecent(now)
        recentStalls.addLast(now)
        while (recentStalls.isNotEmpty() && now - recentStalls.first() > STALL_WINDOW_MS) {
            recentStalls.removeFirst()
        }
        if (recentStalls.size >= STALL_THRESHOLD) maybeStepDownQuality()
    }

    private fun maybeStepDownQuality() {
        if (downgrading) return
        val player = mediaSession?.player ?: return
        val mediaId = player.currentMediaItem?.mediaId ?: return
        if (!com.streamflow.data.MediaUrl.classify(mediaId).let {
                it == com.streamflow.data.MediaKind.YOUTUBE
            }) return
        // A live stream has no fixed ladder to walk down and re-extracting it
        // would jump the user to the live edge.
        if (try { player.isCurrentMediaItemLive } catch (_: Exception) { false }) return

        // The exact position, taken here on the main thread. lastGoodPositionMs
        // is a 5-second sample that the ticker only writes in STATE_READY — and
        // a stall storm is precisely when ticks land in STATE_BUFFERING instead,
        // so the sample is stale by up to 5 s, or still 0 if the stalling began
        // early. That put the user back at 0:00 of the video they were watching.
        val resumeFrom = try { player.currentPosition } catch (_: Exception) { lastGoodPositionMs }
        // What is actually on screen beats what the device is capable of.
        // DeviceCaps.autoMaxHeight is only ever 720 or 1080, so for a video
        // whose best upload is 360p, "step down" resolved to 720P and
        // re-extracted the identical stream: a toast, an interruption, and no
        // change. videoSize is the height being decoded right now.
        val playingHeight = try { player.videoSize.height } catch (_: Exception) { 0 }

        downgrading = true
        recentStalls.clear()
        serviceScope.launch {
            try {
                val current = if (playingHeight > 0) {
                    com.streamflow.data.QualityLadder.heightToPref(playingHeight)
                } else {
                    resumeQuality()
                }
                val next = com.streamflow.data.QualityLadder.stepDown(
                    current, com.streamflow.data.DeviceCaps.autoMaxHeight
                )
                if (next == null) {
                    com.streamflow.data.PlaybackLog.info(
                        "quality", "stalling at ${com.streamflow.data.QualityLadder.label(current)}, already lowest")
                    // Without this the same three stalls re-enter forever on a
                    // link that stays bad at 360p, each round re-reading three
                    // preferences and writing another log line for no action.
                    ignoreStallsUntil =
                        android.os.SystemClock.elapsedRealtime() + DOWNGRADE_SETTLE_MS
                    return@launch
                }
                com.streamflow.data.PlaybackLog.warn(
                    "quality",
                    "$STALL_THRESHOLD stalls in ${STALL_WINDOW_MS / 1000}s -> " +
                        "${com.streamflow.data.QualityLadder.label(current)} to " +
                        com.streamflow.data.QualityLadder.label(next)
                )
                // Both together: resumeQuality() drops an override whose
                // baseline no longer matches, so setting one without the other
                // would cancel the step-down on the very next read.
                com.streamflow.data.AdaptiveQuality.lower(next, basePreference())
                // Deliberately NOT recorded in failedRungs. That set exists
                // to retire a rung the app CHOSE to climb back to and that
                // then failed; blacklisting the rung a step-down is leaving
                // would make it permanently unreachable and disable the
                // climb back entirely.
                stepUpRung = null
                ignoreStallsUntil = android.os.SystemClock.elapsedRealtime() + DOWNGRADE_SETTLE_MS
                reExtractInPlace(mediaId, resumeFrom)
                // Only claim it after it happened. Announcing first meant that
                // when the re-extract threw — likely, on the bad network that
                // caused this — the user had been told quality was lowered while
                // nothing had changed.
                android.widget.Toast.makeText(
                    this@PlaybackService,
                    "Lowered to ${com.streamflow.data.QualityLadder.label(next)} for a smoother stream",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                com.streamflow.data.PlaybackLog.warn(
                    "quality", "step-down failed: ${e.javaClass.simpleName}")
            } finally {
                downgrading = false
            }
        }
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

        // Retries a timeout, but NOT a removed or geo-blocked video: advance()
        // relies on a permanent failure throwing straight through so it can skip
        // the dead entry instead of stalling the queue on it three times over.
        val q = resumeQuality()
        // Deliberately stingier than the open-a-video path. advance() walks up to
        // three queue entries, so the full budget stacked up: with no signal,
        // one press of "next" on a headset could hold `advancing` for around a
        // hundred seconds, during which next AND previous both did nothing.
        // Before this retry existed the same press failed in under a second.
        val details = com.streamflow.data.ExtractionRetry.run(
            "advance", attempts = 2, waitForNetwork = false
        ) {
            com.streamflow.data.YouTubeRepository().getVideoDetails(url, q)
        }
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

        /** Position is captured every 5 s but written every third capture. */
        private const val SAVE_EVERY_N_TICKS = 3

        /** Three stalls inside a minute is a link that cannot carry this
         *  stream, not a run of bad luck. */
        private const val STALL_THRESHOLD = 3
        private const val STALL_WINDOW_MS = 60_000L
        /** Grace period after a step-down, so the re-prepare's own buffering
         *  cannot be counted as evidence for another one. */
        private const val DOWNGRADE_SETTLE_MS = 20_000L
        /** Grace period after a seek. Long enough to cover the re-buffer that a
         *  skip causes, short enough that a genuinely failing link is still
         *  caught within the same minute. */
        private const val SEEK_SETTLE_MS = 6_000L
        /**
         * How often the watchdog looks at the buffer while playback is stuck.
         *
         * There is no fixed buffering deadline any more: the limits live in
         * BufferHealth and are expressed against how much data has actually
         * arrived, so a stream that keeps filling keeps its time and one that
         * has gone silent is caught in twelve seconds instead of thirty. This
         * poll runs only while stuck, and stops the moment playback resolves.
         */
        private const val WATCH_POLL_MS = 2_000L
    }
}
