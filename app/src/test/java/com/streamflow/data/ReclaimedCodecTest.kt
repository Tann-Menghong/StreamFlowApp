package com.streamflow.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A decoder reclaimed by the system is recoverable, not fatal.
 *
 * media3 1.4 added ERROR_CODE_DECODING_RESOURCES_RECLAIMED for the case where
 * the OS hands our hardware decoder to something with a higher claim on it --
 * an incoming video call, the camera, another player. Before 1.4 that arrived
 * as a generic decoder failure; now it is its own code, and plan()'s
 * `else -> FATAL` would have classified it as unplayable media.
 *
 * That would have been the wrong answer in the most annoying possible way:
 * "This video cannot be played on this device", shown for a video that plays
 * perfectly, because someone rang the user.
 */
class ReclaimedCodecTest {

    @Test
    fun `a reclaimed decoder is retried, not declared unplayable`() {
        assertEquals(
            RecoveryPlan.RETRY,
            PlaybackRecovery.plan(PlaybackRecovery.CODE_DECODING_RESOURCES_RECLAIMED)
        )
    }

    @Test
    fun `it is retried for local media too`() {
        // plan() short-circuits every other failure on local media to FATAL,
        // which is right when the fix is re-extracting a URL a file does not
        // have. Losing the codec has nothing to do with the source, and a
        // downloaded video is if anything the likeliest thing to still be
        // playing when a call arrives.
        assertEquals(
            RecoveryPlan.RETRY,
            PlaybackRecovery.plan(
                PlaybackRecovery.CODE_DECODING_RESOURCES_RECLAIMED, isRemote = false
            )
        )
    }

    @Test
    fun `a genuinely unsupported format is still fatal`() {
        // The neighbouring 400x codes must NOT be swept up by this: retrying a
        // format this device has no decoder for burns battery to reach the same
        // conclusion, which is the reason FATAL exists.
        assertEquals(RecoveryPlan.FATAL, PlaybackRecovery.plan(4005))
        assertEquals(RecoveryPlan.FATAL, PlaybackRecovery.plan(4004))
    }

    @Test
    fun `the code matches the media3 constant`() {
        // Declared as a plain Int so this suite stays JVM-only. Verified against
        // PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED in the
        // media3 1.4.1 artifact; if media3 ever renumbers it, this is the line
        // that has to be re-checked.
        assertEquals(4006, PlaybackRecovery.CODE_DECODING_RESOURCES_RECLAIMED)
    }
}
