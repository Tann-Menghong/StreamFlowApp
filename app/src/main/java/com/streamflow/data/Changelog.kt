package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.9.0"

    val notes = listOf(
        "Redesigned the rest of the app to match: Player, Library, Settings and search results",
        "Player: bigger, bolder video title and a clean channel bar grouping the avatar, name and Subscribe button",
        "Library: modern pill-style tabs replace the old underline tabs",
        "Settings: the busy Playback page is now split into clear labeled sections (Stream quality, Playback, Audio, Data & privacy)",
        "Search & related: larger, cleaner video rows",
        "Completes the full-app layout refresh started over the last few updates"
    )
}
