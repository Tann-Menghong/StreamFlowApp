package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.66.2"

    val notes = listOf(
        "Clip moments! Tap \"Clip moment\" in the player to save the exact time - find them in Library > Bookmarks and jump right back",
        "Equalizer! Settings > Playback > Equalizer with presets: Rock, Pop, Jazz, Bass and more",
        "Search inside any channel - a search box now sits above the channel's videos",
        "Bookmarks tab added to Library with timestamp chips on thumbnails"
    )
}
