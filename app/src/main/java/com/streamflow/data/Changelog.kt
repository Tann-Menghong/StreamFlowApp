package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.1.2"

    val notes = listOf(
        "Bug fix pass!",
        "Fixed: opening a saved moment could quietly wipe that video's watch-time stats if it later failed to reload",
        "Fixed: Battery saver could raise a manually picked lower quality (e.g. 360p) back up to 480p instead of leaving it alone",
        "Fixed: Bluetooth resume now skips downloaded files and finds your last real YouTube video to continue"
    )
}
