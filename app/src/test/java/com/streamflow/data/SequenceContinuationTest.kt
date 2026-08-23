package com.streamflow.data

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * One dead video must not stop the whole sequence.
 *
 * PlaybackService.advance() already skipped up to three unplayable queue
 * entries. The player screen's countdown had no equivalent: it popped one
 * entry, navigated to it, and a video that would not extract left the user on an
 * error page with "Go back" and "Retry" -- while the rest of the queue sat
 * behind the entry popNext() had already consumed, unreachable.
 */
class SequenceContinuationTest {

    @Before
    @After
    fun reset() {
        AutoAdvance.onSequenceProgress()
        AutoAdvance.consumeAuto("https://a")
        AutoAdvance.consumeAuto("https://b")
    }

    @Test
    fun `a video the sequence opened is recognised`() {
        AutoAdvance.markAuto("https://a")
        assertTrue(AutoAdvance.consumeAuto("https://a"))
    }

    @Test
    fun `a video the user opened is left alone`() {
        // The distinction the whole fix rests on. Skipping past a video someone
        // deliberately chose, because it failed once, would be the app
        // overriding them -- they want THAT video, not the next one.
        assertFalse(AutoAdvance.consumeAuto("https://user-picked"))
    }

    @Test
    fun `the mark belongs to one video only`() {
        AutoAdvance.markAuto("https://a")
        assertFalse(AutoAdvance.consumeAuto("https://b"))
        assertTrue(AutoAdvance.consumeAuto("https://a"))
    }

    @Test
    fun `consuming the mark is a one-shot`() {
        // Or a manual Retry of the same video, which lands back on the same
        // error, would be treated as another automatic arrival and skip away
        // from the video the user just asked to try again.
        AutoAdvance.markAuto("https://a")
        assertTrue(AutoAdvance.consumeAuto("https://a"))
        assertFalse(AutoAdvance.consumeAuto("https://a"))
    }

    @Test
    fun `skipping is bounded`() {
        // A queue of dead links must not burn one extraction per entry.
        repeat(AutoAdvance.MAX_SEQUENCE_SKIPS) {
            assertTrue("skip ${it + 1} should be allowed", AutoAdvance.onSequenceFailure())
        }
        assertFalse("must stop after the limit", AutoAdvance.onSequenceFailure())
    }

    @Test
    fun `a video that plays restores the full budget`() {
        // Two dead links early in a long queue must not leave the sequence one
        // failure from giving up an hour later.
        assertTrue(AutoAdvance.onSequenceFailure())
        assertTrue(AutoAdvance.onSequenceFailure())
        AutoAdvance.onSequenceProgress()
        repeat(AutoAdvance.MAX_SEQUENCE_SKIPS) {
            assertTrue(AutoAdvance.onSequenceFailure())
        }
        assertFalse(AutoAdvance.onSequenceFailure())
    }

    @Test
    fun `the skip limit matches the service's own`() {
        // PlaybackService.advance() walks at most three dead queue entries. The
        // screen path and the background path should give up at the same point,
        // or the same queue behaves differently depending on whether the user
        // happened to be looking at it.
        assertTrue(AutoAdvance.MAX_SEQUENCE_SKIPS == 3)
    }

    @Test
    fun `the end-of-video claim is independent of the skip budget`() {
        // They are different concerns sharing one object: claim() arbitrates who
        // advances, the budget bounds how many failures that survives.
        AutoAdvance.reset()
        assertTrue(AutoAdvance.claim("https://v"))
        assertFalse(AutoAdvance.claim("https://v"))
        assertTrue(AutoAdvance.onSequenceFailure())
        assertFalse(AutoAdvance.claim("https://v"))
    }
}
