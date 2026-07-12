package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "3.3.0"

    val notes = listOf(
        "Fixed: tapping Next/autoplaying through several videos in a row no longer piles up the back button - back now returns you to where you started watching",
        "Fixed: sponsor-skip segments weren't found for videos opened from a shared youtu.be or Shorts link",
        "Fixed: rapidly switching between a channel's tabs could show the wrong tab's videos for a moment"
    )
}
