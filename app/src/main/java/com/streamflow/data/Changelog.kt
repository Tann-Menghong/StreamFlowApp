package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.18.0"

    val notes = listOf(
        "Backup got a major upgrade: it now includes your Bookmarks (clip moments), channel groups, per-channel notification bells, playlist ordering, and save dates — restoring on a new phone keeps everything",
        "Fixed: importing a NewPipe subscriptions file in plain list format always failed",
        "Fixed: the Home feed could stop loading more videos partway down (both list and grid layouts)",
        "Fixed: a rare connection leak when the update check failed",
        "Custom home topics no longer keep accidental spaces around their names",
        "Hide-Shorts setting description now matches what it actually does",
        "Plus small polish across Settings and Home"
    )
}
