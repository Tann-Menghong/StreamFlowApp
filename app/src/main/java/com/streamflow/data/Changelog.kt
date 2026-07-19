package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "4.0.1"

    val notes = listOf(
        "The Auto-play setting now actually works — turn it off and videos no longer jump to the next one when they end (your queue still plays, as intended)",
        "Cancel on the \"Next in 5s\" countdown now stops it instantly — before, the timer kept ticking after you pressed Cancel",
        "The Library now really opens on Favorites by default, matching what Settings says — it silently opened History on fresh installs",
        "Bookmarks can now be chosen as your default Library tab in Settings",
        "Fixed a rare double-trigger when tapping \"Start watching\" twice at the end of first-launch setup"
    )
}
