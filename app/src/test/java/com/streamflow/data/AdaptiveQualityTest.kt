package com.streamflow.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The app-imposed quality ceiling, and the two bugs that came from it being
 * private to PlaybackService.
 *
 * Both were silent: the app reported a quality it was not playing, and the one
 * gesture that most clearly means "stop managing this for me" -- picking a
 * quality by hand -- did not always stop it.
 */
class AdaptiveQualityTest {

    @Before
    @After
    fun reset() = AdaptiveQuality.clear()

    @Test
    fun `nothing is imposed until the app lowers something`() {
        assertNull(AdaptiveQuality.rung)
        assertNull(AdaptiveQuality.baseline)
    }

    @Test
    fun `a step-down records both the rung and what it was measured against`() {
        // Both together or neither: an override whose baseline is missing is
        // dropped on the very next read, which silently disabled the step-down
        // it was part of.
        AdaptiveQuality.lower("480P", baseline = QualityLadder.AUTO)
        assertEquals("480P", AdaptiveQuality.rung)
        assertEquals(QualityLadder.AUTO, AdaptiveQuality.baseline)
    }

    @Test
    fun `climbing back keeps the baseline it was measured against`() {
        // Losing it here would make the next read see baseline == null, decide
        // the preference had changed, and drop the override -- undoing the
        // step-down wholesale instead of one rung at a time.
        AdaptiveQuality.lower("360P", baseline = "1080P")
        AdaptiveQuality.raise("480P")
        assertEquals("480P", AdaptiveQuality.rung)
        assertEquals("1080P", AdaptiveQuality.baseline)
    }

    @Test
    fun `an explicit pick clears the override outright`() {
        // The bug this fixes: clearing used to depend on the stored preference
        // CHANGING. Someone whose preference was already 1080p, watching a
        // stream the app had lowered, tapped 1080p, got it, and was dropped
        // again at the next re-extract because nothing had contradicted the
        // override.
        AdaptiveQuality.lower("480P", baseline = "1080P")
        AdaptiveQuality.clear()
        assertNull(AdaptiveQuality.rung)
        assertNull(AdaptiveQuality.baseline)
    }

    @Test
    fun `clearing twice is harmless`() {
        AdaptiveQuality.clear()
        AdaptiveQuality.clear()
        assertNull(AdaptiveQuality.rung)
    }

    @Test
    fun `the state is observable so the player can show it`() {
        // The whole reason this left PlaybackService: as a private field the UI
        // could not see it, so the quality button kept reporting the height the
        // player screen had extracted while the service played a lower one.
        AdaptiveQuality.lower("360P", baseline = "720P")
        assertEquals("360P", AdaptiveQuality.state.value.rung)
        AdaptiveQuality.clear()
        assertNull(AdaptiveQuality.state.value.rung)
    }

    @Test
    fun `an imposed rung is only ever a cap on the preference`() {
        // The override is applied through QualityLadder.cap, so it can lower a
        // preference and never raise one. Someone who deliberately chose 360p
        // must not be pushed up to 480p by a step-down aimed at 1080p.
        AdaptiveQuality.lower("480P", baseline = QualityLadder.AUTO)
        assertEquals("360P", QualityLadder.cap("360P", AdaptiveQuality.rung!!))
        assertEquals("480P", QualityLadder.cap("1080P", AdaptiveQuality.rung!!))
    }
}
