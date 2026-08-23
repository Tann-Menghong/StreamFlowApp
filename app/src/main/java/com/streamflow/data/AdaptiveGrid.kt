package com.streamflow.data

/**
 * How many columns the video grid should use for the width it actually has.
 *
 * The grid was `GridCells.Fixed(userPreference)` and nothing in the app looked
 * at the screen at all -- no size classes, no orientation handling, nowhere.
 * Two columns is a good choice for a phone held upright and a poor one for the
 * same phone turned sideways, where it produces two enormous cards and almost
 * nothing visible. On a tablet it is worse.
 *
 * The rule here keeps the user's setting as the authority and reads it as a
 * CARD SIZE rather than a fixed count: "two columns" means "cards half the
 * width of this phone", and a wider window then fits more cards of that same
 * size instead of stretching them. Portrait is unchanged, which matters --
 * the setting is a visible preference and the app must not quietly overrule it.
 *
 * The baseline is the device's NARROW dimension, not its current width, so the
 * implied card size stays put when the phone rotates. Deriving it from the
 * current width instead would make the cards grow on rotation and the count
 * never change, which is the bug being fixed.
 *
 * Pure arithmetic with no Android types, so the behaviour is provable in JVM
 * unit tests -- the same reason QualityLadder and BufferHealth live here.
 */
object AdaptiveGrid {

    /** Below this a card is too small to read a title in. Acts as a ceiling on
     *  the column count for very wide windows, not as a floor on the count. */
    const val MIN_CARD_DP = 120

    /** Sanity bound: past this, more columns stop being useful and start being
     *  a wall of thumbnails. */
    const val MAX_COLUMNS = 8

    /**
     * @param preferred        the user's chosen column count
     * @param narrowestWidthDp the device's narrow dimension (its portrait
     *                         width), which does not change on rotation
     * @param currentWidthDp   the width available right now
     */
    fun columnsFor(preferred: Int, narrowestWidthDp: Int, currentWidthDp: Int): Int {
        val pref = preferred.coerceAtLeast(1)
        // Unknown metrics: honour the preference rather than guess.
        if (narrowestWidthDp <= 0 || currentWidthDp <= 0) return pref
        val cardDp = (narrowestWidthDp / pref).coerceAtLeast(MIN_CARD_DP)
        return (currentWidthDp / cardDp)
            .coerceAtLeast(pref)      // never fewer than the user asked for
            .coerceAtMost(MAX_COLUMNS)
    }
}
