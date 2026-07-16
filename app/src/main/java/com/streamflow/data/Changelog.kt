package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.14.0"

    val notes = listOf(
        "The 100th build of StreamFlow! 🎉",
        "Library now remembers which tab you were on when you open a video and come back",
        "Home search bar keeps your text when you return from a video",
        "Fixed: the pull-to-refresh spinner could get stuck forever if a refresh failed quietly",
        "Shorts now resumes playing automatically when you return to the app",
        "The scroll-to-top button now also works in grid layout",
        "Search: Retry always works, and long result lists no longer stall while scrolling",
        "Sorting search results by Newest works correctly right after app start",
        "Less battery use during playback (a hidden stats loop no longer runs constantly)",
        "Fixed a rare crash when opening picture-in-picture on some devices"
    )
}
