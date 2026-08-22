package com.streamflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The step-down has to move to a rung the extractor will actually resolve
 * differently. Every case here produced a real no-op in v6.9.0: a toast, a
 * cache invalidation and a re-prepare that handed back the identical stream.
 */
class StepDownHeightTest {

    /** What PlaybackService now does: read the height being decoded and derive
     *  the current rung from that, rather than from the device ceiling. */
    private fun stepDownFromPlaying(playingHeight: Int, deviceCeiling: Int): String? {
        val current = QualityLadder.heightToPref(playingHeight)
        return QualityLadder.stepDown(current, deviceCeiling)
    }

    @Test
    fun `a 360p-only upload is already at the bottom`() {
        // The bug: DeviceCaps.autoMaxHeight is only ever 720 or 1080, so
        // stepDown(AUTO, 1080) answered "720P" for a video whose best stream is
        // 360p. Re-extracting at 720P resolved to the same 360p file.
        assertNull(stepDownFromPlaying(playingHeight = 360, deviceCeiling = 1080))
    }

    @Test
    fun `a 480p upload steps to 360p, not to 720p`() {
        assertEquals("360P", stepDownFromPlaying(playingHeight = 480, deviceCeiling = 1080))
    }

    @Test
    fun `a video playing at the ceiling still steps down normally`() {
        assertEquals("720P", stepDownFromPlaying(playingHeight = 1080, deviceCeiling = 1080))
        assertEquals("480P", stepDownFromPlaying(playingHeight = 720, deviceCeiling = 720))
    }

    @Test
    fun `an odd height rounds down to the rung it belongs to`() {
        // 1440p and other non-ladder heights must not fall off the ladder.
        assertEquals("720P", stepDownFromPlaying(playingHeight = 1440, deviceCeiling = 1080))
        assertEquals("360P", stepDownFromPlaying(playingHeight = 540, deviceCeiling = 1080))
    }

    @Test
    fun `unknown height falls back to the preference path`() {
        // videoSize is 0 before the first frame is decoded; the service then
        // uses resumeQuality() and the old ceiling-based reasoning, which is
        // still correct when nothing better is known.
        assertEquals("720P", QualityLadder.stepDown(QualityLadder.AUTO, 1080))
    }
}

/**
 * The override that a stall storm installs must not outlive its justification.
 * Mirrors the guard in PlaybackService.resumeQuality().
 */
class OverrideBaselineTest {

    private class Session {
        var override: String? = null
        var baseline: String? = null

        fun resolve(base: String): String {
            if (override != null && base != baseline) {
                override = null
                baseline = null
            }
            return override?.let { QualityLadder.cap(base, it) } ?: base
        }

        fun stepDownTo(next: String, base: String) {
            override = next
            baseline = base
        }
    }

    @Test
    fun `the override holds while the preference is unchanged`() {
        val s = Session()
        s.stepDownTo("480P", base = "1080P")
        assertEquals("480P", s.resolve("1080P"))
        assertEquals("480P", s.resolve("1080P"))
    }

    @Test
    fun `changing the quality setting cancels the override`() {
        // Without this the session stayed pinned to the lower rung with nothing
        // in the UI saying so, and no way back short of force-stopping the app.
        val s = Session()
        s.stepDownTo("480P", base = "1080P")
        assertEquals("720P", s.resolve("720P"))
        assertNull(s.override)
    }

    @Test
    fun `an override never raises quality above the preference`() {
        val s = Session()
        s.stepDownTo("720P", base = "1080P")
        // User drops to 360P themselves: their choice wins outright.
        assertEquals("360P", s.resolve("360P"))
    }
}
