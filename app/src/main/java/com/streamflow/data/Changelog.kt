package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "6.3.0"

    val notes = listOf(
        "Loading now shows a real percentage ⏳ — an animated ring that tells you how far along it is instead of a spinner that says nothing",
        "If a video stalls mid-playback you now see the actual buffered percentage climbing, so you know it's working",
        "Videos should open sharper: the player no longer guesses a slow connection for the first few seconds before catching up",
        "Smoother playback on changing signal — quality now drops fast when your connection dips and climbs back gently, instead of stalling",
        "More simultaneous connections and longer-lived ones, so video, audio and thumbnails stop competing for the same sockets",
        "Pressing play on your first video no longer hitches while the media cache opens — that work moved off the main thread at startup"
    )
}
