package com.streamflow.data

/**
 * The quality preference ladder, in one place.
 *
 * The rungs were previously an inline list inside PlayerViewModel.capQuality,
 * which meant the service had no way to reason about quality at all — its
 * resumeQuality() returned a flat "AUTO" or "480P" and ignored the user's
 * setting entirely.
 */
object QualityLadder {

    /** Lowest first. "AUTO" sits above these: it means "the best this device
     *  can sensibly decode", which DeviceCaps.autoMaxHeight decides. */
    val ORDER = listOf("360P", "480P", "720P", "1080P")

    const val AUTO = "AUTO"

    /**
     * Apply a ceiling without ever raising the user's choice.
     *
     * A genuine cap, not a floor: someone who deliberately picked 360P to save
     * more than 480p would must not be pushed back up to 480p by battery saver.
     */
    fun cap(pref: String, cap: String): String {
        if (pref == AUTO) return cap
        val prefIdx = ORDER.indexOf(pref)
        val capIdx = ORDER.indexOf(cap)
        return if (prefIdx == -1 || capIdx == -1 || prefIdx <= capIdx) pref else cap
    }

    /**
     * One rung down, or null when already at the bottom.
     *
     * AUTO resolves to whatever this device would actually have chosen — so on
     * a phone whose AUTO ceiling is 720p, stepping down from AUTO means 480p,
     * not 720p. Stepping "down" to the height you were already playing would
     * re-extract for no benefit and look like the app doing nothing.
     */
    fun stepDown(current: String, autoMaxHeight: Int): String? {
        val effective = if (current == AUTO) heightToPref(autoMaxHeight) else current
        val idx = ORDER.indexOf(effective)
        if (idx <= 0) return null
        return ORDER[idx - 1]
    }

    fun heightToPref(height: Int): String = when {
        height >= 1080 -> "1080P"
        height >= 720 -> "720P"
        height >= 480 -> "480P"
        else -> "360P"
    }

    /** "480P" -> "480p", for a sentence shown to the user. */
    fun label(pref: String): String =
        if (pref == AUTO) "Auto" else pref.lowercase()
}
