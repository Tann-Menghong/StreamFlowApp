package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.53.1"

    val notes = listOf(
        "Fixed: Shorts audio kept playing after leaving the app",
        "Fixed: Shorts played over other apps' music — now requests audio focus properly",
        "Fixed: Shorts audio could overlap when opening a video in the full player or a channel",
        "Fixed: possible crash in Channels/Playlists search results with duplicate entries",
        "Fixed: background music sometimes wasn't paused when opening Shorts right after launch"
    )
}
