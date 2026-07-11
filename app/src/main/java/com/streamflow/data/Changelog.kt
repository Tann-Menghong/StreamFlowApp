package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.59.1"

    val notes = listOf(
        "Fixed for real: videos now resume exactly where you left off",
        "The resume position is handed to the player before playback starts, so it can never be lost",
        "Position saving is now bulletproof - another video playing in the mini player can no longer overwrite it"
    )
}
