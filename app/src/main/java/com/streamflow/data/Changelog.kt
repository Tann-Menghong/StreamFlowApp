package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.10.0"

    val notes = listOf(
        "Fixed: quickly switching searches or categories on Home could briefly show results from the previous one — the newest query now always wins",
        "Fixed: Incognito is now fully private — it no longer updates watch progress on videos you'd watched before",
        "Fixed: the Search tab could show stale results when you changed your query mid-load",
        "View counts in the billions now read as 2.5B instead of 2500M",
        "Numbers and durations format correctly in every region (no more '2,0M' on some devices)",
        "Various stability and polish fixes across the app"
    )
}
