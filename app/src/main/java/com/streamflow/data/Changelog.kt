package com.streamflow.data

// User-facing release notes shown in the "What's New" dialog after an update.
// MUST be updated on every version bump so users see what changed.
object Changelog {
    const val VERSION_NAME = "5.9.0"

    val notes = listOf(
        "New: a Previous ⏮️ button on the media notification and lock screen — jump back to the video you were just watching, right next to the Next button",
        "The app remembers your watch order as you go, so Previous replays exactly what you came from",
        "The notification-permission prompt now asks just once instead of on every launch",
        "Small reliability tidy-ups under the hood"
    )
}
