package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "2.65.0"

    val notes = listOf(
        "Telegram-style design update!",
        "Long-press or tap the three dots on any video: actions now open in a beautiful bottom sheet with a video preview",
        "Settings icons are now colorful squares, each row with its own color - just like Telegram",
        "Library and Settings have big bold titles that collapse as you scroll",
        "Screens now slide in smoothly with a parallax push instead of fading",
        "Helpful gray hints added under key settings sections"
    )
}
