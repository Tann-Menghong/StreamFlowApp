package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.56.0"

    val notes = listOf(
        "Fixed: the video player no longer covers the clock, battery and wifi status bar",
        "Channel pages now have YouTube-style tabs: Videos, Shorts, Live, Playlists and About",
        "Browse a channel's playlists and open them right from the channel page",
        "Tighter player layout — much less empty space between the title, channel and speed rows",
        "Switching channel tabs keeps the header in place instead of a full-screen spinner"
    )
}
