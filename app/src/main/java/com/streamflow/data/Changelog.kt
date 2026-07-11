package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.58.1"

    val notes = listOf(
        "Fixed: videos from History now resume where you left off (they used to restart)",
        "If you had finished a video (95%+), it starts over fresh like YouTube",
        "Leaving the app now keeps playing in the notification only - no more pop-up mini video",
        "Prefer the floating mini video? Turn it back on in Settings > Playback > Pop-up video on exit"
    )
}
