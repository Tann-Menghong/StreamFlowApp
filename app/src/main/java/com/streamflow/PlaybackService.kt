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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    private var boostGainMb = 0
    private var audioSessionId = 0
    private var equalizer: android.media.audiofx.Equalizer? = null
    private var eqPresetName = "OFF"

    // Apply the user's chosen equalizer preset (matched by name — indices vary per device)
    private fun applyEq() {
        try {
            equalizer?.release()
            equalizer = null
            if (eqPresetName == "OFF" || audioSessionId == 0) return
            val eq = android.media.audiofx.Equalizer(0, audioSessionId)
            val idx = (0 until eq.numberOfPresets).firstOrNull {
                eq.getPresetName(it.toShort()).equals(eqPresetName, ignoreCase = true)
            }
            if (idx == null) { eq.release(); return }
            eq.usePreset(idx.toShort())
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
        val dsf = OkHttpDataSource.Factory(httpClient)

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
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 20_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 800,
                /* bufferForPlaybackAfterRebufferMs = */ 1_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        audioSessionId = player.audioSessionId
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
        serviceScope.launch {
            (application as StreamFlowApp).prefs.volumeBoost.collect { v ->
                boostGainMb = v.toIntOrNull() ?: 0
                applyBoost()
            }
        }
        serviceScope.launch {
            (application as StreamFlowApp).prefs.eqPreset.collect { v ->
                eqPresetName = v
                applyEq()
            }
        }

        // Tapping the media notification / lock-screen card opens the app
        val sessionActivity = android.app.PendingIntent.getActivity(
            this, 0,
            android.content.Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                        .build()
                    return MediaSession.ConnectionResult.accept(
                        sessionCommands,
                        Player.Commands.Builder().addAllCommands().build()
                    )
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
                            val last = app.database.historyDao().getAll()
                                .first().firstOrNull() ?: throw Exception("no history")
                            val details = com.streamflow.data.YouTubeRepository()
                                .getVideoDetails(last.url)
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
}
