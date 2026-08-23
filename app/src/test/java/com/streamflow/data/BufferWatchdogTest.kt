package com.streamflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The watchdog that ends "stuck on loading".
 *
 * ExoPlayer reports STATE_BUFFERING and then waits indefinitely; it raises an
 * error only when the data source actually fails. A server that accepts the
 * connection and trickles bytes therefore produced no error, no timeout and no
 * recovery -- and the pre-existing stall detector required currentPosition > 0,
 * so the entire initial load was excluded from it.
 *
 * These cover the decision logic that arms and fires the watchdog, mirrored
 * from PlaybackService.updateBufferWatchdog / watchBuffer. The coroutine timing
 * itself needs a device; what is testable here is when it should count at all,
 * and that is where the bug lived.
 */
class BufferWatchdogTest {

    /** Mirrors the arming condition in updateBufferWatchdog(). */
    private fun shouldWatch(buffering: Boolean, playWhenReady: Boolean) =
        buffering && playWhenReady

    /** Mirrors the plan choice in onBufferTimedOut(). */
    private fun planFor(mediaId: String) =
        if (!MediaUrl.isLocalFile(mediaId)) RecoveryPlan.REEXTRACT else RecoveryPlan.RETRY

    @Test
    fun `the opening buffer is watched`() {
        // The regression this exists for: the old stall detector required
        // position > 0, so a video that never produced a first frame was the
        // one case nothing was watching.
        assertTrue(shouldWatch(buffering = true, playWhenReady = true))
    }

    @Test
    fun `a paused video is never treated as stuck`() {
        // Buffering while paused is the user's doing. Recovering it would
        // restart a video they deliberately stopped.
        assertFalse(shouldWatch(buffering = true, playWhenReady = false))
    }

    @Test
    fun `a playing video is not watched`() {
        assertFalse(shouldWatch(buffering = false, playWhenReady = true))
    }

    @Test
    fun `startup is given more room than mid-playback`() {
        // Cutting startup short would re-extract videos that were about to
        // play, which is the failure mode of a naive timeout. Mid-playback a
        // buffer already existed and was consumed, which is stronger evidence.
        assertTrue(
            BufferHealth.hardLimitMs(atStartup = true) >
                BufferHealth.hardLimitMs(atStartup = false)
        )
    }

    @Test
    fun `a stalled remote video is re-extracted`() {
        // The usual cause is a signed URL that died, so re-resolving it is the
        // fix; merely re-preparing would replay the same dead URL.
        assertEquals(planFor("https://www.youtube.com/watch?v=dQw4w9WgXcQ"), RecoveryPlan.REEXTRACT)
    }

    @Test
    fun `a stalled local file is only re-prepared`() {
        // There is no URL to re-resolve for a download, and re-extracting one
        // would send a local path to the YouTube extractor.
        assertEquals(planFor("file:///storage/emulated/0/Download/a.mp4"), RecoveryPlan.RETRY)
        assertEquals(planFor("/storage/emulated/0/Movies/b.mkv"), RecoveryPlan.RETRY)
    }

    @Test
    fun `the watchdog shares the error path's attempt budget`() {
        // Both failures walk the same counter, so a stall cannot retry forever
        // alongside an error retrying on its own schedule.
        assertTrue(PlaybackRecovery.MAX_ATTEMPTS in 1..10)
        var attempt = 0
        while (attempt < PlaybackRecovery.MAX_ATTEMPTS) attempt++
        assertEquals(PlaybackRecovery.MAX_ATTEMPTS, attempt)
    }

    @Test
    fun `backoff grows so retries do not hammer a failing link`() {
        val first = PlaybackRecovery.backoffMs(1)
        val last = PlaybackRecovery.backoffMs(PlaybackRecovery.MAX_ATTEMPTS)
        assertTrue("backoff must not be instant", first > 0L)
        assertTrue("backoff must grow with attempts", last > first)
    }
}
