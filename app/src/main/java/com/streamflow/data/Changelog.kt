package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.4.0"

    val notes = listOf(
        "Fixed: view and subscriber counts showed as \"2.0M\" / \"5.0K\" instead of a clean \"2M\" / \"5K\"",
        "Fixed: dimming the screen with the brightness swipe in the player no longer keeps the whole app dark after you leave the video",
        "Fixed: switching to Audio-only now lets the screen sleep as intended (and switching back keeps it awake for video)",
        "Fixed: the mini player's play/pause button no longer briefly shows the wrong icon when tapped"
    )
}
