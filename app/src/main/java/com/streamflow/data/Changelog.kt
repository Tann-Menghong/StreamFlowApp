package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.50.1"

    val notes = listOf(
        "\"What's new\" can now be reopened anytime from Settings > About",
        "Watch stats — see videos watched today, this week and total watch time in Library > History",
        "Queue upgrades — Play next, reorder, shuffle, and your queue now survives app restarts",
        "Watch-progress bar on video thumbnails, just like YouTube",
        "Chapter markers on the seek bar, with the chapter name shown while seeking",
        "Seek preview thumbnails — see the video frame while dragging the seek bar",
        "A–B loop — repeat any section of a video (great for music practice)",
        "Channel groups — organize subscriptions into folders and filter your feed by group",
        "Subscriptions feed can be filtered by channel group",
        "Audio-only mode is now remembered between videos",
        "Stats overlay now shows resolution and codec",
        "Backup now includes your watch history",
        "Incognito polish — recent searches aren't saved and Home shows an incognito badge",
        "New home-screen widget with quick actions: Search, Subscriptions, Library"
    )
}
