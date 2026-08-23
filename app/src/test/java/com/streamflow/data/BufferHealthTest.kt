package com.streamflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Buffer occupancy as the signal, replacing two fixed timers and a one-way
 * quality ratchet.
 *
 * The cases that matter here are the ones a stopwatch gets wrong: a dead
 * connection that should be given up on early, a slow-but-filling stream that
 * should be given its full time, a link starving before the user has seen a
 * single stall, and a link that has recovered and should get its quality back.
 */
class BufferHealthTest {

    // ── Stuck vs slow ────────────────────────────────────────────────────────

    @Test
    fun `a connection delivering nothing is given up on well before the ceiling`() {
        // The whole point of measuring data instead of time: waiting the full
        // 45 s for a source that has produced nothing in 12 s is 33 s of a
        // spinner for a foregone conclusion.
        assertTrue(
            BufferHealth.exhausted(
                waitedMs = 12_000L, sinceProgressMs = 12_000L, atStartup = true
            )
        )
        assertTrue(BufferHealth.NO_PROGRESS_MS < BufferHealth.STARTUP_HARD_MS)
    }

    @Test
    fun `a slow but filling stream keeps its time`() {
        // A high-bitrate stream over a weak link is still on its way to
        // playing. Cutting it off is the failure mode of a naive timeout, and
        // is exactly what a flat 30 s deadline did.
        assertFalse(
            BufferHealth.exhausted(
                waitedMs = 28_000L, sinceProgressMs = 1_500L, atStartup = true
            )
        )
    }

    @Test
    fun `a stream that trickles forever still hits the ceiling`() {
        // Progress alone must not buy unlimited time, or a source dribbling one
        // byte per poll would hold the player open indefinitely -- the same
        // endless load, reached by a different route.
        assertTrue(
            BufferHealth.exhausted(
                waitedMs = BufferHealth.STARTUP_HARD_MS, sinceProgressMs = 0L, atStartup = true
            )
        )
        assertTrue(
            BufferHealth.exhausted(
                waitedMs = BufferHealth.MIDPLAY_HARD_MS, sinceProgressMs = 0L, atStartup = false
            )
        )
    }

    @Test
    fun `mid-playback is judged sooner than startup`() {
        val waited = BufferHealth.MIDPLAY_HARD_MS
        assertTrue(BufferHealth.exhausted(waited, sinceProgressMs = 0L, atStartup = false))
        assertFalse(BufferHealth.exhausted(waited, sinceProgressMs = 0L, atStartup = true))
    }

    @Test
    fun `buffer noise does not count as progress`() {
        // Sub-epsilon movement is rounding, not data. Counting it would reset
        // the idle timer forever on a stream that is going nowhere.
        assertFalse(BufferHealth.progressed(5_000L, 5_000L))
        assertFalse(BufferHealth.progressed(5_000L, 5_000L + BufferHealth.PROGRESS_EPSILON_MS))
        assertTrue(BufferHealth.progressed(5_000L, 9_000L))
    }

    @Test
    fun `a shrinking buffer is not progress`() {
        assertFalse(BufferHealth.progressed(8_000L, 2_000L))
    }

    // ── Starving: acting before the stall, not after three of them ───────────

    @Test
    fun `a draining buffer is caught before the first stall`() {
        val draining = listOf(9_000L, 2_500L, 1_800L, 900L)
        assertTrue(BufferHealth.starving(draining))
    }

    @Test
    fun `one bad sample is not starvation`() {
        // A single dip is a hiccup. Acting on it would re-extract healthy
        // videos, which is the cost this threshold exists to avoid.
        assertFalse(BufferHealth.starving(listOf(1_000L)))
        assertFalse(BufferHealth.starving(listOf(30_000L, 30_000L, 1_000L)))
    }

    @Test
    fun `a full buffer is never starving`() {
        assertFalse(BufferHealth.starving(listOf(40_000L, 38_000L, 41_000L)))
    }

    // ── Comfortable: the evidence for giving quality back ────────────────────

    @Test
    fun `the two verdicts cannot overlap`() {
        // The gap between starving and comfortable IS the hysteresis. If they
        // touched, a link sitting between them would flap between rungs, each
        // flap costing a re-extract of a video that is playing.
        assertTrue(BufferHealth.COMFORTABLE_AHEAD_MS > BufferHealth.STARVING_AHEAD_MS * 2)
        assertFalse(BufferHealth.comfortable(BufferHealth.STARVING_AHEAD_MS))
        assertTrue(BufferHealth.comfortable(BufferHealth.COMFORTABLE_AHEAD_MS))
    }

    @Test
    fun `restoring quality takes far longer to justify than lowering it`() {
        // Stepping down costs a re-extract the user already needed; stepping up
        // costs one they did not. Being slow to restore is the cheaper mistake.
        val downSeconds = BufferHealth.STARVING_SAMPLES * 5
        val upSeconds = BufferHealth.COMFORTABLE_SAMPLES * 5
        assertTrue("step-up must be much slower to trigger", upSeconds > downSeconds * 4)
    }

    @Test
    fun `with no step-down in force there is nothing to restore`() {
        // Quality is already whatever the user asked for; re-extracting a
        // perfectly healthy video to change nothing is pure cost.
        assertFalse(
            BufferHealth.readyToStepUp(
                comfortableStreak = 999, hasOverride = false, remainingMs = 600_000L
            )
        )
    }

    @Test
    fun `a brief calm patch does not restore quality`() {
        assertFalse(
            BufferHealth.readyToStepUp(
                comfortableStreak = BufferHealth.COMFORTABLE_SAMPLES - 1,
                hasOverride = true,
                remainingMs = 600_000L
            )
        )
    }

    @Test
    fun `quality is never restored at the end of a video`() {
        // The interruption would cost more than the remaining seconds are
        // worth, and an unknown duration (a live stream) has no ladder anyway.
        assertFalse(
            BufferHealth.readyToStepUp(
                comfortableStreak = BufferHealth.COMFORTABLE_SAMPLES,
                hasOverride = true,
                remainingMs = 20_000L
            )
        )
        assertFalse(
            BufferHealth.readyToStepUp(
                comfortableStreak = BufferHealth.COMFORTABLE_SAMPLES,
                hasOverride = true,
                remainingMs = -1L
            )
        )
    }

    @Test
    fun `a sustained healthy link does restore quality`() {
        assertTrue(
            BufferHealth.readyToStepUp(
                comfortableStreak = BufferHealth.COMFORTABLE_SAMPLES,
                hasOverride = true,
                remainingMs = 600_000L
            )
        )
    }

    // ── What the loading ring reports ───────────────────────────────

    @Test
    fun `an empty buffer reads as no progress`() {
        assertEquals(0f, BufferHealth.startupProgress(0L), 0.001f)
    }

    @Test
    fun `progress climbs with the buffer`() {
        // The regression this exists for: fed bufferedPercentage, the ring
        // showed 0.008 for twenty seconds of a forty-minute video and looked
        // frozen. Against what is actually needed to start, the same buffer is
        // a full ring.
        val quarter = BufferHealth.startupProgress(BufferHealth.START_TARGET_MS / 4)
        val half = BufferHealth.startupProgress(BufferHealth.START_TARGET_MS / 2)
        assertTrue(quarter > 0f)
        assertTrue(half > quarter)
    }

    @Test
    fun `progress never exceeds full`() {
        // A buffer well past the start target must not drive the arc past 360
        // degrees, and a negative reading must not drive it backwards.
        assertEquals(1f, BufferHealth.startupProgress(BufferHealth.START_TARGET_MS * 20), 0.001f)
        assertEquals(0f, BufferHealth.startupProgress(-5_000L), 0.001f)
    }

    @Test
    fun `the display target is above the player's own start threshold`() {
        // DefaultLoadControl starts playback at 800 ms buffered. A display
        // target at or below that would show a full ring before playback began,
        // which is the frozen-looking indicator again with a different value.
        assertTrue(BufferHealth.START_TARGET_MS > 800L)
    }

    // ── The ladder climbing back up ──────────────────────────────────────────

    @Test
    fun `step-up never exceeds the user's own preference`() {
        // The app may only undo what it did. No amount of measured bandwidth
        // authorises overriding a deliberate choice of 480p.
        assertNull(QualityLadder.stepUp("480P", ceiling = "480P", autoMaxHeight = 1080))
        assertEquals("480P", QualityLadder.stepUp("360P", ceiling = "480P", autoMaxHeight = 1080))
    }

    @Test
    fun `step-up resolves AUTO against what the device can decode`() {
        // On a phone whose AUTO ceiling is 720p, climbing back to AUTO must
        // stop at 720p rather than extracting a 1080p stream it cannot decode.
        assertEquals("720P", QualityLadder.stepUp("480P", ceiling = QualityLadder.AUTO, autoMaxHeight = 720))
        assertNull(QualityLadder.stepUp("720P", ceiling = QualityLadder.AUTO, autoMaxHeight = 720))
    }

    @Test
    fun `step-up and step-down are inverses`() {
        // A rung reached by stepping down must be exactly the rung stepping up
        // returns to, or the pair would drift the session downward over time.
        val down = QualityLadder.stepDown("720P", autoMaxHeight = 1080)
        assertEquals("480P", down)
        assertEquals("720P", QualityLadder.stepUp(down!!, ceiling = "720P", autoMaxHeight = 1080))
    }

    @Test
    fun `an unknown rung climbs nowhere`() {
        assertNull(QualityLadder.stepUp("144P", ceiling = "1080P", autoMaxHeight = 1080))
    }
}
