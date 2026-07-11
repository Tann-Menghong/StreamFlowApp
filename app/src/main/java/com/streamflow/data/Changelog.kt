package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.60.0"

    val notes = listOf(
        "Design refresh across the whole app!",
        "Settings got a modern look with colorful rounded icons",
        "Channel pages: bigger banner with a smooth fade, larger avatar with an accent ring",
        "Thumbnails now show a soft placeholder while loading - no more white flashes",
        "Cleaned up internal code and verified all screens: home, search, player, channel, library, settings"
    )
}
