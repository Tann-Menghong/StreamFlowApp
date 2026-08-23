package com.streamflow.data

/**
 * Buffer-occupancy analysis: the difference between "slow" and "stopped".
 *
 * The watchdog added alongside this file ended the worst case — a player parked
 * in STATE_BUFFERING forever, because ExoPlayer has no buffering timeout of its
 * own. But a flat deadline answers a question the player can already answer
 * better. Two very different streams look identical to a stopwatch:
 *
 *   a) the connection died mid-handshake. Nothing has arrived and nothing will.
 *      Waiting the full 30 s is 30 s of a spinner for a foregone conclusion.
 *
 *   b) a 1080p stream over a weak link. Bytes ARE arriving, steadily, just not
 *      fast enough to reach bufferForPlaybackMs yet. Cutting this off at 30 s
 *      re-extracts a video that was seconds from playing.
 *
 * So the deadline here is an idle timeout on DATA, not on time: as long as the
 * buffer keeps growing the app keeps waiting, and a hard ceiling still bounds
 * the whole thing. That is the difference between finding the cause and raising
 * the timeout until the symptom is someone else's problem.
 *
 * The same measurement answers the second question the app could not answer.
 * The v6.9.0 step-down waits for three STALLS — i.e. it acts only after the
 * user has watched the video stop three times. But a buffer draining steadily
 * toward zero says the link cannot carry this bitrate BEFORE the first stall,
 * and a buffer sitting comfortably full for two minutes says a step-down taken
 * during a dead spot is no longer justified. One number, read on a ticker that
 * was already running, drives both directions.
 *
 * Pure Kotlin with no media3 or Android types, so all of it runs in JVM unit
 * tests — the same constraint QualityLadder and PlaybackRecovery are written
 * under, and the reason this logic lives here rather than inline in the service.
 */
object BufferHealth {

    // ── Stuck-vs-slow, used by the buffering watchdog ────────────────────────

    /** Buffer growth smaller than this over a poll is noise, not progress. */
    const val PROGRESS_EPSILON_MS = 250L

    /**
     * How long the buffer may fail to grow at all before the app stops
     * believing the stream. Deliberately much shorter than the hard ceilings:
     * a source that has delivered nothing for this long is not slow, it is gone,
     * and recovering it 12 s in beats recovering it 45 s in.
     */
    const val NO_PROGRESS_MS = 12_000L

    /**
     * Ceilings for a stream that keeps trickling just enough to look alive.
     * Startup is the more generous of the two: a cold connection, a redirect
     * chain and the first segment of a high-bitrate stream are genuinely slow
     * on a weak link. Mid-playback a buffer already existed and was consumed,
     * which is a much stronger signal.
     */
    const val STARTUP_HARD_MS = 45_000L
    const val MIDPLAY_HARD_MS = 25_000L

    fun hardLimitMs(atStartup: Boolean): Long =
        if (atStartup) STARTUP_HARD_MS else MIDPLAY_HARD_MS

    /** Did the buffer genuinely grow between two polls? */
    fun progressed(previousAheadMs: Long, currentAheadMs: Long): Boolean =
        currentAheadMs > previousAheadMs + PROGRESS_EPSILON_MS

    /**
     * Should the app stop waiting for this buffer?
     *
     * @param waitedMs        total time in STATE_BUFFERING
     * @param sinceProgressMs time since the buffer last grew
     */
    fun exhausted(waitedMs: Long, sinceProgressMs: Long, atStartup: Boolean): Boolean =
        sinceProgressMs >= NO_PROGRESS_MS || waitedMs >= hardLimitMs(atStartup)

    // ── What the loading ring should actually show ───────────────────────

    /**
     * Buffer ahead at which playback reliably begins, for DISPLAY only.
     *
     * Not a control threshold -- ExoPlayer's own bufferForPlaybackMs decides
     * when playback starts and is untouched by this. This is the denominator
     * for the ring, chosen slightly above the player's real trigger so the arc
     * is still climbing when playback begins rather than sitting full and
     * waiting.
     */
    const val START_TARGET_MS = 2_500L

    /**
     * Progress toward being able to play, 0f..1f.
     *
     * The indicator was previously fed `bufferedPercentage`, which is the
     * fraction of the WHOLE VIDEO that is buffered. During the opening buffer of
     * a forty-minute video, twenty seconds of data is 0.8% -- so the ring sat at
     * zero and looked frozen through exactly the wait it exists to explain, and
     * the number it showed answered a question nobody was asking. How far
     * through the video the buffer reaches is irrelevant while you are waiting
     * to start; how close the buffer is to being playable is the whole question.
     *
     * Both quantities are real. This one is the one the user is waiting on.
     */
    fun startupProgress(aheadMs: Long): Float =
        (aheadMs.toFloat() / START_TARGET_MS).coerceIn(0f, 1f)

    // ── Sustained health, used by the quality ladder ─────────────────────────

    /** Buffer ahead of the playhead below this and the next hiccup is a stall. */
    const val STARVING_AHEAD_MS = 3_000L

    /**
     * Buffer ahead above this and the link is carrying the stream with room to
     * spare. Well clear of STARVING so the two verdicts cannot alternate on a
     * link sitting between them — that gap IS the hysteresis.
     */
    const val COMFORTABLE_AHEAD_MS = 20_000L

    /** Consecutive 5 s samples. Three starving samples is 15 s of a buffer that
     *  never recovers — acted on before the user sees the stall it predicts. */
    const val STARVING_SAMPLES = 3

    /**
     * Two minutes of comfort before undoing a step-down.
     *
     * Far longer than the 15 s that triggers one, and deliberately so: stepping
     * down costs a re-extract the user already needed, stepping back up costs
     * one they did not. Being slow to restore quality is a much cheaper mistake
     * than flapping between rungs on a link that is merely intermittent.
     */
    const val COMFORTABLE_SAMPLES = 24

    /** Never re-extract to raise quality this close to the end of a video —
     *  the interruption would cost more than the remaining seconds are worth. */
    const val MIN_REMAINING_FOR_STEP_UP_MS = 60_000L

    /** The link cannot sustain the current bitrate, on the evidence of the
     *  buffer rather than of stalls the user has already sat through. */
    fun starving(recentAheadMs: List<Long>): Boolean =
        recentAheadMs.size >= STARVING_SAMPLES &&
            recentAheadMs.takeLast(STARVING_SAMPLES).all { it < STARVING_AHEAD_MS }

    fun comfortable(aheadMs: Long): Boolean = aheadMs >= COMFORTABLE_AHEAD_MS

    /**
     * @param comfortableStreak consecutive comfortable samples
     * @param hasOverride       a step-down is currently in force; with none
     *                          there is nothing to undo and quality is already
     *                          whatever the user asked for
     * @param remainingMs       time left in the video, or a negative value when
     *                          unknown (live streams have no ladder to climb)
     */
    fun readyToStepUp(comfortableStreak: Int, hasOverride: Boolean, remainingMs: Long): Boolean =
        hasOverride &&
            comfortableStreak >= COMFORTABLE_SAMPLES &&
            remainingMs >= MIN_REMAINING_FOR_STEP_UP_MS

    /**
     * How long a rung has to survive after a step-up before it counts as
     * proven. Stalling inside this window means the extra bitrate was the
     * cause, so that rung is retired for the session instead of being tried
     * again every two minutes for the rest of the video.
     */
    const val STEP_UP_PROBATION_MS = 60_000L
}
