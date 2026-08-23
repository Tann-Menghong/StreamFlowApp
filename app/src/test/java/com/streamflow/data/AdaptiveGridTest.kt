package com.streamflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The video grid adapting to the width it actually has.
 *
 * Nothing in this app looked at screen size anywhere, so a phone turned
 * sideways showed the same two columns as upright -- two enormous cards with
 * almost nothing visible.
 */
class AdaptiveGridTest {

    private val phonePortrait = 411   // a typical phone's narrow dimension
    private val phoneLandscape = 891  // the same phone turned

    @Test
    fun `portrait gives exactly what the user asked for`() {
        // The guarantee that makes this safe to ship: the setting is visible in
        // Settings and must not be quietly overruled on the screen people
        // normally use.
        for (pref in 1..4) {
            assertEquals(
                "preference $pref must be honoured exactly in portrait",
                pref,
                AdaptiveGrid.columnsFor(pref, phonePortrait, phonePortrait)
            )
        }
    }

    @Test
    fun `turning the phone sideways fits more cards, not bigger ones`() {
        val portrait = AdaptiveGrid.columnsFor(2, phonePortrait, phonePortrait)
        val landscape = AdaptiveGrid.columnsFor(2, phonePortrait, phoneLandscape)
        assertEquals(2, portrait)
        assertTrue("landscape should add columns, got $landscape", landscape > portrait)
    }

    @Test
    fun `the card size stays put when the phone rotates`() {
        // The baseline is the NARROW dimension precisely so this holds. Using
        // the current width would grow the cards on rotation and never change
        // the count -- the bug being fixed.
        val landscapeCols = AdaptiveGrid.columnsFor(2, phonePortrait, phoneLandscape)
        val cardDp = phoneLandscape / landscapeCols
        val portraitCardDp = phonePortrait / 2
        assertTrue(
            "card widths should be comparable across rotation ($cardDp vs $portraitCardDp)",
            kotlin.math.abs(cardDp - portraitCardDp) < 60
        )
    }

    @Test
    fun `a wider window never drops below the preference`() {
        for (pref in 1..4) {
            for (w in listOf(320, 411, 600, 891, 1280)) {
                assertTrue(
                    "pref=$pref width=$w",
                    AdaptiveGrid.columnsFor(pref, phonePortrait, w) >= pref
                )
            }
        }
    }

    @Test
    fun `cards never shrink past readability`() {
        // Four columns on a narrow phone already implies ~100dp cards; a wide
        // window must not multiply that into a wall of thumbnails.
        val cols = AdaptiveGrid.columnsFor(4, phonePortrait, 1280)
        val cardDp = 1280 / cols
        assertTrue("card $cardDp dp is too narrow to read", cardDp >= AdaptiveGrid.MIN_CARD_DP - 1)
    }

    @Test
    fun `column count is bounded`() {
        assertTrue(AdaptiveGrid.columnsFor(2, phonePortrait, 4000) <= AdaptiveGrid.MAX_COLUMNS)
    }

    @Test
    fun `unknown metrics fall back to the preference`() {
        // A zero from the configuration must not produce a divide-by-zero or a
        // one-column feed.
        assertEquals(3, AdaptiveGrid.columnsFor(3, 0, 0))
        assertEquals(3, AdaptiveGrid.columnsFor(3, -1, 411))
    }

    @Test
    fun `a nonsense preference still yields a usable grid`() {
        assertTrue(AdaptiveGrid.columnsFor(0, phonePortrait, phonePortrait) >= 1)
        assertTrue(AdaptiveGrid.columnsFor(-5, phonePortrait, phonePortrait) >= 1)
    }
}
