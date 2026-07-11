package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.56.1"

    val notes = listOf(
        "Fixed for real: the empty space between the title, channel and speed rows in the player",
        "Every control in the speed row (repeat, A·B loop, audio-only, queue, subtitles) is now compact",
        "Tighter spacing around the video description too"
    )
}
