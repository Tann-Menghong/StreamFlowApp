package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.66.2"

    val notes = listOf(
        "Auto-download! Turn on Settings > Storage > Auto-download Watch Later - videos save themselves on Wi-Fi",
        "Download quality picker: choose Best / 720p / 480p / Audio when downloading",
        "Battery saver mode: caps quality at 480p and disables extras (Settings > Playback)",
        "NEW badge: the Feed button in Library shows how many new uploads your channels posted"
    )
}
