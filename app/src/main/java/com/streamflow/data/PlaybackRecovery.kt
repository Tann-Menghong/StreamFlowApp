package com.streamflow.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What to do about a playback error.
 *
 * Before this existed the app had NO Player.Listener.onPlayerError anywhere on
 * the main player: ExoPlayer reported the failure, moved to STATE_IDLE and
 * stopped, and nothing ever called prepare() again. A two-second tunnel, a
 * Wi-Fi/mobile handover, or a stream URL quietly expiring while the screen was
 * off all produced the same dead end — a frozen notification and a video the
 * user had to restart by hand.
 *
 * The three plans below are deliberately different actions, because the three
 * failure modes need different treatment:
 *
 *  RETRY      the bytes did not arrive (timeout, connection reset, 5xx). The
 *             stream itself is still valid, so re-preparing the SAME media item
 *             at the same position is enough once the network is back.
 *
 *  REEXTRACT  the server actively rejected us (401/403/410, or a 404 on a URL
 *             that worked a minute ago). YouTube googlevideo URLs are signed and
 *             expire, and they are also bound to the requesting IP — so a
 *             Wi-Fi to mobile switch invalidates them mid-playback. Retrying the
 *             same URL can only fail again; the video has to be re-extracted.
 *             This is the single most likely cause of "I left the app and the
 *             audio just stopped".
 *
 *  FATAL      the media is genuinely unplayable here (malformed container, no
 *             usable decoder, cleartext blocked). Retrying burns battery to
 *             reach the same conclusion, so we stop and tell the user.
 */
enum class RecoveryPlan { RETRY, REEXTRACT, FATAL }

object PlaybackRecovery {

    /** Hard ceiling on automatic attempts, so a permanently dead stream can
     *  never become an infinite retry loop draining the battery in a pocket. */
    const val MAX_ATTEMPTS = 5

    // media3 PlaybackException.ERROR_CODE_* values. Declared here as plain Ints
    // so this file stays pure Kotlin and the unit tests run on the JVM with no
    // device and no Robolectric — the same constraint CustomTabs/SearchHistory
    // are written under.
    const val CODE_TIMEOUT = 1003
    const val CODE_IO_UNSPECIFIED = 2000
    const val CODE_IO_NETWORK_CONNECTION_FAILED = 2001
    const val CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002
    const val CODE_IO_INVALID_HTTP_CONTENT_TYPE = 2003
    const val CODE_IO_BAD_HTTP_STATUS = 2004
    const val CODE_IO_FILE_NOT_FOUND = 2005
    const val CODE_IO_NO_PERMISSION = 2006
    const val CODE_IO_CLEARTEXT_NOT_PERMITTED = 2007
    const val CODE_IO_READ_POSITION_OUT_OF_RANGE = 2008

    /**
     * The OS took our hardware decoder away and gave it to something with a
     * higher priority -- an incoming video call, the camera, another player.
     *
     * media3 1.4 introduced this code; before it, the same event surfaced as a
     * generic decoder failure. Value confirmed against
     * PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED in the 1.4.1
     * artifact rather than assumed, because it is declared here as a plain Int
     * so these tests can run on the JVM with no device.
     *
     * It is the most recoverable failure in this whole file: nothing is wrong
     * with the video, the network, or the URL. The codec simply has to be
     * acquired again, which is exactly what prepare() does.
     */
    const val CODE_DECODING_RESOURCES_RECLAIMED = 4006

    /** No HTTP status was available (not an HTTP failure, or media3 did not say). */
    const val NO_STATUS = 0

    /**
     * @param errorCode  PlaybackException.errorCode
     * @param httpStatus HTTP response code if the cause was an
     *                   InvalidResponseCodeException, else [NO_STATUS]
     * @param isRemote   false for file:// and content:// media, where there is
     *                   no stream to re-extract and no network to wait for
     */
    fun plan(errorCode: Int, httpStatus: Int = NO_STATUS, isRemote: Boolean = true): RecoveryPlan {
        // Checked BEFORE the local-media shortcut below. Losing the decoder is a
        // device-side event with nothing to do with where the media came from,
        // and a downloaded file is if anything the likeliest thing to still be
        // playing when a call arrives. Treating it as fatal there would tell
        // someone their own downloaded video is unplayable on their own phone.
        if (errorCode == CODE_DECODING_RESOURCES_RECLAIMED) return RecoveryPlan.RETRY

        if (!isRemote) return RecoveryPlan.FATAL

        // An explicit rejection beats the generic code: media3 reports every bad
        // HTTP status as CODE_IO_BAD_HTTP_STATUS, but 403 and 503 need opposite
        // responses — one means "this URL is dead", the other "come back later".
        when (httpStatus) {
            401, 403, 410, 404 -> return RecoveryPlan.REEXTRACT
            408, 429 -> return RecoveryPlan.RETRY
            in 500..599 -> return RecoveryPlan.RETRY
        }

        return when (errorCode) {
            CODE_TIMEOUT,
            CODE_IO_UNSPECIFIED,
            CODE_IO_NETWORK_CONNECTION_FAILED,
            CODE_IO_NETWORK_CONNECTION_TIMEOUT -> RecoveryPlan.RETRY

            // A signed stream URL that has expired usually surfaces as a 403, but
            // some CDN edges answer 404 and media3 maps that to FILE_NOT_FOUND.
            // On a remote item that is an expiry, not a missing local file.
            CODE_IO_FILE_NOT_FOUND -> RecoveryPlan.REEXTRACT

            // Range request rejected / HTML error page served instead of media:
            // both mean the URL we hold is no longer serving this stream.
            CODE_IO_READ_POSITION_OUT_OF_RANGE,
            CODE_IO_INVALID_HTTP_CONTENT_TYPE -> RecoveryPlan.REEXTRACT

            // A bad status we did not recognise above. Re-extracting is the safer
            // guess than hammering a URL the server just refused.
            CODE_IO_BAD_HTTP_STATUS -> RecoveryPlan.REEXTRACT

            else -> RecoveryPlan.FATAL
        }
    }

    /**
     * Exponential backoff, capped.
     *
     * The cap matters as much as the growth: without it the 5th attempt would
     * land 16 s out and the user would assume the app had died. With it the
     * whole recovery sequence finishes inside about 30 s, which is roughly how
     * long someone will wait before reaching for the phone.
     *
     * attempt 1 -> 1 s, 2 -> 2 s, 3 -> 4 s, 4 -> 8 s, 5+ -> 12 s
     */
    fun backoffMs(attempt: Int): Long {
        if (attempt <= 1) return 1_000L
        val shifted = 1_000L shl (attempt - 1).coerceAtMost(20)
        return shifted.coerceAtMost(12_000L)
    }

    // ── UI-observable recovery state ─────────────────────────────────────────
    // The player screen shows "Reconnecting (2/5)" instead of an anonymous
    // spinner, so a stall the app is actively working on looks different from
    // one it has given up on. This is real state, not a progress animation.

    data class State(
        val active: Boolean = false,
        val attempt: Int = 0,
        val waitingForNetwork: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /**
     * Set when automatic recovery has given up, so the player screen can say so
     * instead of leaving a frozen frame and a spinner that never resolves.
     * Silent failure is the worst of the three outcomes: the user cannot tell
     * whether to wait, retry, or restart the app.
     */
    private val _fatal = MutableStateFlow<String?>(null)
    val fatal: StateFlow<String?> = _fatal

    fun onAttempt(attempt: Int, waitingForNetwork: Boolean) {
        _fatal.value = null
        _state.value = State(active = true, attempt = attempt, waitingForNetwork = waitingForNetwork)
    }

    fun onRecovered() {
        if (_state.value.active) _state.value = State()
        _fatal.value = null
    }

    fun onGaveUp(plan: RecoveryPlan, exhausted: Boolean) {
        _state.value = State()
        _fatal.value = when {
            exhausted -> "Playback stopped — the connection did not come back. Tap to try again."
            plan == RecoveryPlan.FATAL -> "This video cannot be played on this device."
            else -> "Playback stopped. Tap to try again."
        }
    }

    /** The media session never connected, so there is nothing to play into.
     *  A different failure from "this video is broken", and it deserves a
     *  different sentence — telling the user their video is unplayable when the
     *  real problem is a service binding sends them chasing the wrong thing. */
    fun onSessionUnavailable() {
        _state.value = State()
        _fatal.value = "Could not connect to the player service. Tap to try again."
    }

    /** Cleared when the user acts on the message, or a new video starts. */
    fun clearFatal() { _fatal.value = null }
}

/**
 * Single-claim guard for end-of-video advancement.
 *
 * Two independent things can advance to the next video: the player screen (which
 * runs a visible 5-second countdown with a Cancel button) and PlaybackService
 * (which has to take over when the screen is gone or the app is backgrounded).
 * Without arbitration a video ending just as the user pressed Home would be
 * advanced twice — skipping an episode.
 *
 * Whoever claims the finished video id first owns the advance; the other side
 * sees false and stands down.
 */
object AutoAdvance {
    private val lock = Any()
    private var claimedFor: String? = null

    /** True exactly once per finished video id. */
    fun claim(mediaId: String): Boolean = synchronized(lock) {
        if (claimedFor == mediaId) false
        else { claimedFor = mediaId; true }
    }

    /** Called when a new video starts, so replaying the same video later can
     *  still auto-advance when IT ends. */
    fun reset() = synchronized(lock) { claimedFor = null }

    // ── Sequence continuation ────────────────────────────────────────────────
    //
    // PlaybackService.advance() already skips up to three dead entries before
    // giving up, because a queue full of expired links must not spin. The
    // player SCREEN's countdown had no equivalent: it popped one entry,
    // navigated to it, and if that video would not extract -- deleted, private,
    // geo-blocked, a link that rotted in a queue saved last week -- the user
    // landed on an error page with "Go back" and "Retry" as the only options.
    //
    // popNext() had already consumed the entry, so the rest of the queue was
    // still there and nothing could reach it. One dead video stopped the whole
    // sequence, which is the failure a queue exists to prevent.
    //
    // These three calls let the screen tell the difference between a video the
    // USER opened (leave the error alone; they asked for this one) and one the
    // SEQUENCE opened (skip it and keep going, up to a limit).

    /** Consecutive automatic skips allowed before the sequence stops and asks.
     *  Bounded for the same reason the service's loop is: a queue of dead links
     *  must not silently burn through fifty extractions. */
    const val MAX_SEQUENCE_SKIPS = 3

    private var autoTarget: String? = null
    private var sequenceFailures = 0

    /** The sequence is opening [url] on its own. */
    fun markAuto(url: String) = synchronized(lock) { autoTarget = url }

    /**
     * Was [url] opened by the sequence rather than by the user?
     *
     * Consumes the mark, so a later manual retry of the same video is treated
     * as the user's choice and left alone.
     */
    fun consumeAuto(url: String): Boolean = synchronized(lock) {
        if (autoTarget == url) { autoTarget = null; true } else false
    }

    /** @return true while the sequence may still skip to another video. */
    fun onSequenceFailure(): Boolean = synchronized(lock) {
        sequenceFailures++
        sequenceFailures <= MAX_SEQUENCE_SKIPS
    }

    /** Anything that plays proves the sequence is healthy again. */
    fun onSequenceProgress() = synchronized(lock) { sequenceFailures = 0 }

    /**
     * Release the claim on a video that has started over from the beginning.
     *
     * reset() only ran on a media-item transition, but replaying the video you
     * just finished — a seek back to zero, or repeat-one — fires no transition
     * at all. The claim from the first play-through therefore stood, and the
     * second time it ended nothing advanced.
     */
    fun releaseIfClaimed(mediaId: String) = synchronized(lock) {
        if (claimedFor == mediaId) claimedFor = null
    }
}

/**
 * Whether a player screen is currently on screen and able to drive its own
 * end-of-video countdown. PlaybackService reads this to decide whether to
 * advance by itself — audio playing under the mini player, or with the screen
 * off, has no countdown to wait for.
 *
 * This is a COUNTER, not a boolean, and the difference was a real bug.
 *
 * Player -> Player navigation (autoplay, tapping a related video) pops the old
 * back-stack entry, but its composition survives its exit transition while the
 * incoming screen is already composing. So a plain boolean ran in this order:
 *
 *      incoming screen composes  ->  active = true
 *      outgoing screen disposes  ->  active = false
 *
 * leaving the flag false while a player screen was very much on screen. From
 * that point on the service stopped standing down, so the 5-second countdown
 * with its Cancel button never appeared again for the rest of the session, and
 * auto-PiP stopped firing on the Home press.
 *
 * A depth counter is immune to the ordering: two screens overlapping reads as
 * two, and only the last one to leave takes it back to zero. This is the same
 * reasoning — and the same fix — as AppForeground's started-activity count.
 */
object PlayerUiPresence {

    private val depth = java.util.concurrent.atomic.AtomicInteger(0)

    val active: Boolean get() = depth.get() > 0

    fun enter() { depth.incrementAndGet() }

    /** Clamped at zero: an unbalanced dispose must not drive the count
     *  negative, which would report "no player UI" forever afterwards. */
    fun exit() { depth.updateAndGet { if (it > 0) it - 1 else 0 } }
}
